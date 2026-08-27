package com.moriah.skillhub.common.notification.dispatch;

import com.sendgrid.SendGrid;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Both clients as injected beans, not constructed inline per-call — unlike {@code
 * RazorpayService}'s inline {@code new RazorpayClient(...)}, {@link EmailDispatcher}/{@link
 * WhatsAppDispatcher} each need their own unit test to verify the request they build is correct
 * (recipient, template, body), which needs a mockable collaborator injected via the constructor —
 * same reasoning {@code S3Client} is a {@code @Bean} rather than constructed inline.
 */
@Configuration
@EnableConfigurationProperties({EmailProperties.class, WhatsAppProperties.class})
public class NotificationClientConfig {

    @Bean
    public SendGrid sendGridClient(EmailProperties props) {
        return new SendGrid(props.apiKey());
    }

    @Bean
    public RestClient whatsAppRestClient(WhatsAppProperties props) {
        return RestClient.builder()
                .baseUrl(props.apiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.accessToken())
                .build();
    }
}
