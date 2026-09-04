package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.payment.dto.CheckoutPreviewResponse;
import com.moriah.skillhub.payment.dto.CheckoutRequest;
import com.moriah.skillhub.payment.dto.CheckoutResponse;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.entity.PaymentGateway;
import com.moriah.skillhub.payment.entity.PaymentStatus;
import com.moriah.skillhub.payment.gateway.RazorpayProperties;
import com.moriah.skillhub.payment.gateway.RazorpayService;
import com.moriah.skillhub.payment.gateway.StripeService;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.entity.SubscriptionStatus;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import com.moriah.skillhub.subscription.repository.UserSubscriptionRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import com.razorpay.Order;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * build-plan.md feature 07: "Amount always re-read from subscription_plans server-side — never
 * trust the client." The client only ever names a plan code and (optionally) a coupon code; every
 * number in the response is computed here.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final String CURRENCY = "INR";

    /** Layer-1 double-charge guard: a second {@code /checkout} for the same plan within this
     * window reuses the first still-open Razorpay order instead of creating another. */
    private static final Duration CHECKOUT_DEDUPE_WINDOW = Duration.ofMinutes(15);

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final CouponService couponService;
    private final RazorpayService razorpayService;
    private final RazorpayProperties razorpayProperties;
    private final StripeService stripeService;

    /**
     * "What would I pay for this plan with this coupon?" — read-only, creates nothing and
     * reserves no coupon capacity. An invalid coupon comes back as {@code couponApplied=false}
     * with a reason rather than an error, so the checkout page can show the message inline.
     */
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse previewCheckout(String planCode, String couponCode) {
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(planCode)
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));

        BigDecimal original = plan.getPriceInr();
        if (couponCode == null || couponCode.isBlank()) {
            return new CheckoutPreviewResponse(plan.getCode(), plan.getName(), original, original, CURRENCY, false, null);
        }
        try {
            BigDecimal discounted = couponService.preview(couponCode, original).discountedAmount();
            return new CheckoutPreviewResponse(plan.getCode(), plan.getName(), original, discounted, CURRENCY,
                    true, "Coupon applied.");
        } catch (BusinessException e) {
            return new CheckoutPreviewResponse(plan.getCode(), plan.getName(), original, original, CURRENCY,
                    false, "That coupon code isn't valid or has expired.");
        }
    }

    @Transactional
    public CheckoutResponse checkout(Long userId, CheckoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(request.planCode())
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));

        // Layer-1 guard A — the user already has an ACTIVE subscription. Reject before any money
        // moves; a second capture could never create a second subscription anyway (V3's
        // uq_one_active_subscription), so this only ever leads to a "captured, nothing delivered"
        // row that needs a manual refund. Renewals/upgrades are a separate flow.
        if (userSubscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE).isPresent()) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
        }

        // Layer-1 guard B — a rapid re-submit (double-click, back-then-retry) of the same plan.
        // Reuse the first still-open Razorpay order rather than minting another gateway order the
        // user could also pay. Razorpay only: Stripe's hosted-checkout URL isn't persisted, so a
        // reused Stripe session can't be reconstructed — a fresh session there is harmless (the
        // stale one just expires unpaid).
        if (request.gateway() == PaymentGateway.RAZORPAY) {
            List<Payment> reusable = paymentRepository.findReusableCreated(
                    user.getId(), plan.getId(), PaymentGateway.RAZORPAY,
                    Instant.now().minus(CHECKOUT_DEDUPE_WINDOW));
            if (!reusable.isEmpty()) {
                Payment open = reusable.get(0);
                return new CheckoutResponse(
                        PaymentGateway.RAZORPAY, open.getId(), open.getAmount(), CURRENCY,
                        open.getGatewayOrderId(), razorpayProperties.keyId(), null);
            }
        }

        BigDecimal amount = plan.getPriceInr();
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            amount = couponService.preview(request.couponCode(), amount).discountedAmount();
        }

        // A temporary, unique placeholder — gateway_order_id is NOT NULL UNIQUE, and the real
        // gateway order/session id doesn't exist until after the call below, which itself needs
        // payment.id (as the Razorpay receipt / Stripe client_reference_id) to already exist.
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setPlanId(plan.getId());
        payment.setTrackCode(request.trackCode());
        payment.setGateway(request.gateway());
        payment.setGatewayOrderId("pending-" + UUID.randomUUID());
        payment.setAmount(amount);
        payment.setCurrency(CURRENCY);
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.saveAndFlush(payment);

        CheckoutResponse response = switch (request.gateway()) {
            case RAZORPAY -> checkoutWithRazorpay(payment, plan);
            case STRIPE -> checkoutWithStripe(payment, plan);
        };

        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            couponService.redeem(request.couponCode(), user, payment);
        }

        return response;
    }

    private CheckoutResponse checkoutWithRazorpay(Payment payment, SubscriptionPlan plan) {
        String receipt = "payment-" + payment.getId();
        Order order = razorpayService.createOrder(payment.getAmount(), receipt);
        String orderId = order.get("id");

        payment.setGatewayOrderId(orderId);
        paymentRepository.save(payment);

        return new CheckoutResponse(
                PaymentGateway.RAZORPAY, payment.getId(), payment.getAmount(), CURRENCY,
                orderId, razorpayProperties.keyId(), null);
    }

    private CheckoutResponse checkoutWithStripe(Payment payment, SubscriptionPlan plan) {
        Session session = stripeService.createCheckoutSession(
                payment.getAmount(), CURRENCY, plan.getName(), payment.getId());

        payment.setGatewayOrderId(session.getId());
        paymentRepository.save(payment);

        return new CheckoutResponse(
                PaymentGateway.STRIPE, payment.getId(), payment.getAmount(), CURRENCY,
                null, null, session.getUrl());
    }
}
