package com.swingtrade.broker.service;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.domain.Position;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.service.FyersServiceClient;
import com.swingtrade.domain.OhlcvCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fyers-specific EOD position monitor.
 * Runs after market close (16:45 IST) to fetch latest Fyers candle,
 * update position prices, and check SL/TP triggers.
 */
@Service
public class PaperTradingMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingMonitorService.class);

    private final PaperTradingEngine engine;
    private final PaperTradingStateService stateService;
    private final OhlcvCandleRepository ohlcvCandleRepository;

    public PaperTradingMonitorService(PaperTradingEngine engine,
                                      PaperTradingStateService stateService,
                                      OhlcvCandleRepository ohlcvCandleRepository) {
        this.engine = engine;
        this.stateService = stateService;
        this.ohlcvCandleRepository = ohlcvCandleRepository;
    }

    @Scheduled(cron = "${paper.trading.monitor-cron:0 45 16 * * MON-FRI}", zone = "Asia/Kolkata")
    public void monitorPositions() {
        List<Position> openPositions = engine.getOpenPositions();
        if (openPositions.isEmpty()) {
            logger.debug("No open positions to monitor");
            return;
        }

        logger.info("Monitoring {} open positions", openPositions.size());

        for (Position pos : openPositions) {
            try {
                OhlcvCandle candle = fetchLatestCandle(pos.symbol());
                if (candle == null) continue;

                // Persist candle to DB for signal engine use
                OhlcvCandleEntity entity = OhlcvCandleEntity.fromDomain(candle);
                ohlcvCandleRepository.save(entity);

                // Update position price and check SL/TP
                engine.updatePositionsFromDomain(candle);

                // Persist updated position
                if (stateService != null) {
                    stateService.savePosition(pos);
                }

            } catch (Exception e) {
                logger.warn("Failed to monitor position {} for {}: {}",
                    pos.positionId(), pos.symbol(), e.getMessage());
                // Best-effort: skip failed symbol, continue with others
            }
        }

        // Save portfolio snapshot
        if (stateService != null) {
            stateService.saveSnapshot();
            stateService.savePortfolio();
        }

        logger.info("Position monitoring complete");
    }

    private OhlcvCandle fetchLatestCandle(String symbol) {
        // Get latest candle from DB (EodIngestionScheduler fetches at 16:30)
        return ohlcvCandleRepository.findLatestBySymbol(symbol)
            .map(OhlcvCandleEntity::toDomain)
            .orElse(null);
    }
}