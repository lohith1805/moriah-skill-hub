package com.moriah.skillhub.common.notification.dispatch;

import com.sendgrid.Client;
import com.sendgrid.SendGrid;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Both clients as injected beans, not constructed inline per-call — unlike {@code
 * RazorpayService}'s inline {@code new RazorpayClient(...)}, {@link EmailDispatcher}/{@link
 * WhatsAppDispatcher} each need their own unit test to verify the request they build is correct
 * (recipient, template, body), which needs a mockable collaborator injected via the constructor —
 * same reasoning {@code S3Client} is a {@code @Bean} rather than constructed inline.
 * <p>
 * <b>Audit 2026-08-31 (H3):</b> both clients now carry explicit connect/read timeouts. Without
 * them, a single unresponsive SendGrid or WhatsApp call blocks a notification-dispatch thread
 * indefinitely — and the whole pipeline stalls behind it.
 */
@Configuration
@EnableConfigurationProperties({EmailProperties.class, WhatsAppProperties.class})
public class NotificationClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    @Bean
    public SendGrid sendGridClient(EmailProperties props) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout((int) CONNECT_TIMEOUT.toMillis())
                .setConnectionRequestTimeout((int) CONNECT_TIMEOUT.toMillis())
                .setSocketTimeout((int) READ_TIMEOUT.toMillis())
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        return new SendGrid(props.apiKey(), new Client(httpClient));
    }

    @Bean
    public RestClient whatsAppRestClient(WhatsAppProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(props.apiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.accessToken())
                .requestFactory(requestFactory)
                .build();
    }
}
