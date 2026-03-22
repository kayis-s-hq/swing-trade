package com.swingtrade.domain.fixtures;

import com.swingtrade.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Factory class for creating domain objects for testing.
 */
public class DomainObjectFactory {

    // Stock fixtures
    public static Stock createStock(String symbol, Stock.Exchange exchange, Stock.Sector sector) {
        return new Stock(
            symbol,
            exchange,
            symbol + " Industries Limited",
            sector,
            "INE" + symbol.substring(0, 5) + "0000",
            100,
            LocalDate.of(2020, 1, 1)
        );
    }

    // OhlcvCandle fixtures
    public static OhlcvCandle createCandle(String symbol, LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        return OhlcvCandle.of(symbol, date, open, high, low, close, 1000000L);
    }

    // Signal fixtures
    public static Signal createBuySignal(String symbol, BigDecimal confidence) {
        return Signal.create(symbol, LocalDate.now(), Signal.SignalType.BUY, confidence, "Technical breakout");
    }

    public static Signal createSellSignal(String symbol, BigDecimal confidence) {
        return Signal.create(symbol, LocalDate.now(), Signal.SignalType.SELL, confidence, "Resistance hit");
    }

    public static Signal createHoldSignal(String symbol, BigDecimal confidence) {
        return Signal.create(symbol, LocalDate.now(), Signal.SignalType.HOLD, confidence, "Wait for confirmation");
    }

    // Position fixtures
    public static Position createPosition(String symbol, BigDecimal entryPrice, Integer quantity, BigDecimal atr) {
        return Position.createWithRisk(symbol, entryPrice, LocalDate.now(), quantity, atr, "Entry on breakout");
    }

    // Trade fixtures
    public static Trade createOpenTrade(String symbol, BigDecimal entryPrice, Integer quantity, BigDecimal fees) {
        return Trade.open(1L, symbol, LocalDate.now(), entryPrice, quantity, "Entry on signal", fees);
    }

    public static Trade createClosedTrade(String symbol, BigDecimal entryPrice, BigDecimal exitPrice, Integer quantity, BigDecimal fees) {
        Trade openTrade = createOpenTrade(symbol, entryPrice, quantity, fees);
        return Trade.close(openTrade, LocalDate.now().plusDays(5), exitPrice, "Target hit");
    }

    // SentimentResult fixtures
    public static SentimentResult createPositiveSentiment(String symbol) {
        return SentimentResult.create(symbol, LocalDate.now(), SentimentResult.SentimentScore.POSITIVE, "Strong earnings", "News content", 0.85);
    }

    public static SentimentResult createNeutralSentiment(String symbol) {
        return SentimentResult.create(symbol, LocalDate.now(), SentimentResult.SentimentScore.NEUTRAL, "Mixed signals", "News content", 0.60);
    }

    public static SentimentResult createNegativeSentiment(String symbol) {
        return SentimentResult.create(symbol, LocalDate.now(), SentimentResult.SentimentScore.NEGATIVE, "Concerns raised", "News content", 0.75);
    }
}
