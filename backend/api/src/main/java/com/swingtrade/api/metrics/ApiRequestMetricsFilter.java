package com.swingtrade.api.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Records request latency with bounded method/status tag cardinality. */
@Component
public class ApiRequestMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    public ApiRequestMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            filterChain.doFilter(request, response);
        } finally {
            sample.stop(Timer.builder("api.request.duration")
                .description("HTTP API request duration")
                .tag("method", request.getMethod())
                .tag("status", Integer.toString(response.getStatus()))
                .register(meterRegistry));
        }
    }
}
