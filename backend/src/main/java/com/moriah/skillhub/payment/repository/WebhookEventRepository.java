package com.moriah.skillhub.payment.repository;

import com.moriah.skillhub.payment.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** No custom finder methods — {@code WebhookIdempotencyService.claim()} only ever calls {@code
 * save()} and relies on the {@code event_id} unique-constraint violation (library-docs.md
 * "Webhook Idempotency": "never replace it with a SELECT then INSERT — that races"). */
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
}
