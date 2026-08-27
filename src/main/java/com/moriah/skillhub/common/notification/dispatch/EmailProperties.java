package com.moriah.skillhub.common.notification.dispatch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** No real SendGrid account exists yet — same no-real-credentials situation as Razorpay/Stripe/
 * OAuth2 (features 05/07). {@code EmailDispatcher} is unit-tested against a mocked SendGrid
 * client; these properties just need to bind at context startup. */
@ConfigurationProperties(prefix = "moriah.notification.email")
public record EmailProperties(String apiKey, String fromAddress, String fromName) {
}
