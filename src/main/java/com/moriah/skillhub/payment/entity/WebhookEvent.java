package com.moriah.skillhub.payment.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * {@code eventId}'s uniqueness is the entire idempotency guarantee (library-docs.md "Webhook
 * Idempotency") — {@code WebhookIdempotencyService.claim()} relies on the unique-constraint
 * violation itself, never a {@code SELECT} then {@code INSERT}. {@code payload} stores the raw
 * webhook body verbatim as text (already verified against its HMAC signature by the time it's
 * saved) — a plain {@code String} with {@code columnDefinition = "JSON"}, not a Hibernate
 * JSON-mapped type, since nothing ever queries into it; it exists for reconciliation/audit, not
 * for the application to read fields back out of.
 */
@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class WebhookEvent extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String gateway;

    @Column(name = "event_id", nullable = false, length = 150)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookEventStatus status = WebhookEventStatus.RECEIVED;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
