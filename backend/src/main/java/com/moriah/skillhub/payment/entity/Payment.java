package com.moriah.skillhub.payment.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code gatewayOrderId} is Razorpay's {@code order_id} or Stripe's checkout {@code session.id} —
 * the reconciliation key both this system and the gateway's own dashboard key off (library-docs.md
 * "Razorpay": "receipt is our payments.gateway_order_id reference — always set it").
 * {@code gatewayPaymentId} is only populated once the gateway confirms capture (Razorpay's
 * {@code payment_id} / Stripe's {@code payment_intent}), via the webhook — never at checkout time.
 * <p>
 * {@code planId} is a plain id, not a {@code @ManyToOne SubscriptionPlan} — {@code
 * SubscriptionPlan} belongs to the {@code subscription/} package (architecture.md's own package
 * diagram), a sibling feature module to {@code payment/}, not a shared-kernel type the way {@code
 * User} is. Same reasoning already applied to {@code RefreshToken.replacedBy}: a bare id keeps
 * this an equality comparison, never a cross-module entity navigation.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** `/architect feature 10`: captured at checkout, not derived from {@code user_profiles} —
     * {@code BatchAllocationService.allocate} reads it straight off this row at webhook time,
     * with no dependency on the student having filled out their profile first. Nullable at the
     * DB level (V9) since it didn't exist before this feature; every new checkout sets it
     * (`CheckoutRequest.trackCode` is {@code @NotBlank}). */
    @Column(name = "track_code", length = 30)
    private String trackCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentGateway gateway;

    @Column(name = "gateway_order_id", nullable = false, length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // columnDefinition matches V3's CHAR(3) exactly — Hibernate's default for a String column is
    // VARCHAR, not CHAR (the same fix applied throughout feature 03's token-hash columns).
    @Column(nullable = false, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "captured_at")
    private Instant capturedAt;
}
