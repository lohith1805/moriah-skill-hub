package com.moriah.skillhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.security.ClientIpResolver;
import com.moriah.skillhub.common.security.RateLimitFilter;
import com.moriah.skillhub.common.security.RateLimitProperties;
import com.moriah.skillhub.common.security.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the rate limiter itself trips at the configured threshold, using a
 * {@link RateLimitProperties} instance constructed directly with a low limit — not the app-wide
 * configured value (deliberately raised to 1000/min for the {@code test} profile in
 * {@code application-test.yml}, so the many other IT classes making a handful of auth requests
 * each don't spuriously trip a shared, low, real-world limit).
 */
class RateLimitFilterIT extends IntegrationTestBase {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void requestsBeyondTheLimitReceive429() throws Exception {
        RateLimitProperties tightLimit = new RateLimitProperties(3, 3);
        ClientIpResolver clientIpResolver = new ClientIpResolver(new TrustedProxyProperties(List.of()), new MockEnvironment());
        RateLimitFilter filter = new RateLimitFilter(redisTemplate, tightLimit, objectMapper, clientIpResolver);

        String ip = "203.0.113." + System.nanoTime() % 250; // unique-ish per test run, avoids cross-run collision

        for (int i = 1; i <= 3; i++) {
            MockHttpServletResponse response = fireRequest(filter, ip);
            assertThat(response.getStatus()).as("request %d of 3 (within limit)", i).isNotEqualTo(429);
        }

        MockHttpServletResponse fourth = fireRequest(filter, ip);
        assertThat(fourth.getStatus()).isEqualTo(429);
        assertThat(fourth.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    private MockHttpServletResponse fireRequest(RateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
