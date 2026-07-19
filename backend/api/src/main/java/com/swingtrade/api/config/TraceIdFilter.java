package com.swingtrade.api.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds traceId and symbol to MDC for structured log correlation.
 * traceId = UUID per request. symbol = extracted from query/path params.
 */
@Component
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID = "traceId";
    private static final String SYMBOL = "symbol";
    private static final String MODULE = "module";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        MDC.put(TRACE_ID, UUID.randomUUID().toString().substring(0, 8));
        MDC.put(MODULE, "api");

        // Extract symbol from common param names
        String symbol = httpReq.getParameter("symbol");
        if (symbol == null) {
            symbol = httpReq.getParameter("code");
        }
        if (symbol == null) {
            String path = httpReq.getRequestURI();
            if (path.contains("/signals/") || path.contains("/positions/")) {
                symbol = path.substring(path.lastIndexOf('/') + 1);
            }
        }
        if (symbol != null && !symbol.isBlank()) {
            MDC.put(SYMBOL, symbol);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}