package com.swingtrade.api.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PortfolioMetrics {

    private static final Logger log = LoggerFactory.getLogger(PortfolioMetrics.class);

    private final MeterRegistry meterRegistry;

    public PortfolioMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("PortfolioMetrics initialized");
    }

    public void registerGauges(Object bean, java.util.function.ToDoubleFunction<Object> valueProvider, String metricName) {
        Gauge.builder(metricName, bean, valueProvider)
                .description("Portfolio metric: " + metricName)
                .register(meterRegistry);
    }

    public void registerActivePositionsGauge(Object positionTracker) {
        Gauge.builder("portfolio.active_positions", positionTracker,
                p -> 0)
                .description("Number of active positions")
                .register(meterRegistry);
    }

    public void registerTotalPnLGauge(Object portfolio, java.util.function.ToDoubleFunction<Object> pnlProvider) {
        Gauge.builder("portfolio.total_pnl", portfolio, pnlProvider)
                .description("Total portfolio P&L")
                .register(meterRegistry);
    }

    public void registerDailyReturnGauge(Object portfolio, java.util.function.ToDoubleFunction<Object> dailyReturnProvider) {
        Gauge.builder("portfolio.daily_return", portfolio, dailyReturnProvider)
                .description("Daily portfolio return percentage")
                .register(meterRegistry);
    }
}
