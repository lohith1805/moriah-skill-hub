package com.moriah.skillhub.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Confirmed the hard way (see the filter's own Javadoc): a Redis outage used to throw
 * uncaught out of this filter for every single request, including {@code /auth/login} — Spring
 * Security's {@code AuthenticationEntryPoint} then turned that into a deeply misleading
 * {@code UNAUTHENTICATED} response, hiding the real problem. This proves the fix: the filter
 * fails open (lets the request through, no 429) instead of propagating the Redis exception. The
 * "trips at the configured threshold" happy path is {@code RateLimitFilterIT} — that one needs a
 * real Redis (via Testcontainers) to prove the counter itself works; this one doesn't, so it's a
 * plain Mockito unit test instead. */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void redisUnavailable_failsOpenInsteadOfPropagatingTheException() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        RateLimitFilter filter = new RateLimitFilter(redisTemplate, new RateLimitProperties(3, 3),
                new ObjectMapper(), new ClientIpResolver(new TrustedProxyProperties(List.of()), new MockEnvironment()));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).as("the request reached downstream instead of being blocked").isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(429);
    }
}
