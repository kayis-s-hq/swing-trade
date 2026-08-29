package com.swingtrade.data.service;

import com.swingtrade.data.entity.WatchlistEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WatchlistService {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistService.class);

    private final WatchlistRepository watchlistRepository;
    private final OhlcvCandleRepository candleRepository;
    private final DataIngestionService dataIngestionService;
    private final MarketDataClientProvider marketDataClientProvider;

    // In-memory pull progress tracking
    private final ConcurrentHashMap<String, PullProgress> pullProgressMap = new ConcurrentHashMap<>();
    private final AtomicReference<String> activePullId = new AtomicReference<>(null);

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            OhlcvCandleRepository candleRepository,
            DataIngestionService dataIngestionService,
            MarketDataClientProvider marketDataClientProvider
    ) {
        this.watchlistRepository = watchlistRepository;
        this.candleRepository = candleRepository;
        this.dataIngestionService = dataIngestionService;
        this.marketDataClientProvider = marketDataClientProvider;
    }

    // -----------------------------------------------------------------------
    // Watchlist CRUD
    // -----------------------------------------------------------------------

    public List<WatchlistEntity> getActiveWatchlist() {
        return watchlistRepository.findByIsActiveTrueOrderBySymbolAsc();
    }

    public List<WatchlistEntity> getAllWatchlist() {
        return watchlistRepository.findAll();
    }

    public Optional<WatchlistEntity> getBySymbol(String symbol) {
        return watchlistRepository.findBySymbol(symbol);
    }

    @Transactional
    public WatchlistEntity addToWatchlist(String symbol, String name, String exchange) {
        Optional<WatchlistEntity> existing = watchlistRepository.findBySymbol(symbol);
        if (existing.isPresent()) {
            WatchlistEntity entity = existing.get();
            entity.setIsActive(true);
            return watchlistRepository.save(entity);
        }

        WatchlistEntity entity = new WatchlistEntity(symbol, name);
        entity.setExchange(exchange != null ? exchange : "NSE");
        return watchlistRepository.save(entity);
    }

    @Transactional
    public Optional<WatchlistEntity> removeFromWatchlist(String symbol) {
        Optional<WatchlistEntity> existing = watchlistRepository.findBySymbol(symbol);
        if (existing.isPresent()) {
            WatchlistEntity entity = existing.get();
            entity.setIsActive(false);
            return Optional.of(watchlistRepository.save(entity));
        }
        return Optional.empty();
    }

    @Transactional
    public WatchlistEntity toggleActive(String symbol, boolean isActive) {
        Optional<WatchlistEntity> existing = watchlistRepository.findBySymbol(symbol);
        if (existing.isPresent()) {
            WatchlistEntity entity = existing.get();
            entity.setIsActive(isActive);
            return watchlistRepository.save(entity);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Ingestion Status
    // -----------------------------------------------------------------------

    public List<Map<String, Object>> getIngestionStatus() {
        List<WatchlistEntity> watchlist = getActiveWatchlist();
        List<Map<String, Object>> results = new ArrayList<>();

        for (WatchlistEntity entry : watchlist) {
            Map<String, Object> status = new HashMap<>();
            status.put("symbol", entry.getSymbol());
            status.put("name", entry.getName());
            status.put("exchange", entry.getExchange());
            status.put("isActive", entry.getIsActive());

            // Get candle data from ohlcv_candles table
            long candleCount = candleRepository.countBySymbol(entry.getSymbol());
            status.put("candleCount", candleCount);

            Optional<com.swingtrade.data.entity.OhlcvCandleEntity> latest = candleRepository.findLatestBySymbol(entry.getSymbol());
            status.put("lastCandleDate", latest.map(com.swingtrade.data.entity.OhlcvCandleEntity::getDate).orElse(null));

            Optional<com.swingtrade.data.entity.OhlcvCandleEntity> earliest = candleRepository.findEarliestBySymbol(entry.getSymbol());
            status.put("earliestCandleDate", earliest.map(com.swingtrade.data.entity.OhlcvCandleEntity::getDate).orElse(null));

            status.put("lastSyncedAt", entry.getLastSyncedAt());
            status.put("hasData", candleCount > 0);
            status.put("dataQuality", candleCount > 0 ? this.assessDataQuality(entry.getSymbol(), candleCount) : "no_data");

            results.add(status);
        }

        return results;
    }

    private String assessDataQuality(String symbol, long actualCount) {
        Optional<com.swingtrade.data.entity.OhlcvCandleEntity> earliest = candleRepository.findEarliestBySymbol(symbol);
        if (earliest.isEmpty()) return "no_data";

        LocalDate startDate = earliest.get().getDate();
        LocalDate endDate = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        // Calculate expected trading days (approx 252 per year)
        long years = (long) (java.time.Duration.between(startDate.atStartOfDay(), endDate.atStartOfDay()).toDays() / 365.25);
        long expectedDays = Math.max(1, (long) (years * 252));

        double coverage = (double) actualCount / expectedDays;
        if (coverage >= 0.95) return "excellent";
        if (coverage >= 0.80) return "good";
        if (coverage >= 0.50) return "partial";
        return "poor";
    }

    // -----------------------------------------------------------------------
    // Data Pull Orchestration
    // -----------------------------------------------------------------------

    @Transactional
    public String startPullAll(int yearsBack) {
        LocalDate toDate = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        return startPullAll(toDate.minusYears(yearsBack), toDate);
    }

    @Transactional
    public String startPullAll(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }
        String pullId = "pull-" + System.currentTimeMillis();
        activePullId.set(pullId);

        List<WatchlistEntity> watchlist = getActiveWatchlist();
        PullProgress progress = new PullProgress(pullId, watchlist.size());
        pullProgressMap.put(pullId, progress);

        // Run in a separate thread to not block the HTTP request
        Thread pullThread = new Thread(() -> executePull(pullId, watchlist, fromDate, toDate));
        pullThread.setDaemon(true);
        pullThread.start();

        return pullId;
    }

    private void executePull(String pullId, List<WatchlistEntity> watchlist,
                             LocalDate fromDate, LocalDate toDate) {
        PullProgress progress = pullProgressMap.get(pullId);
        if (progress == null) return;

        for (WatchlistEntity entry : watchlist) {
            progress.updateCurrent(entry.getSymbol());
            try {
                // Request the complete configured range in one provider call. The database
                // upsert makes this idempotent while allowing the provider response to repair
                // internal gaps, not just append after the latest stored candle.
                dataIngestionService.processStockData(entry.getSymbol(), fromDate, toDate);

                // Update watchlist entry
                entry.setLastSyncedAt(LocalDateTime.now());
                entry.setCandleCount((int) candleRepository.countBySymbol(entry.getSymbol()));
                watchlistRepository.save(entry);

                progress.incrementCompleted();
                logger.info("Pull progress: {}/{} - {} complete", progress.getCompleted(), progress.getTotal(), entry.getSymbol());
            } catch (Exception e) {
                logger.error("Failed to pull data for {}: {}", entry.getSymbol(), e.getMessage());
                progress.incrementFailed();
            }
        }

        progress.setStatus("completed");
        activePullId.set(null);
        logger.info("Pull {} completed: {} succeeded, {} failed", pullId, progress.getCompleted(), progress.getFailed());
    }

    public Optional<PullProgress> getPullProgress(String pullId) {
        return Optional.ofNullable(pullProgressMap.get(pullId));
    }

    public Optional<PullProgress> getActivePullProgress() {
        if (activePullId.get() != null) {
            return Optional.ofNullable(pullProgressMap.get(activePullId.get()));
        }
        return Optional.empty();
    }

    public void cancelPull() {
        if (activePullId.get() != null) {
            PullProgress progress = pullProgressMap.get(activePullId.get());
            if (progress != null) {
                progress.setStatus("cancelled");
            }
            activePullId.set(null);
        }
    }

    // -----------------------------------------------------------------------
    // PullProgress Inner Class
    // -----------------------------------------------------------------------

    public static class PullProgress {
        private final String pullId;
        private final int total;
        private final AtomicInteger completed = new AtomicInteger(0);
        private final AtomicInteger failed = new AtomicInteger(0);
        private final AtomicReference<String> currentSymbol = new AtomicReference<>("");
        private final AtomicReference<String> status = new AtomicReference<>("running");

        public PullProgress(String pullId, int total) {
            this.pullId = pullId;
            this.total = total;
        }

        public String getPullId() { return pullId; }
        public int getTotal() { return total; }
        public int getCompleted() { return completed.get(); }
        public int getFailed() { return failed.get(); }
        public String getCurrentSymbol() { return currentSymbol.get(); }
        public String getStatus() { return status.get(); }

        void setStatus(String s) { status.set(s); }

        void updateCurrent(String symbol) {
            this.currentSymbol.set(symbol);
        }

        void incrementCompleted() {
            completed.incrementAndGet();
        }

        void incrementFailed() {
            failed.incrementAndGet();
        }

        public double getPercentComplete() {
            return total > 0 ? (double) completed.get() / total * 100 : 0;
        }
    }
}
