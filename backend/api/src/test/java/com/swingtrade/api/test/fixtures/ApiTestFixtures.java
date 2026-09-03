package com.swingtrade.api.test.fixtures;

import com.swingtrade.api.dto.PerformanceResponse;
import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.ScanResponse;
import com.swingtrade.api.dto.SignalResponse;
import com.swingtrade.domain.PositionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ApiTestFixtures {

    private ApiTestFixtures() {
    }

    // Factory methods for SignalResponse test data
    public static SignalResponse createBuySignal(String symbol) {
        SignalResponse response = new SignalResponse();
        response.setSymbol(symbol);
        response.setDate(LocalDate.now());
        response.setSignalType(com.swingtrade.domain.Signal.SignalType.BUY);
        response.setConfidence(new BigDecimal("0.85"));
        response.setReasoning("Strong bullish momentum with RSI oversold");
        response.setEntryPrice(new BigDecimal("2500.00"));
        response.setStopLoss(new BigDecimal("2450.00"));
        response.setTarget(new BigDecimal("2600.00"));
        response.setRiskRewardRatio(new BigDecimal("2.5"));
        response.setIndicators(List.of("RSI=35", "MACD bullish crossover"));
        response.setGeneratedAt(LocalDate.now());
        return response;
    }

    public static SignalResponse createSellSignal(String symbol) {
        SignalResponse response = new SignalResponse();
        response.setSymbol(symbol);
        response.setDate(LocalDate.now());
        response.setSignalType(com.swingtrade.domain.Signal.SignalType.SELL);
        response.setConfidence(new BigDecimal("0.75"));
        response.setReasoning("Bearish divergence detected");
        response.setEntryPrice(new BigDecimal("3800.00"));
        response.setStopLoss(new BigDecimal("3850.00"));
        response.setTarget(new BigDecimal("3700.00"));
        response.setRiskRewardRatio(new BigDecimal("2.0"));
        response.setIndicators(List.of("RSI=72", "resistance rejection"));
        response.setGeneratedAt(LocalDate.now());
        return response;
    }

    public static SignalResponse createHoldSignal(String symbol) {
        SignalResponse response = new SignalResponse();
        response.setSymbol(symbol);
        response.setDate(LocalDate.now());
        response.setSignalType(com.swingtrade.domain.Signal.SignalType.HOLD);
        response.setConfidence(new BigDecimal("0.55"));
        response.setReasoning("Mixed signals, wait for confirmation");
        response.setEntryPrice(new BigDecimal("1500.00"));
        response.setStopLoss(new BigDecimal("1480.00"));
        response.setTarget(new BigDecimal("1520.00"));
        response.setRiskRewardRatio(new BigDecimal("1.5"));
        response.setIndicators(List.of("Neutral RSI", "sideways trend"));
        response.setGeneratedAt(LocalDate.now());
        return response;
    }

    // Factory methods for PositionResponse test data
    public static PositionResponse createLongPosition(String symbol, BigDecimal entryPrice, int quantity) {
        PositionResponse response = new PositionResponse();
        response.setId(1L);
        response.setSymbol(symbol);
        response.setEntryPrice(entryPrice);
        response.setEntryDate(LocalDate.now());
        response.setQuantity(quantity);
        response.setStopLoss(entryPrice.multiply(new BigDecimal("0.95")));
        response.setTarget(entryPrice.multiply(new BigDecimal("1.10")));
        response.setStatus(PositionStatus.OPEN);
        response.setEntryReason("Breakout above resistance");
        response.setCurrentPrice(entryPrice);
        response.setUnrealizedPnL(BigDecimal.ZERO);
        response.setUnrealizedPnLPercent(BigDecimal.ZERO);
        response.setAveragePrice(entryPrice);
        response.setTotalValue(entryPrice.multiply(BigDecimal.valueOf(quantity)));
        return response;
    }

    // Factory methods for PerformanceResponse test data
    public static PerformanceResponse createPerformanceMetrics() {
        PerformanceResponse response = new PerformanceResponse();
        response.setTotalReturn(new BigDecimal("15.5"));
        response.setTotalPnL(new BigDecimal("18.2"));
        response.setAnnualizedReturn(new BigDecimal("1.2"));
        response.setSharpeRatio(new BigDecimal("-5.3"));
        response.setMaxDrawdown(new BigDecimal("1.5"));
        response.setTotalTrades(10);
        response.setClosedTrades(7);
        response.setWinningTrades(7);
        response.setLosingTrades(3);
        response.setWinRate(new BigDecimal("70"));
        response.setAverageWin(new BigDecimal("5000"));
        response.setAverageLoss(new BigDecimal("2000"));
        response.setAverageTrade(new BigDecimal("3100"));
        response.setProfitFactor(new BigDecimal("2.5"));
        response.setLongestWinStreak(4);
        response.setLongestLossStreak(2);
        response.setAverageHoldingPeriod(5);
        response.setAsOfDate(LocalDateTime.now());
        response.setTotalCapitalGained(new BigDecimal("15000"));
        response.setTotalFeesPaid(new BigDecimal("350"));
        return response;
    }

    // Factory methods for ScanResponse test data
    public static ScanResponse createScanResult() {
        ScanResponse response = new ScanResponse();
        response.setScanTime(LocalDateTime.now());
        response.setStatus(ScanResponse.ScanStatus.COMPLETED);
        response.setSymbolsScanned(500);
        response.setSignalsFound(6);
        response.setBuySignals(3);
        response.setSellSignals(2);
        response.setHoldSignals(1);
        response.setScannedSymbols(List.of("RELIANCE", "TCS", "INFY"));
        response.setMessage("Scan completed successfully");
        return response;
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
