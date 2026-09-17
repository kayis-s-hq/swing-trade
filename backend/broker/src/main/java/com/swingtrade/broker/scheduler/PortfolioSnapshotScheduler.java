package com.swingtrade.broker.scheduler;

import com.swingtrade.domain.service.PaperPortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Daily portfolio snapshot scheduler for P&amp;L chart data.
 * Runs at 15:45 IST.
 *
 * <p>Plan §7.2 / finding F8: this used to only ever snapshot the single "default" portfolio
 * ({@code PaperTradingStateService.saveSnapshot()}). It now delegates to
 * {@link PaperPortfolioService#snapshotAllPortfolios()}, which iterates every known
 * {@code paper_trading_portfolio} row (one per active variant) and snapshots each.
 */
@Service
public class PortfolioSnapshotScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioSnapshotScheduler.class);

    private final PaperPortfolioService paperPortfolioService;

    public PortfolioSnapshotScheduler(PaperPortfolioService paperPortfolioService) {
        this.paperPortfolioService = paperPortfolioService;
    }

    @Scheduled(cron = "${paper.trading.snapshot-cron:0 45 15 * * MON-FRI}", zone = "Asia/Kolkata")
    public void takeSnapshot() {
        try {
            paperPortfolioService.snapshotAllPortfolios();
            logger.debug("Portfolio snapshots saved for all portfolios");
        } catch (Exception e) {
            logger.warn("Failed to save portfolio snapshots: {}", e.getMessage());
        }
    }
}
