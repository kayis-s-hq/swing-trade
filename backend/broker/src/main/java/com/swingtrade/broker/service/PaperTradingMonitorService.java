package com.swingtrade.broker.service;

import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.domain.Position;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.PriceBand;
import com.swingtrade.domain.PriceBandPolicy;
import com.swingtrade.domain.RiskManagementPolicy;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.strategy.TrailingBreakevenPolicy;
import com.swingtrade.domain.store.PriceBandStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;

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
                    if (!applyRiskManagement(pos, candle, null)) {
                        engine.updatePositionsFromDomain(candle);
                    }
                } else {
                    PriceBand band = priceBandStore.findBySymbolAndDate(pos.symbol(), candle.date()).orElse(null);
                    if (!applyRiskManagement(pos, candle, band)) {
                        engine.updatePositionsFromDomain(candle, band);
                    }
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

    private boolean applyRiskManagement(Position position, OhlcvCandle candle, PriceBand band) {
        if (!properties.isRiskManagementEnabled() || position.direction() != TradeDirection.LONG
                || position.stopLoss() == null || position.target() == null) {
            return false;
        }
        if (PriceBandPolicy.blocksLongExit(band, candle)) {
            return false;
        }
        RiskManagementPolicy policy = new TrailingBreakevenPolicy(
                properties.getBreakevenRiskMultiple(), properties.getTrailingStopPct(),
                properties.getPartialExitRiskMultiple(), properties.getPartialExitRatio(),
                properties.getChandelierAtrMultiple());
        List<OhlcvCandle> previousCandles = ohlcvCandleRepository.findAllBySymbolOrderByDateDesc(position.symbol())
                .stream()
                .map(OhlcvCandleEntity::toDomain)
                .filter(previous -> previous != null && previous.date() != null
                        && previous.date().isBefore(candle.date())
                        && previous.close() != null && previous.close().signum() > 0)
                .toList();
        BigDecimal highestCloseBeforeBar = previousCandles.stream()
                .map(OhlcvCandle::close)
                .max(BigDecimal::compareTo)
                .orElse(position.entryPrice());
        BigDecimal currentAtr = calculateAtrBeforeBar(previousCandles);
        RiskManagementPolicy.RiskManagementDecision decision = policy.evaluate(
                        new RiskManagementPolicy.RiskManagementContext(position.entryPrice(), position.stopLoss(),
                        position.target(), candle.close(), candle.low(), candle.high(), highestCloseBeforeBar,
                        (int) ChronoUnit.DAYS.between(position.entryDate(), candle.date()),
                        position.partialExitTaken(), currentAtr));
        if (decision.partialExitRatio() != null) {
            BigDecimal fill = candle.open();
            if (fill == null || fill.compareTo(decision.stopPrice()) > 0) fill = decision.stopPrice();
            try {
                engine.partialExitPosition(position.positionId(), decision.partialExitRatio(), fill);
                logger.info("Partial managed exit for {} at {}: {}", position.positionId(), fill,
                        decision.partialExitRatio());
                return true;
            } catch (IllegalArgumentException e) {
                logger.warn("Unable to apply partial managed exit for {}: {}", position.positionId(), e.getMessage());
                return false;
            }
        }
        if (!decision.exit()) return false;

        BigDecimal fill = candle.open();
        if (fill == null || (fill.compareTo(decision.stopPrice()) > 0)) {
            fill = decision.stopPrice();
        }
        engine.closePosition(position.positionId(), fill, decision.reason());
        logger.info("Managed risk exit for {} at {}: {}", position.positionId(), fill, decision.reason());
        return true;
    }

    private BigDecimal calculateAtrBeforeBar(List<OhlcvCandle> descendingCandles) {
        List<OhlcvCandle> candles = new ArrayList<>(descendingCandles);
        candles.sort(Comparator.comparing(OhlcvCandle::date));
        if (candles.size() < 2) return null;
        int start = Math.max(1, candles.size() - 14);
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (int i = start; i < candles.size(); i++) {
            OhlcvCandle current = candles.get(i);
            OhlcvCandle previous = candles.get(i - 1);
            if (current.high() == null || current.low() == null || previous.close() == null) continue;
            BigDecimal trueRange = current.high().subtract(current.low())
                    .max(current.high().subtract(previous.close()).abs())
                    .max(current.low().subtract(previous.close()).abs());
            total = total.add(trueRange);
            count++;
        }
        return count == 0 ? null : total.divide(BigDecimal.valueOf(count), 8, java.math.RoundingMode.HALF_UP);
    }

    private OhlcvCandle fetchLatestCandle(String symbol) {
        // Get latest candle from DB after EodIngestionScheduler fetches at 16:30.
        return ohlcvCandleRepository.findLatestBySymbol(symbol)
            .map(OhlcvCandleEntity::toDomain)
            .orElse(null);
    }
}
