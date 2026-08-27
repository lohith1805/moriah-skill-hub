package com.moriah.skillhub.payment.repository;

import com.moriah.skillhub.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /** Refund webhooks (Razorpay's {@code refund.processed}, Stripe's {@code charge.refunded})
     * reference the gateway's payment/charge id, not the order/session id the checkout flow
     * created — {@code gatewayPaymentId} is only ever set once, by the capture event itself. */
    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);
}
