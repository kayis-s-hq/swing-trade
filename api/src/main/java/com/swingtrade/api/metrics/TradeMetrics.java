package com.swingtrade.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TradeMetrics {

    private static final Logger log = LoggerFactory.getLogger(TradeMetrics.class);

    private final MeterRegistry meterRegistry;

    private final Counter tradeOpenCounter;
    private final Counter tradeCloseCounter;
    private final Counter tradeStopLossCounter;
    private final Counter tradeTargetHitCounter;
    private final Counter tradeTimeStopCounter;
    private final Counter tradeTrendBreakCounter;
    private final Counter tradeManualCounter;
    private final Timer tradeDurationTimer;
    private final Timer tradePnLTimer;

    private final Map<String, Counter> symbolTradeCounters = new ConcurrentHashMap<>();

    public TradeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.tradeOpenCounter = Counter.builder("trades.open")
                .description("Count of trade openings")
                .tag("broker", "paper")
                .register(meterRegistry);

        this.tradeCloseCounter = Counter.builder("trades.closed")
                .description("Count of trade closings")
                .tag("broker", "paper")
                .register(meterRegistry);

        this.tradeStopLossCounter = Counter.builder("trades.outcome")
                .description("Count of trades by exit reason")
                .tag("reason", "stop_loss")
                .register(meterRegistry);

        this.tradeTargetHitCounter = Counter.builder("trades.outcome")
                .description("Count of trades by exit reason")
                .tag("reason", "target_hit")
                .register(meterRegistry);

        this.tradeTimeStopCounter = Counter.builder("trades.outcome")
                .description("Count of trades by exit reason")
                .tag("reason", "time_stop")
                .register(meterRegistry);

        this.tradeTrendBreakCounter = Counter.builder("trades.outcome")
                .description("Count of trades by exit reason")
                .tag("reason", "trend_break")
                .register(meterRegistry);

        this.tradeManualCounter = Counter.builder("trades.outcome")
                .description("Count of trades by exit reason")
                .tag("reason", "manual")
                .register(meterRegistry);

        this.tradeDurationTimer = Timer.builder("trades.duration")
                .description("Trade duration in days")
                .baseTimeUnit(java.util.concurrent.TimeUnit.DAYS)
                .register(meterRegistry);

        this.tradePnLTimer = Timer.builder("trades.pnl")
                .description("Trade P&L in INR")
                .register(meterRegistry);

        log.info("TradeMetrics initialized with 9 counters and 2 timers");
    }

    public void recordTradeOpen() {
        tradeOpenCounter.increment();
    }

    public void recordTradeClose(String outcome) {
        tradeCloseCounter.increment();
        switch (outcome.toUpperCase()) {
            case "STOP_LOSS" -> tradeStopLossCounter.increment();
            case "TARGET_HIT" -> tradeTargetHitCounter.increment();
            case "TIME_STOP" -> tradeTimeStopCounter.increment();
            case "TREND_BREAK" -> tradeTrendBreakCounter.increment();
            case "MANUAL" -> tradeManualCounter.increment();
        }
    }

    public void recordTradeDuration(double days) {
        tradeDurationTimer.record(days, java.util.concurrent.TimeUnit.DAYS);
    }

    public void recordTradePnL(double pnl) {
        tradePnLTimer.record(Math.abs(pnl), java.util.concurrent.TimeUnit.INR);
    }

    public void recordSymbolTrade(String symbol) {
        symbolTradeCounters
                .computeIfAbsent(symbol, s -> Counter.builder("trades.per.symbol")
                        .description("Trade count per symbol")
                        .tag("symbol", s)
                        .register(meterRegistry));
        symbolTradeCounters.get(symbol).increment();
    }
}
