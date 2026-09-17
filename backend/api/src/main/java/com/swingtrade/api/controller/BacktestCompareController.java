package com.swingtrade.api.controller;

import com.swingtrade.api.dto.strategy.BacktestCompareRequest;
import com.swingtrade.api.dto.strategy.BacktestCompareResponse;
import com.swingtrade.api.service.BacktestCompareService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/backtest/compare} (plan §6.8): runs {@link com.swingtrade.strategy.PortfolioBacktestEngine}
 * (optionally walk-forward, plan §6.3) for a set of strategy variant/version pairs over a shared
 * date range and persists the results. See {@link BacktestCompareService}'s Javadoc for the
 * sync-vs-async design decision.
 */
@RestController
@RequestMapping("/api/backtest")
public class BacktestCompareController {

    private final BacktestCompareService service;

    public BacktestCompareController(BacktestCompareService service) {
        this.service = service;
    }

    @PostMapping("/compare")
    public ResponseEntity<BacktestCompareResponse> compare(@RequestBody BacktestCompareRequest request) {
        return ResponseEntity.ok(service.compare(request));
    }
}
