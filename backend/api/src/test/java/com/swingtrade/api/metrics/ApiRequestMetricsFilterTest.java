package com.swingtrade.api.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiRequestMetricsFilterTest {

    @Test
    void recordsDurationWithMethodAndStatusTagsEvenWhenHandlerFails() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiRequestMetricsFilter filter = new ApiRequestMetricsFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/positions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        FilterChain chain = mock(FilterChain.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("handler failure"))
            .when(chain).doFilter(request, response);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(request, response, chain))
            .isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("api.request.duration")
            .tag("method", "GET").tag("status", "500").timer().count()).isEqualTo(1);
    }
}
