package com.moriah.skillhub.payment.gateway;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * library-docs.md "Razorpay". A thin wrapper around the SDK, deliberately isolated behind this
 * one class — `/architect feature 07`: with no real sandbox credentials available, {@code
 * CheckoutService}'s tests mock this class entirely rather than exercising a real network call;
 * only this class (and {@code RazorpayServiceTest}, if a real key is ever supplied) would need
 * to change if the gateway integration itself is ever exercised for real.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayService {

    private final RazorpayProperties props;

    /** {@code receipt} becomes {@code payments.gateway_order_id} — the reconciliation key
     * (library-docs.md: "always set it, it is the reconciliation key"). Amount is in rupees in;
     * Razorpay wants paise — {@code intValueExact()} so a fractional-paise rounding bug fails
     * loudly instead of silently truncating (a financial defect, per the same doc). */
    public Order createOrder(BigDecimal amountInr, String receipt) {
        try {
            RazorpayClient client = new RazorpayClient(props.keyId(), props.keySecret());
            JSONObject request = new JSONObject()
                    .put("amount", amountInr.multiply(BigDecimal.valueOf(100)).intValueExact())
                    .put("currency", "INR")
                    .put("receipt", receipt)
                    .put("payment_capture", 1);
            return client.orders.create(request);
        } catch (RazorpayException e) {
            log.error("[razorpay/checkout] order creation failed", e);
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    /** Verified against the raw request body bytes — {@code PaymentWebhookController} captures
     * those before any JSON parsing happens (library-docs.md: "the signature must be verified
     * against the raw request body bytes, before Jackson parses anything"). */
    public boolean verifySignature(String rawBody, String signatureHeader) {
        try {
            return Utils.verifyWebhookSignature(rawBody, signatureHeader, props.webhookSecret());
        } catch (RazorpayException e) {
            log.warn("[razorpay/webhook] signature verification failed");
            return false;
        }
    }
}
