package com.swingtrade.api.metrics;

import com.swingtrade.domain.service.TradingService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PortfolioMetrics {

    private static final Logger log = LoggerFactory.getLogger(PortfolioMetrics.class);

    private final MeterRegistry meterRegistry;

    @Autowired
    public PortfolioMetrics(MeterRegistry meterRegistry, TradingService engine) {
        this.meterRegistry = meterRegistry;
        registerActivePositionsGauge(engine);
        registerTotalPnLGauge(engine, value -> ((TradingService) value).getTotalPnL().doubleValue());
        registerGauge("portfolio.value", engine,
            value -> ((TradingService) value).getTotalValue().doubleValue(),
            "Current paper portfolio value");
        registerDailyReturnGauge(engine,
            value -> {
                TradingService tradingService = (TradingService) value;
                var initial = tradingService.getInitialCapital();
                return initial.signum() == 0 ? 0.0
                    : tradingService.getTotalPnL().divide(initial, 8, java.math.RoundingMode.HALF_UP)
                        .movePointRight(2).doubleValue();
            });
        log.info("PortfolioMetrics initialized");
    }

    /** Lightweight constructor retained for isolated metric tests. */
    public PortfolioMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("PortfolioMetrics initialized without paper engine");
    }

    public void registerGauges(Object bean, java.util.function.ToDoubleFunction<Object> valueProvider, String metricName) {
        Gauge.builder(metricName, bean, valueProvider)
                .description("Portfolio metric: " + metricName)
                .register(meterRegistry);
    }

    public void registerActivePositionsGauge(Object positionTracker) {
        Gauge.builder("portfolio.active_positions", positionTracker,
                p -> ((TradingService) p).getOpenPositions().size())
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

    private void registerGauge(String name, Object bean,
                               java.util.function.ToDoubleFunction<Object> provider,
                               String description) {
        Gauge.builder(name, bean, provider).description(description).register(meterRegistry);
    }
}
