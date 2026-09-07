package com.moriah.skillhub.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static ClientIpResolver resolver(String... trustedProxies) {
        return new ClientIpResolver(new TrustedProxyProperties(List.of(trustedProxies)), new MockEnvironment());
    }

    @Test
    void untrustedRemoteAddr_ignoresForwardedForHeader_returnsRemoteAddr() {
        // No trusted proxies configured — X-Forwarded-For must never be believed, since anyone
        // calling directly could set it to anything.
        ClientIpResolver resolver = resolver();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void trustedRemoteAddr_withForwardedForHeader_returnsLeftmostClientIp() {
        ClientIpResolver resolver = resolver("10.0.0.5");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.99, 10.0.0.5");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.99");
    }

    @Test
    void trustedProxyCidrRange_withForwardedForHeader_returnsLeftmostClientIp() {
        // Audit 2026-08-31 (C4): trusted-proxies entries may be CIDR ranges, not just exact IPs.
        ClientIpResolver resolver = resolver("10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.44.12.9");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.99");
    }

    @Test
    void remoteAddrOutsideTrustedCidr_ignoresForwardedForHeader() {
        ClientIpResolver resolver = resolver("10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void trustedRemoteAddr_withoutForwardedForHeader_fallsBackToRemoteAddr() {
        ClientIpResolver resolver = resolver("10.0.0.5");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void trustedRemoteAddr_withBlankForwardedForHeader_fallsBackToRemoteAddr() {
        ClientIpResolver resolver = resolver("10.0.0.5");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void nullTrustedProxiesList_defaultsToEmpty_neverThrows() {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(null), new MockEnvironment());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }
}
