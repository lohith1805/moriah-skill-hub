package com.moriah.skillhub.common.notification.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationStatus;
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
 * {@code userId} is a bare {@code Long}, not a {@code @ManyToOne User} — {@code common/} never
 * imports a feature package (architecture.md), and {@code User} living in {@code user/} is only
 * an established exception for {@code common/security} classes that need the token/role check
 * every request already requires (feature 05 decision log), not for a queue-row entity that only
 * ever needs the id. Same {@code RefreshToken.replacedBy}/{@code Payment.planId} pattern.
 * <p>
 * {@code payload} stores pre-serialized JSON text, same treatment as {@code WebhookEvent.payload}
 * — nothing queries into it, it exists for the dispatcher to read back and for audit.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.QUEUED;

    // columnDefinition matches V2's TINYINT UNSIGNED exactly — see SubscriptionPlan (feature 07)
    // for why a plain Integer/int mapping fails ddl-auto: validate against an UNSIGNED column.
    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
