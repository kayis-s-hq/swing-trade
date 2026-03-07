package com.swingtrade.api;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service for retrieving performance statistics
 */
@Service
public class PerformanceService {
    
    /**
     * Get performance statistics for backtesting and paper trading
     * @return Performance statistics
     */
    public PerformanceStats getPerformanceStats() {
        // In a real implementation, this would fetch performance data from a database or service
        return new PerformanceStats(
            new BigDecimal("15.75"),           // Total return
            new BigDecimal("22.30"),           // Annualized return
            new BigDecimal("1.45"),            // Sharpe ratio
            new BigDecimal("8.20"),            // Max drawdown
            42,                                // Total trades
            28,                                // Winning trades
            new BigDecimal("125.00"),          // Average win
            new BigDecimal("85.00"),           // Average loss
            LocalDateTime.now()                // As of date
        );
    }
}
