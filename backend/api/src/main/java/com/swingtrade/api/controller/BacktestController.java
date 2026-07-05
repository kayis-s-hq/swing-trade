package com.swingtrade.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestReportSummary;
import com.swingtrade.strategy.BacktestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * REST endpoints for running backtests and retrieving saved backtest reports.
 */
@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private static final Logger logger = LoggerFactory.getLogger(BacktestController.class);
    private static final Pattern REPORT_FILENAME_PATTERN = Pattern.compile("^backtest_\\d{8}_\\d{6}\\.json$");

    private final BacktestEngine backtestEngine;
    private final ObjectMapper objectMapper;
    private final String reportsDir;

    public BacktestController(BacktestEngine backtestEngine,
                               ObjectMapper objectMapper,
                               @Value("${backtest.reports.dir:reports}") String reportsDir) {
        this.backtestEngine = backtestEngine;
        this.objectMapper = objectMapper;
        this.reportsDir = reportsDir;
    }

    /**
     * Runs a backtest for a single symbol using default {@link BacktestConfig} parameters.
     *
     * @param symbol   stock symbol
     * @param exchange exchange code (accepted for API symmetry; not used to filter candles)
     * @return the backtest result, or 404 if there isn't enough candle history
     */
    @PostMapping("/run")
    public ResponseEntity<BacktestResult> runBacktest(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "NSE") String exchange) {
        logger.info("Running backtest for {} on {}", symbol, exchange);
        try {
            BacktestResult result = backtestEngine.runBacktest(symbol, exchange, BacktestConfig.defaults());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            logger.warn("Backtest failed for {}: {}", symbol, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Runs a backtest across every symbol in the active watchlist and saves a JSON+CSV report.
     *
     * @param exchange exchange code (accepted for API symmetry; not used to filter candles)
     * @return the aggregated report summary
     */
    @PostMapping("/run-all")
    public ResponseEntity<BacktestReportSummary> runBacktestAll(
            @RequestParam(defaultValue = "NSE") String exchange) {
        logger.info("Running backtest for all watchlist symbols on {}", exchange);
        List<BacktestResult> results = backtestEngine.runBacktestAll(exchange, BacktestConfig.defaults());
        BacktestReportSummary summary = backtestEngine.generateReport(results);
        return ResponseEntity.ok(summary);
    }

    /**
     * Lists saved backtest report filenames, most recent first.
     */
    @GetMapping("/reports")
    public ResponseEntity<List<String>> listReports() {
        Path dir = Path.of(reportsDir);
        if (!Files.isDirectory(dir)) {
            return ResponseEntity.ok(List.of());
        }

        try (Stream<Path> files = Files.list(dir)) {
            List<String> filenames = files
                .map(p -> p.getFileName().toString())
                .filter(name -> REPORT_FILENAME_PATTERN.matcher(name).matches())
                .sorted(Comparator.reverseOrder())
                .toList();
            return ResponseEntity.ok(filenames);
        } catch (IOException e) {
            logger.error("Failed to list backtest reports: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Returns the contents of a saved backtest report JSON file.
     */
    @GetMapping("/reports/{filename}")
    public ResponseEntity<JsonNode> getReport(@PathVariable String filename) {
        if (!REPORT_FILENAME_PATTERN.matcher(filename).matches()) {
            return ResponseEntity.badRequest().build();
        }

        Path dir = Path.of(reportsDir).toAbsolutePath().normalize();
        Path filePath = dir.resolve(filename).normalize();
        if (!filePath.startsWith(dir) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            JsonNode report = objectMapper.readTree(filePath.toFile());
            return ResponseEntity.ok(report);
        } catch (IOException e) {
            logger.error("Failed to read backtest report {}: {}", filename, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
