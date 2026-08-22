package com.swingtrade.data.service;

import com.swingtrade.core.metrics.DataIngestionMetrics;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.data.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for ingesting and managing OHLCV market data.
 * Handles backfilling historical data and validating data quality.
 */
@Service
public class DataIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DataIngestionService.class);

    private final OhlcvCandleRepository candleRepository;
    private final StockRepository stockRepository;
    private final WatchlistRepository watchlistRepository;
    private final MarketDataClientProvider marketDataClientProvider;
    private final TransactionTemplate txTemplate;
    private final DataIngestionMetrics ingestionMetrics;

    public DataIngestionService(
        OhlcvCandleRepository candleRepository,
        StockRepository stockRepository,
        WatchlistRepository watchlistRepository,
        MarketDataClientProvider marketDataClientProvider,
        TransactionTemplate txTemplate,
        DataIngestionMetrics ingestionMetrics
    ) {
        this.candleRepository = candleRepository;
        this.stockRepository = stockRepository;
        this.watchlistRepository = watchlistRepository;
        this.marketDataClientProvider = marketDataClientProvider;
        this.txTemplate = txTemplate;
        this.ingestionMetrics = ingestionMetrics;
    }

    /**
     * Backfill historical data for a specific stock.
     *
     * @param stockSymbol the stock symbol
     * @param yearsBack number of years to backfill
     */
    @Transactional
    public void backfillStockData(String stockSymbol, int yearsBack) {
        logger.info("Starting backfill for {}: {} years of historical data", stockSymbol, yearsBack);

        LocalDate toDate = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate fromDate = toDate.minusYears(yearsBack);

        processStockData(stockSymbol, fromDate, toDate);

        logger.info("Backfill completed for {}", stockSymbol);
    }

    /**
     * Process stock data for a specific date range.
     *
     * @param symbol the stock symbol
     * @param fromDate start date
     * @param toDate end date
     */
    public void processStockData(String symbol, LocalDate fromDate, LocalDate toDate) {
        logger.info("Processing data for {} from {} to {}", symbol, fromDate, toDate);

        // Batch-fetch all candles in one API call
        Iterable<CandleData> candles = marketDataClientProvider.getClient().fetchCandles(symbol, fromDate, toDate);
        int saved = 0;
        int skipped = 0;
        for (CandleData candle : candles) {
            if (!candleRepository.existsBySymbolAndDate(symbol, candle.date())) {
                if (CandleValidator.isValid(candle)) {
                    txTemplate.execute(status -> {
                        saveCandle(symbol, candle);
                        return null;
                    });
                    saved++;
                } else {
                    skipped++;
                }
            }
        }
        logger.info("Processed {}: {} candles fetched, {} newly saved, {} rejected by validation",
            symbol, saved + skipped, saved, skipped);
    }

    /**
     * Process a single stock for a specific date.
     *
     * @param symbol the stock symbol
     * @param date the trading date
     */
    public void processSingleStock(String symbol, LocalDate date) {
        logger.debug("Processing single stock: {} for date {}", symbol, date);

        if (candleRepository.existsBySymbolAndDate(symbol, date)) {
            logger.trace("Candle already exists for {}: {}", symbol, date);
            return;
        }

        CandleData candle = marketDataClientProvider.getClient().fetchCandle(symbol, date);
        if (candle != null && CandleValidator.isValid(candle)) {
            txTemplate.execute(status -> {
                saveCandle(symbol, candle);
                ingestionMetrics.recordCandleIngested();
                return null;
            });
            logger.trace("Saved candle for {}: {}", symbol, date);
        } else if (candle != null) {
            logger.debug("Rejected invalid candle for {} on {}: {}", symbol, date, candle);
            ingestionMetrics.recordFetchFailure("unknown");
        }
    }

    /**
     * Pull data from Upstox API.
     * Uses the MarketDataClient (UpstoxServiceClient) for authenticated API calls.
     *
     * @param symbol the stock symbol
     * @param startDate start date
     * @param endDate end date
     * @return number of candles successfully ingested
     */
    @Transactional
    public int pullDataFromUpstox(String symbol, LocalDate startDate, LocalDate endDate) {
        logger.info("Pulling data from Upstox for {} from {} to {}", symbol, startDate, endDate);

        int count = 0;
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            if (candleRepository.existsBySymbolAndDate(symbol, current)) {
                logger.trace("Candle already exists for {}: {}", symbol, current);
                current = current.plusDays(1);
                continue;
            }

            CandleData candle = marketDataClientProvider.getClient().fetchCandle(symbol, current);
            if (candle != null && CandleValidator.isValid(candle)) {
                saveCandle(symbol, candle);
                count++;
                ingestionMetrics.recordCandleIngested();
                logger.trace("Ingested candle for {}: {}", symbol, current);
            } else if (candle != null) {
                logger.debug("Rejected invalid candle for {} on {}: {}", symbol, current, candle);
                ingestionMetrics.recordFetchFailure("upstox");
            } else {
                logger.warn("Failed to fetch candle for {}: {}", symbol, current);
                ingestionMetrics.recordFetchFailure("upstox");
            }

            current = current.plusDays(1);
        }

        logger.info("Data pull completed: {} candles ingested for {}", count, symbol);
        return count;
    }

    /**
     * Save a candle to the database.
     *
     * @param symbol the stock symbol
     * @param candle the candle data
     */
    private void saveCandle(String symbol, CandleData candle) {
        OhlcvCandleEntity entity = new OhlcvCandleEntity();
        entity.setSymbol(symbol);
        entity.setDate(candle.date());
        entity.setOpenPrice(candle.open());
        entity.setHighPrice(candle.high());
        entity.setLowPrice(candle.low());
        entity.setClosePrice(candle.close());
        entity.setVolume(candle.volume());
        entity.setAdjClosePrice(candle.adjClose());
        candleRepository.save(entity);
    }

    private List<LocalDate> getTradingDays(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (current.getDayOfWeek().getValue() <= 5) {
                tradingDays.add(current);
            }
            current = current.plusDays(1);
        }
        return tradingDays;
    }

    /**
     * Validate data quality for a stock.
     * Checks for missing candles and anomalies.
     *
     * @param stockSymbol the stock symbol
     * @param fromDate start date for validation
     * @param toDate end date for validation
     * @return DataQualityReport with validation results
     */
    public DataQualityReport validateDataQuality(String stockSymbol, LocalDate fromDate, LocalDate toDate) {
        logger.info("Validating data quality for {} from {} to {}", stockSymbol, fromDate, toDate);

        DataQualityReport report = new DataQualityReport();
        report.setStockSymbol(stockSymbol);
        report.setFromDate(fromDate);
        report.setToDate(toDate);

        // Calculate expected trading days
        long expectedTradingDays = getTradingDays(fromDate, toDate).size();
        report.setExpectedTradingDays(expectedTradingDays);

        // Get actual candles
        List<OhlcvCandleEntity> candles = candleRepository.findBySymbolAndDateRange(
            stockSymbol, fromDate, toDate,
            org.springframework.data.domain.Pageable.unpaged()
        );

        report.setActualTradingDays(candles.size());

        // Find gaps
        List<LocalDate> tradingDays = getTradingDays(fromDate, toDate);
        List<LocalDate> actualDates = candles.stream()
            .map(OhlcvCandleEntity::getDate)
            .toList();

        List<DataGap> gaps = new ArrayList<>();
        LocalDate currentGapStart = null;

        for (LocalDate expectedDate : tradingDays) {
            if (!actualDates.contains(expectedDate)) {
                if (currentGapStart == null) {
                    currentGapStart = expectedDate;
                }
            } else {
                if (currentGapStart != null) {
                    gaps.add(new DataGap("Missing data", currentGapStart, expectedDate.minusDays(1)));
                    currentGapStart = null;
                }
            }
        }

        // Handle gap at end of range
        if (currentGapStart != null) {
            gaps.add(new DataGap("Missing data", currentGapStart, toDate));
        }

        report.setGaps(gaps);
        report.setHasIssues(!gaps.isEmpty());

        // Check for price anomalies
        List<PriceAnomaly> anomalies = findPriceAnomalies(candles);
        report.setAnomalies(anomalies);

        return report;
    }

    /**
     * Find price anomalies in candle data.
     *
     * @param candles list of candles to check
     * @return list of anomalies found
     */
    private List<PriceAnomaly> findPriceAnomalies(List<OhlcvCandleEntity> candles) {
        List<PriceAnomaly> anomalies = new ArrayList<>();

        for (OhlcvCandleEntity candle : candles) {
            // High should be >= all other prices
            if (candle.getHighPrice().compareTo(candle.getOpenPrice()) < 0 ||
                candle.getHighPrice().compareTo(candle.getLowPrice()) < 0 ||
                candle.getHighPrice().compareTo(candle.getClosePrice()) < 0) {
                anomalies.add(new PriceAnomaly(
                    candle.getSymbol(),
                    candle.getDate(),
                    "High price invalid",
                    String.format("High: %s should be >= Open: %s, Low: %s, Close: %s",
                        candle.getHighPrice(), candle.getOpenPrice(),
                        candle.getLowPrice(), candle.getClosePrice())
                ));
                continue;
            }

            // Low should be <= all other prices
            if (candle.getLowPrice().compareTo(candle.getOpenPrice()) > 0 ||
                candle.getLowPrice().compareTo(candle.getHighPrice()) > 0 ||
                candle.getLowPrice().compareTo(candle.getClosePrice()) > 0) {
                anomalies.add(new PriceAnomaly(
                    candle.getSymbol(),
                    candle.getDate(),
                    "Low price invalid",
                    String.format("Low: %s should be <= Open: %s, High: %s, Close: %s",
                        candle.getLowPrice(), candle.getOpenPrice(),
                        candle.getHighPrice(), candle.getClosePrice())
                ));
            }
        }

        return anomalies;
    }

    /**
     * Get the latest candle for a stock.
     *
     * @param symbol the stock symbol
     * @return optional containing the latest candle
     */
    public Optional<OhlcvCandleEntity> getLatestCandle(String symbol) {
        return candleRepository.findLatestBySymbol(symbol);
    }

    /**
     * Get recent candles for a stock.
     *
     * @param symbol the stock symbol
     * @param days number of days to fetch
     * @return list of candles
     */
    public List<OhlcvCandleEntity> getRecentCandles(String symbol, int days) {
        return candleRepository.findTopBySymbolOrderByDateDesc(
            symbol,
            org.springframework.data.domain.PageRequest.of(0, days)
        );
    }

    /**
     * Data quality report for validating candle data.
     */
    public static class DataQualityReport {
        private String stockSymbol;
        private LocalDate fromDate;
        private LocalDate toDate;
        private long expectedTradingDays;
        private long actualTradingDays;
        private List<DataGap> gaps = new ArrayList<>();
        private List<PriceAnomaly> anomalies = new ArrayList<>();
        private boolean hasIssues = false;

        public String getStockSymbol() { return stockSymbol; }
        public void setStockSymbol(String stockSymbol) { this.stockSymbol = stockSymbol; }
        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate toDate) { this.toDate = toDate; }
        public long getExpectedTradingDays() { return expectedTradingDays; }
        public void setExpectedTradingDays(long expectedTradingDays) {
            this.expectedTradingDays = expectedTradingDays;
            this.hasIssues = actualTradingDays < expectedTradingDays;
        }
        public long getActualTradingDays() { return actualTradingDays; }
        public void setActualTradingDays(long actualTradingDays) {
            this.actualTradingDays = actualTradingDays;
            this.hasIssues = actualTradingDays < expectedTradingDays;
        }
        public List<DataGap> getGaps() { return gaps; }
        public void setGaps(List<DataGap> gaps) {
            this.gaps = gaps;
            this.hasIssues = !gaps.isEmpty();
        }
        public List<PriceAnomaly> getAnomalies() { return anomalies; }
        public void setAnomalies(List<PriceAnomaly> anomalies) {
            this.anomalies = anomalies;
            this.hasIssues = !anomalies.isEmpty();
        }
        public boolean hasIssues() { return hasIssues; }
        public void setHasIssues(boolean hasIssues) { this.hasIssues = hasIssues; }
    }

    /**
     * Represents a data gap.
     */
    public static class DataGap {
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;

        public DataGap(String description, LocalDate startDate, LocalDate endDate) {
            this.description = description;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    }

    /**
     * Represents a price anomaly.
     */
    public static class PriceAnomaly {
        private String symbol;
        private LocalDate date;
        private String type;
        private String details;

        public PriceAnomaly(String symbol, LocalDate date, String type, String details) {
            this.symbol = symbol;
            this.date = date;
            this.type = type;
            this.details = details;
        }

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
    }
}
