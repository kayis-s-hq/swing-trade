package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.data.service.WatchlistService;
import com.swingtrade.data.service.WatchlistService.PullProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class WatchlistController {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistController.class);

    private final WatchlistService watchlistService;

    private final DataIngestionService dataIngestionService;

    public WatchlistController(WatchlistService watchlistService,
                               DataIngestionService dataIngestionService) {
        this.watchlistService = watchlistService;
        this.dataIngestionService = dataIngestionService;
    }

    // -----------------------------------------------------------------------
    // Watchlist CRUD
    // -----------------------------------------------------------------------

    @GetMapping("/watchlist")
    public ResponseEntity<ApiResponse<Object>> getWatchlist() {
        return ResponseEntity.ok(ApiResponse.ok(watchlistService.getActiveWatchlist()));
    }

    @GetMapping("/watchlist/all")
    public ResponseEntity<ApiResponse<Object>> getAllWatchlist() {
        return ResponseEntity.ok(ApiResponse.ok(watchlistService.getAllWatchlist()));
    }

    @PostMapping("/watchlist")
    public ResponseEntity<ApiResponse<Object>> addToWatchlist(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "") String name,
            @RequestParam(required = false, defaultValue = "NSE") String exchange
    ) {
        if (watchlistService.getBySymbol(symbol).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Symbol " + symbol + " already in watchlist"));
        }
        var entity = watchlistService.addToWatchlist(symbol.toUpperCase(java.util.Locale.ROOT).trim(), name, exchange);
        return ResponseEntity.ok(ApiResponse.ok(entity));
    }

    @DeleteMapping("/watchlist/{symbol}")
    public ResponseEntity<ApiResponse<String>> removeFromWatchlist(@PathVariable String symbol) {
        watchlistService.removeFromWatchlist(symbol);
        return ResponseEntity.ok(ApiResponse.ok("Removed " + symbol + " from watchlist"));
    }

    @PatchMapping("/watchlist/{symbol}/toggle")
    public ResponseEntity<ApiResponse<Object>> toggleWatchlist(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "false") boolean activate
    ) {
        var entity = watchlistService.toggleActive(symbol, !activate);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(entity));
    }

    // -----------------------------------------------------------------------
    // Data Ingestion Status
    // -----------------------------------------------------------------------

    @GetMapping("/data/status")
    public ResponseEntity<ApiResponse<Object>> getIngestionStatus() {
        return ResponseEntity.ok(ApiResponse.ok(watchlistService.getIngestionStatus()));
    }

    // -----------------------------------------------------------------------
    // Data Pull
    // -----------------------------------------------------------------------

    @PostMapping("/data/pull")
    public ResponseEntity<ApiResponse<Object>> triggerDataPull(
            @RequestParam(defaultValue = "1") int yearsBack
    ) {
        // Check if there's already an active pull
        if (watchlistService.getActivePullProgress().isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Data pull already in progress"));
        }

        String pullId = watchlistService.startPullAll(yearsBack);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "pullId", pullId,
                "message", "Data pull started for " + yearsBack + " year(s) of historical data"
        )));
    }

    @GetMapping("/data/pull/progress")
    public ResponseEntity<ApiResponse<Object>> getPullProgress(
            @RequestParam(required = false) String pullId
    ) {
        PullProgress progress;
        if (pullId != null) {
            progress = watchlistService.getPullProgress(pullId).orElse(null);
        } else {
            progress = watchlistService.getActivePullProgress().orElse(null);
        }

        if (progress == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "status", "no_active_pull",
                    "message", "No active pull found"
            )));
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "pullId", progress.pullId,
                "status", progress.status,
                "total", progress.total,
                "completed", progress.completed.get(),
                "failed", progress.failed.get(),
                "currentSymbol", progress.currentSymbol,
                "percentComplete", Math.round(progress.getPercentComplete())
        )));
    }

    @PostMapping("/data/pull/cancel")
    public ResponseEntity<ApiResponse<String>> cancelPull() {
        watchlistService.cancelPull();
        return ResponseEntity.ok(ApiResponse.ok("Data pull cancelled"));
    }

    // -----------------------------------------------------------------------
    // Single Symbol Backfill
    // -----------------------------------------------------------------------

    @PostMapping("/data/backfill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> backfillSymbol(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "2") int yearsBack) {

        logger.info("Starting full backfill for {}: {} years", symbol, yearsBack);

        CompletableFuture.runAsync(() -> {
            try {
                dataIngestionService.backfillStockData(symbol, yearsBack);
                logger.info("Backfill completed for {}", symbol);
            } catch (Exception e) {
                logger.error("Backfill failed for {}: {}", symbol, e.getMessage(), e);
            }
        });

        Map<String, Object> data = Map.of(
            "symbol", symbol,
            "yearsBack", yearsBack,
            "status", "started",
            "message", "Backfill started for " + symbol + " (" + yearsBack + " year(s))"
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
