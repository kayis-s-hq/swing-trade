package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.data.service.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for manual data ingestion triggers.
 */
@RestController
@RequestMapping("/api")
public class IngestionController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    private final DataIngestionService dataIngestionService;
    private final WatchlistService watchlistService;

    public IngestionController(DataIngestionService dataIngestionService, WatchlistService watchlistService) {
        this.dataIngestionService = dataIngestionService;
        this.watchlistService = watchlistService;
    }

    /**
     * Backfill historical data for a single stock.
     */
    @PostMapping("/ingestion/backfill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> backfillStock(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "3") int years) {

        logger.info("Manual backfill requested for {} ({} years)", symbol, years);

        try {
            dataIngestionService.backfillStockData(symbol, years);

            long count = watchlistService.getIngestionStatus().stream()
                .filter(s -> symbol.equals(s.get("symbol")))
                .mapToLong(s -> ((Number) s.get("candleCount")).longValue())
                .findFirst()
                .orElse(0);

            Map<String, Object> data = Map.of(
                "symbol", symbol,
                "years", years,
                "candleCount", count,
                "status", "completed"
            );
            return ResponseEntity.ok(ApiResponse.ok(data));

        } catch (Exception e) {
            logger.error("Backfill failed for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to backfill: " + e.getMessage()));
        }
    }

    /**
     * Backfill all watchlist stocks.
     */
    @PostMapping("/ingestion/backfill-all")
    public ResponseEntity<ApiResponse<Map<String, String>>> backfillAll(@RequestParam(defaultValue = "3") int years) {
        logger.info("Manual backfill-all requested ({} years)", years);

        try {
            String pullId = watchlistService.startPullAll(years);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "pullId", pullId,
                "status", "started"
            )));

        } catch (Exception e) {
            logger.error("Backfill-all failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to start backfill: " + e.getMessage()));
        }
    }

    /**
     * Ingest only the latest candle for a stock.
     */
    @PostMapping("/ingestion/latest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ingestLatest(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "NSE") String exchange) {

        logger.info("Latest candle ingestion requested for {} ({})", symbol, exchange);

        try {
            dataIngestionService.processSingleStock(symbol, LocalDate.now());

            Map<String, Object> data = Map.of(
                "symbol", symbol,
                "exchange", exchange,
                "status", "completed"
            );
            return ResponseEntity.ok(ApiResponse.ok(data));

        } catch (Exception e) {
            logger.error("Latest ingestion failed for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to ingest latest: " + e.getMessage()));
        }
    }

    /**
     * Get candle count per symbol for all watchlist stocks.
     */
    @GetMapping("/ingestion/status")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getIngestionStatus() {
        try {
            List<Map<String, Object>> status = watchlistService.getIngestionStatus();
            return ResponseEntity.ok(ApiResponse.ok(status));
        } catch (Exception e) {
            logger.error("Failed to get ingestion status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get status: " + e.getMessage()));
        }
    }
}
