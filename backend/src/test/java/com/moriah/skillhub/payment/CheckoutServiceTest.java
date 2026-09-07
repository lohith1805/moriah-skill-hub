package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.payment.dto.CheckoutRequest;
import com.moriah.skillhub.payment.dto.CheckoutResponse;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.entity.PaymentGateway;
import com.moriah.skillhub.payment.gateway.RazorpayProperties;
import com.moriah.skillhub.payment.gateway.RazorpayService;
import com.moriah.skillhub.payment.gateway.StripeService;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.entity.SubscriptionStatus;
import com.moriah.skillhub.subscription.entity.UserSubscription;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import com.moriah.skillhub.subscription.repository.UserSubscriptionRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import com.razorpay.Order;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `/architect feature 07` decision: no real Razorpay/Stripe sandbox credentials exist, so
 * {@link RazorpayService}/{@link StripeService} are mocked entirely here — this proves
 * {@link CheckoutService}'s own logic (amount computation, plan lookup, coupon application,
 * response assembly), not the gateways themselves.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CouponService couponService;
    @Mock
    private RazorpayService razorpayService;
    @Mock
    private StripeService stripeService;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private CheckoutService checkoutService;

    private User user;
    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        RazorpayProperties razorpayProperties = new RazorpayProperties("test-key-id", "test-key-secret", "test-webhook-secret");
        checkoutService = new CheckoutService(
                subscriptionPlanRepository, userSubscriptionRepository, userRepository, paymentRepository,
                couponService, razorpayService, razorpayProperties, stripeService);

        user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");

        plan = new SubscriptionPlan();
        plan.setId(10L);
        plan.setCode("PROJECT_BASED");
        plan.setName("Project Based");
        plan.setPriceInr(new BigDecimal("14999.00"));
        plan.setActive(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    /** Not in {@code setUp()} — {@code unknownPlanCode_throwsPlanNotFound} stubs a different
     * code and never touches this one, and Mockito's strict stubbing fails a test that never
     * actually exercises a stub it declares. */
    private void stubValidPlan() {
        when(subscriptionPlanRepository.findByCode("PROJECT_BASED")).thenReturn(Optional.of(plan));
    }

    /** Also not in {@code setUp()}, same reasoning — the two not-found/inactive-plan tests throw
     * before ever reaching the payment-save code path. */
    private void stubPaymentPersistence() {
        // saveAndFlush/save both just return whatever Payment they're given, with an id assigned
        // the first time — mirrors what a real IDENTITY-strategy save does.
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void razorpayCheckout_noCoupon_usesFullPlanPriceAndPopulatesRazorpayFieldsOnly() {
        stubValidPlan();
        stubPaymentPersistence();
        // Mocked rather than constructed — Order's own constructor is the SDK's concern, not
        // this test's; all CheckoutService actually does with it is call order.get("id").
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_abc123");
        when(razorpayService.createOrder(eq(new BigDecimal("14999.00")), anyString())).thenReturn(order);

        CheckoutResponse response = checkoutService.checkout(1L,
                new CheckoutRequest("PROJECT_BASED", PaymentGateway.RAZORPAY, null, "FULL_STACK"));

        assertThat(response.gateway()).isEqualTo(PaymentGateway.RAZORPAY);
        assertThat(response.amount()).isEqualByComparingTo("14999.00");
        assertThat(response.razorpayOrderId()).isEqualTo("order_abc123");
        assertThat(response.razorpayKeyId()).isEqualTo("test-key-id");
        assertThat(response.stripeCheckoutUrl()).isNull();
        verify(couponService, never()).preview(any(), any());
        verify(couponService, never()).redeem(any(), any(), any());
    }

    @Test
    void stripeCheckout_noCoupon_populatesStripeUrlOnly() {
        stubValidPlan();
        stubPaymentPersistence();
        Session session = new Session();
        session.setId("cs_test_abc123");
        session.setUrl("https://checkout.stripe.com/pay/cs_test_abc123");
        when(stripeService.createCheckoutSession(eq(new BigDecimal("14999.00")), eq("INR"), eq("Project Based"), any()))
                .thenReturn(session);

        CheckoutResponse response = checkoutService.checkout(1L,
                new CheckoutRequest("PROJECT_BASED", PaymentGateway.STRIPE, null, "FULL_STACK"));

        assertThat(response.gateway()).isEqualTo(PaymentGateway.STRIPE);
        assertThat(response.stripeCheckoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_abc123");
        assertThat(response.razorpayOrderId()).isNull();
        assertThat(response.razorpayKeyId()).isNull();
    }

    @Test
    void checkoutWithCoupon_usesDiscountedAmountForGatewayCallAndRedeemsAfterPaymentSaved() {
        stubValidPlan();
        stubPaymentPersistence();
        when(couponService.preview("SAVE20", new BigDecimal("14999.00")))
                .thenReturn(new CouponService.CouponPreview(null, new BigDecimal("11999.20")));
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_discounted");
        when(razorpayService.createOrder(eq(new BigDecimal("11999.20")), anyString())).thenReturn(order);

        CheckoutResponse response = checkoutService.checkout(1L,
                new CheckoutRequest("PROJECT_BASED", PaymentGateway.RAZORPAY, "SAVE20", "FULL_STACK"));

        assertThat(response.amount()).isEqualByComparingTo("11999.20");
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("11999.20");
        verify(couponService).redeem(eq("SAVE20"), eq(user), any(Payment.class));
    }

    @Test
    void unknownPlanCode_throwsPlanNotFound() {
        when(subscriptionPlanRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.checkout(1L,
                new CheckoutRequest("NOPE", PaymentGateway.RAZORPAY, null, "FULL_STACK")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    void inactivePlan_throwsPlanNotFound() {
        plan.setActive(false);
        stubValidPlan();

        assertThatThrownBy(() -> checkoutService.checkout(1L,
                new CheckoutRequest("PROJECT_BASED", PaymentGateway.RAZORPAY, null, "FULL_STACK")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    void alreadyHasActiveSubscription_throwsConflict_andNeverTouchesGateway() {
        stubValidPlan();
        when(userSubscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(new UserSubscription()));

        assertThatThrownBy(() -> checkoutService.checkout(1L,
                new CheckoutRequest("PROJECT_BASED", PaymentGateway.RAZORPAY, null, "FULL_STACK")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE));

        verify(razorpayService, never()).createOrder(any(), anyString());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rapidResubmit_reusesOpenRazorpayOrder_withoutCreatingAnother() {
        stubValidPlan();
        Payment open = new Payment();
        open.setId(100L);
        open.setAmount(new BigDecimal("14999.00"));
        open.setGatewayOrderId("order_open123");
        when(paymentRepository.findReusableCreated(eq(1L), eq(10L), eq(PaymentGateway.RAZORPAY), any()))
                .thenReturn(java.util.List.of(open));

        CheckoutResponse response = checkoutService.checkout(1L,
                new CheckoutRequest("PROJECT_BASED", PaymentGateway.RAZORPAY, null, "FULL_STACK"));

        assertThat(response.razorpayOrderId()).isEqualTo("order_open123");
        assertThat(response.paymentId()).isEqualTo(100L);
        verify(razorpayService, never()).createOrder(any(), anyString());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
}
