package com.swingtrade.broker.scheduler;

import com.swingtrade.broker.service.PaperTradingStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Daily portfolio snapshot scheduler for P&L chart data.
 * Runs at 16:30 IST (same time as EOD candle ingestion).
 */
@Service
public class PortfolioSnapshotScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioSnapshotScheduler.class);

    private final PaperTradingStateService stateService;

    public PortfolioSnapshotScheduler(PaperTradingStateService stateService) {
        this.stateService = stateService;
    }

    @Scheduled(cron = "${paper.trading.snapshot-cron:0 30 16 * * MON-FRI}", zone = "Asia/Kolkata")
    public void takeSnapshot() {
        try {
            stateService.saveSnapshot();
            logger.debug("Portfolio snapshot saved");
        } catch (Exception e) {
            logger.warn("Failed to save portfolio snapshot: {}", e.getMessage());
        }
    }
}