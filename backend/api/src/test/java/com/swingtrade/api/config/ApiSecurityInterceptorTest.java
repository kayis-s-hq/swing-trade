package com.swingtrade.api.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityInterceptorTest {

    private static final Instant START = Instant.parse("2026-09-17T12:00:00Z");

    @Test
    void protectsAdminRoutesWithConstantTimeApiKeyBoundary() throws Exception {
        ApiSecurityInterceptor interceptor = interceptor(true, "secret", 100);
        MockHttpServletRequest request = request("/api/admin/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);

        request.addHeader(ApiSecurityInterceptor.API_KEY_HEADER, "secret");
        response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void failsClosedWhenEnforcementIsEnabledWithoutConfiguredKey() throws Exception {
        ApiSecurityInterceptor interceptor = interceptor(true, "", 100);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/api/admin/health"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void limitsPublicRequestsAndExposesHitMetric() throws Exception {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        ApiSecurityInterceptor interceptor = new ApiSecurityInterceptor(false, "", 2,
                Clock.fixed(START, ZoneOffset.UTC), metrics);
        for (int i = 0; i < 2; i++) {
            assertThat(interceptor.preHandle(request("/api/signals"), new MockHttpServletResponse(), new Object()))
                    .isTrue();
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/signals"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(metrics.get("api.rate.limit.hits").counter().count()).isEqualTo(1.0);
    }

    @Test
    void optionsRequestsBypassApiBoundaryForCorsPreflight() throws Exception {
        ApiSecurityInterceptor interceptor = interceptor(true, "secret", 1);
        MockHttpServletRequest request = request("/api/admin/health");
        request.setMethod("OPTIONS");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    private static ApiSecurityInterceptor interceptor(boolean enabled, String key, int limit) {
        return new ApiSecurityInterceptor(enabled, key, limit,
                Clock.fixed(START, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
