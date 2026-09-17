package com.swingtrade.data.service;

import com.swingtrade.data.entity.WatchlistEntity;
import com.swingtrade.data.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Daily end-of-day scheduler: fetches the latest candle for every watchlist stock.
 * Runs at 16:30 IST on weekdays (after market close).
 */
@Service
public class EodIngestionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(EodIngestionScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** Persisted benchmark symbol required for portfolio and excess-return metrics. */
    static final String NIFTY_50_SYMBOL = "NIFTY50";

    private final DataIngestionService dataIngestionService;
    private final WatchlistRepository watchlistRepository;
    private final NseHolidayService holidayService;

    @Value("${yahoo.finance.rate-limit-ms:500}")
    private long rateLimitMs;

    public EodIngestionScheduler(DataIngestionService dataIngestionService, WatchlistRepository watchlistRepository, NseHolidayService holidayService) {
        this.dataIngestionService = dataIngestionService;
        this.watchlistRepository = watchlistRepository;
        this.holidayService = holidayService;
    }

    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Kolkata")
    public void ingestLatestForAll() {
        LocalDate today = LocalDate.now(IST);
        LocalDate yesterday = today.minusDays(1);

        // Skip weekends (safety net)
        int day = today.getDayOfWeek().getValue();
        if (day > 5) {
            logger.debug("Today is {} — skipping scheduled EOD ingestion", today);
            return;
        }

        // Skip NSE holidays
        if (holidayService.isMarketClosed(yesterday)) {
            logger.info("EOD ingestion skipped: {} is an NSE market holiday", yesterday);
            return;
        }

        logger.info("EOD ingestion started at {} for date {}", LocalDate.now(IST), yesterday);
        long start = System.currentTimeMillis();

        List<WatchlistEntity> watchlist = watchlistRepository.findByIsActiveTrueOrderBySymbolAsc();
        List<String> symbols = new java.util.ArrayList<>(watchlist.stream()
                .map(WatchlistEntity::getSymbol)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .toList());
        if (!symbols.contains(NIFTY_50_SYMBOL)) symbols.add(NIFTY_50_SYMBOL);
        logger.info("EOD ingestion scheduled for {} watchlist symbols plus benchmark {}",
                watchlist.size(), NIFTY_50_SYMBOL);

        int success = 0;
        int failures = 0;

        for (String symbol : symbols) {
            try {
                dataIngestionService.processSingleStock(symbol, yesterday);
                success++;
            } catch (Exception e) {
                logger.warn("EOD ingestion failed for {}: {}", symbol, e.getMessage());
                failures++;
            }

            try {
                Thread.sleep(rateLimitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        logger.info("EOD ingestion completed: {} succeeded, {} failed, {}ms", success, failures, elapsed);
    }
}
