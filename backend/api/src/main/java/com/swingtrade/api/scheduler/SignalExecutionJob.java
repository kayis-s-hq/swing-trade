package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.SignalFilterService;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that polls for unprocessed BUY signals and auto-executes them as paper trades.
 */
@Service
public class SignalExecutionJob {

    private static final Logger logger = LoggerFactory.getLogger(SignalExecutionJob.class);

    private final SignalStore signalStore;
    private final CandleStore candleStore;
    private final SignalFilterService signalFilterService;

    public SignalExecutionJob(SignalStore signalStore,
                              CandleStore candleStore,
                              SignalFilterService signalFilterService) {
        this.signalStore = signalStore;
        this.candleStore = candleStore;
        this.signalFilterService = signalFilterService;
    }

    @Scheduled(fixedDelayString = "${paper.trading.signal-execution-delay:30000}")
    public void executePendingSignals() {
        // Use Store to get unprocessed signals as domain objects
        List<Signal> pendingSignals = signalStore.findUnprocessed().stream()
                .filter(s -> s.type() == Signal.SignalType.BUY)
                .toList();

        for (Signal domainSignal : pendingSignals) {
            executeSignal(domainSignal);
        }
    }

    private void executeSignal(Signal domainSignal) {
        OhlcvCandle latestCandle = candleStore.findLatestBySymbolBeforeDate(
            domainSignal.symbol(), domainSignal.date())
            .orElse(null);

        if (latestCandle == null) {
            logger.warn("No candle data for {}, skipping signal execution", domainSignal.symbol());
            return;
        }

        BigDecimal currentPrice = latestCandle.close();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            logger.warn("Invalid price for {}, skipping signal execution", domainSignal.symbol());
            return;
        }

        try {
            Order order = signalFilterService.filterAndProcess(domainSignal, currentPrice);
            signalStore.markProcessed(domainSignal.id());
            if (order != null) {
                logger.info("Executed signal for {} at price {}", domainSignal.symbol(), currentPrice);
            } else {
                logger.info("Signal for {} suppressed by sentiment filter", domainSignal.symbol());
            }
        } catch (Exception e) {
            logger.warn("Failed to execute signal for {}: {}", domainSignal.symbol(), e.getMessage(), e);
            // Do NOT mark as processed — retry on next run
        }
    }
}