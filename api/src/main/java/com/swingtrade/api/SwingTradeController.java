package com.swingtrade.api;

import com.swingtrade.api.dto.PositionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for Swing Trading System
 * Provides endpoints for signals, positions, performance metrics, and scanning
 */
@RestController
@RequestMapping("/api")
public class SwingTradeController {

    @Autowired
    private SignalService signalService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private PerformanceService performanceService;

    @Autowired
    private ScanService scanService;

    /**
     * Get the latest trading signals for today
     * @return List of latest trading signals
     */
    @GetMapping("/signals/latest")
    public ResponseEntity<List<SignalService.Signal>> getLatestSignals() {
        return ResponseEntity.ok(signalService.getLatestSignals());
    }

    /**
     * Get open paper trading positions
     * @return List of open paper positions
     */
    @GetMapping("/positions")
    public ResponseEntity<List<PositionResponse>> getOpenPositions() {
        return ResponseEntity.ok(positionService.getOpenPositions());
    }

    /**
     * Get performance statistics for backtesting and paper trading
     * @return Performance statistics
     */
    @GetMapping("/performance")
    public ResponseEntity<PerformanceService.PerformanceStats> getPerformance() {
        return ResponseEntity.ok(performanceService.getPerformanceStats());
    }

    /**
     * Trigger manual scan for trading opportunities
     * @return Scan result
     */
    @PostMapping("/scan")
    public ResponseEntity<ScanService.ScanResult> triggerScan() {
        return ResponseEntity.ok(scanService.triggerManualScan());
    }
}
