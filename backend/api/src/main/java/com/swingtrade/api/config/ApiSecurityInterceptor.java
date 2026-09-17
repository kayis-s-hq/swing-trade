package com.swingtrade.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight API boundary protection for the self-hosted deployment.
 *
 * <p>Admin routes can be protected with a shared API key without requiring a
 * full OAuth server, while all non-admin API routes receive a bounded fixed
 * window per-client request budget. The key is compared in constant time and
 * is never included in logs or response bodies.</p>
 */
@Component
public class ApiSecurityInterceptor implements HandlerInterceptor {

    static final String API_KEY_HEADER = "X-API-Key";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final boolean apiKeyEnabled;
    private final byte[] configuredApiKey;
    private final int publicLimit;
    private final Clock clock;
    private final Map<String, WindowCounter> windows = new ConcurrentHashMap<>();
    private final Counter rateLimitHits;

    @Autowired
    public ApiSecurityInterceptor(
            @Value("${app.security.api-key.enabled:false}") boolean apiKeyEnabled,
            @Value("${app.security.api-key:}") String apiKey,
            @Value("${app.security.public-rate-limit-per-minute:100}") int publicLimit,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(apiKeyEnabled, apiKey, publicLimit, Clock.systemUTC(),
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new));
    }

    ApiSecurityInterceptor(boolean apiKeyEnabled, String apiKey, int publicLimit,
                           Clock clock, MeterRegistry meterRegistry) {
        this.apiKeyEnabled = apiKeyEnabled;
        this.configuredApiKey = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
        this.publicLimit = Math.max(1, publicLimit);
        this.clock = clock;
        this.rateLimitHits = meterRegistry.counter("api.rate.limit.hits");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (isAdminPath(request)) {
            return authorizeAdmin(request, response);
        }
        return allowPublicRequest(request, response);
    }

    private boolean authorizeAdmin(HttpServletRequest request, HttpServletResponse response) {
        if (!apiKeyEnabled) {
            return true;
        }
        byte[] supplied = request.getHeader(API_KEY_HEADER) == null
                ? new byte[0]
                : request.getHeader(API_KEY_HEADER).getBytes(StandardCharsets.UTF_8);
        if (configuredApiKey.length > 0 && MessageDigest.isEqual(configuredApiKey, supplied)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", API_KEY_HEADER);
        return false;
    }

    private boolean allowPublicRequest(HttpServletRequest request, HttpServletResponse response) {
        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        Instant now = clock.instant();
        WindowCounter counter = windows.compute(client, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt.plus(WINDOW))) {
                return new WindowCounter(now, 1);
            }
            return current.incremented();
        });
        response.setHeader("X-RateLimit-Limit", String.valueOf(publicLimit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, publicLimit - counter.count)));
        if (counter.count <= publicLimit) {
            return true;
        }
        rateLimitHits.increment();
        long retryAfter = Math.max(1, Duration.between(now, counter.startedAt.plus(WINDOW)).toSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        return false;
    }

    private static boolean isAdminPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && (path.equals("/api/admin") || path.startsWith("/api/admin/"));
    }

    private record WindowCounter(Instant startedAt, int count) {
        WindowCounter incremented() {
            return new WindowCounter(startedAt, count + 1);
        }
    }
}
