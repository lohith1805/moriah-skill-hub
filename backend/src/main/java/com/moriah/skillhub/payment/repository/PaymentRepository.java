package com.moriah.skillhub.payment.repository;

import com.moriah.skillhub.payment.dto.PaymentStatusAggregate;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.entity.PaymentGateway;
import com.moriah.skillhub.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /** {@code InvoiceService.renderAndUpload} runs with no ambient transaction (it does an
     * outbound S3 upload), so a plain {@code findById} hands back a {@code Payment} whose {@code
     * user} is an uninitialised lazy proxy — dereferencing {@code user.getEmail()} later throws
     * {@code LazyInitializationException}. Join-fetch the user so the confirmation email can read
     * it after the session closes. */
    @EntityGraph(attributePaths = "user")
    Optional<Payment> findWithUserById(Long id);

    /** The caller's own billing history — {@code GET /api/v1/subscriptions/me/invoices}. Ordered
     * newest first; the controller filters to CAPTURED + REFUNDED so an abandoned checkout
     * ({@code CREATED}) never shows. */
    List<Payment> findByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<PaymentStatus> statuses);

    /** Refund webhooks (Razorpay's {@code refund.processed}, Stripe's {@code charge.refunded})
     * reference the gateway's payment/charge id, not the order/session id the checkout flow
     * created — {@code gatewayPaymentId} is only ever set once, by the capture event itself. */
    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    /** Layer-1 double-charge guard ({@code CheckoutService.checkout}): a rapid re-submit of the
     * same checkout — same user, plan and gateway, still {@code CREATED} and only a few minutes
     * old — reuses that open gateway order instead of minting a second one. The {@code
     * 'pending-%'} filter drops the pre-flush placeholder {@code gatewayOrderId}. Caller takes
     * the first row (newest). */
    @Query("""
            SELECT p FROM Payment p
             WHERE p.user.id = :userId AND p.planId = :planId AND p.gateway = :gateway
               AND p.status = com.moriah.skillhub.payment.entity.PaymentStatus.CREATED
               AND p.gatewayOrderId NOT LIKE 'pending-%'
               AND p.createdAt >= :since
             ORDER BY p.createdAt DESC
            """)
    List<Payment> findReusableCreated(@Param("userId") Long userId,
                                      @Param("planId") Long planId,
                                      @Param("gateway") PaymentGateway gateway,
                                      @Param("since") java.time.Instant since);

    /** Admin transactions list (gap B1.11). Every filter is optional — a {@code null} argument
     * drops that predicate, the same nullable-parameter pattern as {@code UserRepository.search}.
     * {@code user} is fetched with the row so building {@code AdminPaymentResponse} does not N+1
     * on {@code user.uuid}/{@code user.fullName}. */
    @Query("""
            SELECT p FROM Payment p
             WHERE (:status IS NULL OR p.status = :status)
               AND (:gateway IS NULL OR p.gateway = :gateway)
               AND (:userUuid IS NULL OR p.user.uuid = :userUuid)
            """)
    @EntityGraph(attributePaths = "user")
    Page<Payment> search(@Param("status") PaymentStatus status,
                         @Param("gateway") PaymentGateway gateway,
                         @Param("userUuid") String userUuid,
                         Pageable pageable);

    /** {@code GET /api/v1/admin/payments/summary} — one grouped scan, count and amount total per
     * status. The controller layer folds this into the summary tiles. */
    @Query("""
            SELECT p.status AS status, COUNT(p) AS count, COALESCE(SUM(p.amount), 0) AS totalAmount
              FROM Payment p
             GROUP BY p.status
            """)
    List<PaymentStatusAggregate> summarizeByStatus();
}
