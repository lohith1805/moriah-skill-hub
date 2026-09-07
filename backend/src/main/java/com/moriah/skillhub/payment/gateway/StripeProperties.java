package com.moriah.skillhub.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bare env vars, same treatment as {@link RazorpayProperties}. {@code successUrl}/{@code
 * cancelUrl} are frontend routes Stripe redirects the browser back to after a hosted Checkout
 * session completes or is abandoned — this backend doesn't own those pages, just points at them. */
@ConfigurationProperties(prefix = "moriah.stripe")
public record StripeProperties(String secretKey, String webhookSecret, String successUrl, String cancelUrl) {
}
