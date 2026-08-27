package com.moriah.skillhub.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * library-docs.md "Webhook Idempotency", verbatim: the unique constraint on {@code
 * webhook_events.event_id} is the entire idempotency guarantee, never a {@code SELECT} then
 * {@code INSERT} (that races). {@code REQUIRES_NEW} so the claim itself survives a rollback of
 * whatever business transaction runs after it — a failed state transition later in the same
 * webhook delivery must not un-claim the event and invite the gateway to retry it as if it had
 * never been seen. WhatsApp (feature 18, {@code crm.webhook.WhatsAppWebhookService}) reuses
 * this same table and this same service — GitHub (feature 12) verification is poll-based against
 * GitHub's REST API instead ({@code submission.gateway.GithubVerificationService}), not an
 * inbound webhook, so it never claims a row here.
 * <p>
 * Plain {@link JdbcTemplate}, deliberately not the {@code WebhookEventRepository} JPA repository
 * this originally used — found the hard way: catching a {@code DataIntegrityViolationException}
 * from a Hibernate-backed {@code save()} and returning normally still leaves the surrounding
 * {@code REQUIRES_NEW} transaction marked rollback-only. Hibernate poisons its own session the
 * moment a flush fails, independent of whether application code catches the translated
 * exception afterward — so {@code @Transactional} tries to commit a transaction Hibernate has
 * already condemned, and Spring throws {@code UnexpectedRollbackException} at method exit, every
 * single time a duplicate event arrives. Plain JDBC has no such session to poison: a duplicate
 * key error here doesn't affect anything else this transaction can still do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookIdempotencyService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String gateway, String eventId, String eventType, String payload) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO webhook_events (gateway, event_id, event_type, payload, status)
                    VALUES (?, ?, ?, ?, 'RECEIVED')
                    """, gateway, eventId, eventType, payload);
            return true; // first time — proceed
        } catch (DataIntegrityViolationException e) {
            log.info("[webhook/{}] duplicate event {} ignored", gateway, eventId);
            return false; // already processed — stop
        }
    }
}
