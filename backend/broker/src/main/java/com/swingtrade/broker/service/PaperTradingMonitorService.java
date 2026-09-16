package com.swingtrade.broker.service;

import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.domain.Position;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.PriceBand;
import com.swingtrade.domain.store.PriceBandStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * EOD position monitor.
 * Runs after EOD ingestion to fetch the latest candle,
 * update position prices, and check SL/TP triggers.
 */
@Service
public class PaperTradingMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingMonitorService.class);

    private final PaperTradingEngine engine;
    private final PaperTradingStateService stateService;
    private final OhlcvCandleRepository ohlcvCandleRepository;
    private final PaperTradingProperties properties;
    private final PriceBandStore priceBandStore;

    public PaperTradingMonitorService(PaperTradingEngine engine,
                                      PaperTradingStateService stateService,
                                      OhlcvCandleRepository ohlcvCandleRepository,
                                      PaperTradingProperties properties) {
        this(engine, stateService, ohlcvCandleRepository, properties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaperTradingMonitorService(PaperTradingEngine engine,
                                      PaperTradingStateService stateService,
                                      OhlcvCandleRepository ohlcvCandleRepository,
                                      PaperTradingProperties properties,
                                      PriceBandStore priceBandStore) {
        this.engine = engine;
        this.stateService = stateService;
        this.ohlcvCandleRepository = ohlcvCandleRepository;
        this.properties = properties;
        this.priceBandStore = priceBandStore;
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

                // Update position price and check SL/TP; this already persists the
                // resulting state (open or closed) via stateService internally.
                // Do not additionally save `pos` here — it is the pre-update
                // snapshot captured before this loop and would overwrite whatever
                // updatePositionsFromDomain just wrote with stale values.
                if (priceBandStore == null) {
                    engine.updatePositionsFromDomain(candle);
                } else {
                    PriceBand band = priceBandStore.findBySymbolAndDate(pos.symbol(), candle.date()).orElse(null);
                    engine.updatePositionsFromDomain(candle, band);
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
        // Get latest candle from DB after EodIngestionScheduler fetches at 16:30.
        return ohlcvCandleRepository.findLatestBySymbol(symbol)
            .map(OhlcvCandleEntity::toDomain)
            .orElse(null);
    }
}
