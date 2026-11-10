package com.kuklin.manageapp.common.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static final String RAILWAY_PROXY = "100.64.0.3";

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void directClientCannotSpoofHeaders() {
        MockHttpServletRequest request = request("203.0.113.7");
        request.addHeader("X-Real-IP", "1.1.1.1");
        request.addHeader("X-Forwarded-For", "2.2.2.2");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void railwayUsesRealIp() {
        MockHttpServletRequest request = request(RAILWAY_PROXY);
        request.addHeader("X-Real-IP", "198.51.100.5");
        request.addHeader("X-Forwarded-For", "198.51.100.5");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.5");
    }

    @Test
    void railwayRealIpIsInfrastructureFallsBackToForwardedFor() {
        MockHttpServletRequest request = request(RAILWAY_PROXY);
        request.addHeader("X-Real-IP", "100.64.0.9");
        request.addHeader("X-Forwarded-For", "198.51.100.5, 100.64.0.9");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.5");
    }

    @Test
    void proxyWithoutHeadersFallsBackToRemoteAddr() {
        assertThat(ClientIpResolver.resolve(request("10.0.0.2"))).isEqualTo("10.0.0.2");
    }

    @Test
    void trustedProxyRanges() {
        assertThat(ClientIpResolver.isTrustedProxy("127.0.0.1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("::1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("10.1.2.3")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("172.17.0.1")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("192.168.1.10")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("100.64.0.3")).isTrue();
        assertThat(ClientIpResolver.isTrustedProxy("fd00::1")).isTrue();

        assertThat(ClientIpResolver.isTrustedProxy("8.8.8.8")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy("172.32.0.1")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy("2001:db8::1")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy("example.com")).isFalse();
        assertThat(ClientIpResolver.isTrustedProxy(null)).isFalse();
    }
}
