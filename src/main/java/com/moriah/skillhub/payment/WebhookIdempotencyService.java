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
 * <p>
 * <b>Audit 2026-08-31 (C3):</b> the claim used to be write-once with no lifecycle after it —
 * status stayed {@code 'RECEIVED'} forever and {@code processed_at} was never set, so an event
 * whose handler threw <i>after</i> the claim committed was lost permanently and looked identical
 * to one processed successfully. Now: {@link #claim} first reclaims a stale {@code 'RECEIVED'}
 * row (a crash between claim and handler), {@link #runAndMarkProcessed} flips the row to {@code
 * 'PROCESSED'} in the <i>same</i> transaction as the business state change, and {@link
 * #releaseClaim} deletes a still-{@code 'RECEIVED'} row when the handler failed so the gateway's
 * redelivery re-claims cleanly. {@code WebhookReconciliationJob} alerts on anything still stuck.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookIdempotencyService {

    /** A {@code RECEIVED} row older than this had its handler crash before completing or
     * releasing it; the next delivery of the same event is allowed to reclaim it. Must be well
     * above any realistic handler duration and above every gateway's own retry interval. */
    static final int STALE_CLAIM_MINUTES = 15;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String gateway, String eventId, String eventType, String payload) {
        // Happy path is a bare INSERT — no SELECT, no DELETE — so concurrent first-deliveries of
        // different events take only lightweight insert-intention locks and never gap-lock each
        // other (an earlier version did an unconditional DELETE-if-stale here and deadlocked
        // concurrent claims for lexically-adjacent event_ids).
        if (tryInsert(gateway, eventId, eventType, payload)) {
            return true; // first time — proceed
        }
        // A row already exists. If it is a stale RECEIVED claim (handler process killed
        // mid-delivery), reclaim it — safe because every webhook handler here is idempotent on
        // its own business state, so a genuine double-process is a no-op.
        int reclaimed = jdbcTemplate.update("""
                DELETE FROM webhook_events
                 WHERE gateway = ? AND event_id = ? AND status = 'RECEIVED'
                   AND created_at < (NOW(6) - INTERVAL ? MINUTE)
                """, gateway, eventId, STALE_CLAIM_MINUTES);
        if (reclaimed == 0) {
            log.info("[webhook/{}] duplicate event {} ignored", gateway, eventId);
            return false; // in flight or already PROCESSED — stop
        }
        if (tryInsert(gateway, eventId, eventType, payload)) {
            return true; // reclaimed a stale row and re-inserted
        }
        // Another node reclaimed the same stale row first — treat as a duplicate.
        log.info("[webhook/{}] event {} reclaimed concurrently, ignoring", gateway, eventId);
        return false;
    }

    private boolean tryInsert(String gateway, String eventId, String eventType, String payload) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO webhook_events (gateway, event_id, event_type, payload, status)
                    VALUES (?, ?, ?, ?, 'RECEIVED')
                    """, gateway, eventId, eventType, payload);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    /**
     * Runs the business handler and marks the claim {@code PROCESSED} atomically — the handler
     * joins this transaction (default {@code REQUIRED} propagation), so either both the state
     * change and the {@code PROCESSED} flip commit, or neither does. A handler exception
     * propagates to the caller with nothing committed; the caller is expected to call
     * {@link #releaseClaim} and return a non-2xx so the gateway redelivers.
     */
    @Transactional
    public void runAndMarkProcessed(String gateway, String eventId, Runnable handler) {
        handler.run();
        jdbcTemplate.update("""
                UPDATE webhook_events
                   SET status = 'PROCESSED', processed_at = NOW(6)
                 WHERE gateway = ? AND event_id = ? AND status = 'RECEIVED'
                """, gateway, eventId);
    }

    /**
     * Deletes a still-{@code RECEIVED} claim so the gateway's next redelivery re-claims it as
     * new work. {@code REQUIRES_NEW} so it commits even though the caller is about to rethrow
     * the handler's exception. A no-op if the row already reached {@code PROCESSED}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(String gateway, String eventId) {
        int released = jdbcTemplate.update("""
                DELETE FROM webhook_events
                 WHERE gateway = ? AND event_id = ? AND status = 'RECEIVED'
                """, gateway, eventId);
        if (released > 0) {
            log.warn("[webhook/{}] released claim on failed event {} for gateway redelivery", gateway, eventId);
        }
    }
}
