package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.data.entity.NseHolidayEntity;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.data.service.NseHolidayService;
import com.swingtrade.data.service.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoints for manual data ingestion triggers.
 */
@RestController
@RequestMapping("/api")
public class IngestionController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    private final DataIngestionService dataIngestionService;
    private final WatchlistService watchlistService;
    private final NseHolidayService holidayService;

    public IngestionController(DataIngestionService dataIngestionService, WatchlistService watchlistService, NseHolidayService holidayService) {
        this.dataIngestionService = dataIngestionService;
        this.watchlistService = watchlistService;
        this.holidayService = holidayService;
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

    /**
     * Check if today is an NSE holiday.
     */
    @GetMapping("/holidays/today")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkToday() {
        boolean closed = holidayService.isTodayMarketClosed();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        Map<String, Object> reason;
        if (closed) {
            Optional<NseHolidayEntity> opt = holidayService.getHoliday(today);
            if (opt.isPresent()) {
                NseHolidayEntity h = opt.get();
                reason = Map.of("occasion", h.getOccasion(), "type", h.getHolidayType());
            } else {
                reason = Map.of("reason", "weekend");
            }
        } else {
            reason = Map.of();
        }

        Map<String, Object> data = Map.of(
            "date", today.toString(),
            "marketClosed", closed,
            "reason", reason
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /**
     * List all NSE holidays.
     */
    @GetMapping("/holidays")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listHolidays(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        try {
            List<NseHolidayEntity> holidays;
            if (from != null && to != null) {
                holidays = holidayService.getHolidays(LocalDate.parse(from), LocalDate.parse(to));
            } else {
                holidays = holidayService.getUpcomingHolidays(LocalDate.now(ZoneId.of("Asia/Kolkata")));
            }

            List<Map<String, Object>> result = holidays.stream().map(h -> Map.<String, Object>of(
                "date", h.getHolidayDate().toString(),
                "occasion", h.getOccasion(),
                "type", h.getHolidayType()
            )).toList();

            Map<String, Object> payload = new HashMap<>();
            payload.put("count", result.size());
            payload.put("holidays", result);
            return ResponseEntity.ok(ApiResponse.ok(payload));
        } catch (Exception e) {
            logger.error("Failed to list holidays: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to list holidays: " + e.getMessage()));
        }
    }

    /**
     * Add a new NSE holiday.
     */
    @PostMapping("/holidays")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addHoliday(
            @RequestParam LocalDate date,
            @RequestParam String occasion,
            @RequestParam(defaultValue = "FULL") String type) {

        try {
            NseHolidayEntity entity = holidayService.addHoliday(date, occasion, type);
            Map<String, Object> data = Map.of(
                "date", entity.getHolidayDate().toString(),
                "occasion", entity.getOccasion(),
                "type", entity.getHolidayType(),
                "status", "added"
            );
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            logger.error("Failed to add holiday: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to add holiday: " + e.getMessage()));
        }
    }

    /**
     * Remove an NSE holiday.
     */
    @DeleteMapping("/holidays/{date}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeHoliday(@PathVariable LocalDate date) {
        try {
            boolean removed = holidayService.removeHoliday(date);
            if (removed) {
                return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "date", date.toString(),
                    "status", "removed"
                )));
            }
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("No holiday found on " + date));
        } catch (Exception e) {
            logger.error("Failed to remove holiday: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to remove holiday: " + e.getMessage()));
        }
    }
}
