package com.swingtrade.api;

import com.swingtrade.api.service.SignalFilterService;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.model.Order;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.domain.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that polls for unprocessed BUY signals and auto-executes them as paper trades.
 */
@Service
public class SignalExecutionJob {

    private static final Logger logger = LoggerFactory.getLogger(SignalExecutionJob.class);

    private final SignalRepository signalRepository;
    private final OhlcvCandleRepository ohlcvCandleRepository;
    private final SignalFilterService signalFilterService;

    public SignalExecutionJob(SignalRepository signalRepository,
                              PaperTradingEngine paperTradingEngine,
                              OhlcvCandleRepository ohlcvCandleRepository,
                              SignalFilterService signalFilterService) {
        this.signalRepository = signalRepository;
        this.ohlcvCandleRepository = ohlcvCandleRepository;
        this.signalFilterService = signalFilterService;
    }

    @Scheduled(fixedDelayString = "${paper.trading.signal-execution-delay:30000}")
    public void executePendingSignals() {
        List<SignalEntity> pendingSignals = signalRepository.findUnprocessedBuySignalsSince(
            LocalDateTime.now().minusMinutes(5));

        for (SignalEntity signalEntity : pendingSignals) {
            executeSignal(signalEntity);
        }
    }

    private void executeSignal(SignalEntity signalEntity) {
        OhlcvCandleEntity latestCandle = ohlcvCandleRepository
            .findLatestBySymbolBeforeDate(signalEntity.getSymbol(), LocalDateTime.now())
            .orElse(null);

        if (latestCandle == null) {
            logger.warn("No candle data for {}, skipping signal execution", signalEntity.getSymbol());
            return;
        }

        BigDecimal currentPrice = latestCandle.getClosePrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            logger.warn("Invalid price for {}, skipping signal execution", signalEntity.getSymbol());
            return;
        }

        Signal domainSignal = new Signal(
            signalEntity.getId(),
            signalEntity.getSymbol(),
            signalEntity.getDate(),
            Signal.SignalType.valueOf(signalEntity.getSignalType()),
            signalEntity.getConfidenceScore(),
            signalEntity.getReasoning(),
            signalEntity.getEntryPrice(),
            signalEntity.getStopLoss(),
            signalEntity.getTarget(),
            signalEntity.getRiskReward(),
            signalEntity.getIndicators(),
            signalEntity.getGeneratedAt()
        );

        try {
            Order order = signalFilterService.filterAndProcess(domainSignal, currentPrice);
            signalEntity.setProcessed(true);
            signalRepository.save(signalEntity);
            if (order != null) {
                logger.info("Executed signal for {} at price {}", signalEntity.getSymbol(), currentPrice);
            } else {
                logger.info("Signal for {} suppressed by sentiment filter", signalEntity.getSymbol());
            }
        } catch (Exception e) {
            logger.warn("Failed to execute signal for {}: {}", signalEntity.getSymbol(), e.getMessage(), e);
            // Do NOT mark as processed — retry on next run
        }
    }
}