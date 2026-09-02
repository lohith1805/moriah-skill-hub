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

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /** Refund webhooks (Razorpay's {@code refund.processed}, Stripe's {@code charge.refunded})
     * reference the gateway's payment/charge id, not the order/session id the checkout flow
     * created — {@code gatewayPaymentId} is only ever set once, by the capture event itself. */
    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

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
