package com.swingtrade.api.test.fixtures;

import com.swingtrade.api.dto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ApiTestFixtures {

    // Factory methods for SignalResponse test data
    public static SignalResponse createBuySignal(String symbol) {
        return new SignalResponse(
            symbol,
            LocalDate.now(),
            SignalResponse.SignalType.BUY,
            new BigDecimal("0.85"),
            "Strong bullish momentum with RSI oversold",
            new BigDecimal("2500.00"),
            new BigDecimal("2450.00"),
            new BigDecimal("2600.00"),
            new BigDecimal("2.5"),
            List.of("RSI=35", "MACD bullish crossover"),
            LocalDateTime.now()
        );
    }

    public static SignalResponse createSellSignal(String symbol) {
        return new SignalResponse(
            symbol,
            LocalDate.now(),
            SignalResponse.SignalType.SELL,
            new BigDecimal("0.75"),
            "Bearish divergence detected",
            new BigDecimal("3800.00"),
            new BigDecimal("3850.00"),
            new BigDecimal("3700.00"),
            new BigDecimal("2.0"),
            List.of("RSI=72", "resistance rejection"),
            LocalDateTime.now()
        );
    }

    public static SignalResponse createHoldSignal(String symbol) {
        return new SignalResponse(
            symbol,
            LocalDate.now(),
            SignalResponse.SignalType.HOLD,
            new BigDecimal("0.55"),
            "Mixed signals, wait for confirmation",
            new BigDecimal("1500.00"),
            new BigDecimal("1480.00"),
            new BigDecimal("1520.00"),
            new BigDecimal("1.5"),
            List.of("Neutral RSI", "sideways trend"),
            LocalDateTime.now()
        );
    }

    // Factory methods for PositionResponse test data
    public static PositionResponse createLongPosition(String symbol, BigDecimal entryPrice, int quantity) {
        return new PositionResponse(
            symbol,
            entryPrice,
            LocalDate.now(),
            quantity,
            entryPrice.multiply(new BigDecimal("0.95")),
            entryPrice.multiply(new BigDecimal("1.10")),
            PositionResponse.PositionStatus.OPEN,
            "Breakout above resistance",
            entryPrice,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            entryPrice,
            entryPrice.multiply(BigDecimal.valueOf(quantity)),
            null,
            null,
            null
        );
    }

    // Factory methods for PerformanceResponse test data
    public static PerformanceResponse createPerformanceMetrics() {
        return new PerformanceResponse(
            new BigDecimal("15.5"),
            new BigDecimal("18.2"),
            new BigDecimal("1.2"),
            new BigDecimal("-5.3"),
            new BigDecimal("1.5"),
            10,
            7,
            3,
            70,
            new BigDecimal("5000"),
            new BigDecimal("2000"),
            new BigDecimal("3100"),
            new BigDecimal("2.5"),
            4,
            2,
            5,
            LocalDateTime.now()
        );
    }

    // Factory methods for ScanResponse test data
    public static ScanResponse createScanResult() {
        return new ScanResponse(
            LocalDateTime.now(),
            ScanResponse.ScanStatus.COMPLETED,
            500,
            3,
            2,
            1,
            0,
            List.of("RELIANCE", "TCS", "INFY"),
            "Scan completed successfully"
        );
    }

    // Helper methods
    public static List<SignalResponse> createSampleSignals() {
        return List.of(
            createBuySignal("RELIANCE"),
            createSellSignal("TCS"),
            createHoldSignal("INFY")
        );
    }
}
