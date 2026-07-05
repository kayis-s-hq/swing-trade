package com.swingtrade.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@DisplayName("BacktestEngine")
@ExtendWith(MockitoExtension.class)
class BacktestEngineTest {

    private static final String SYMBOL = "TESTCO";
    private static final String EXCHANGE = "NSE";

    @Mock
    private OhlcvCandleRepository candleRepository;

    @Mock
    private WatchlistService watchlistService;

    private BacktestEngine engine;

    @BeforeEach
    void setUp() {
        PriceActionSignalEngine priceActionSignalEngine = new PriceActionSignalEngine(candleRepository);
        engine = new BacktestEngine(candleRepository, watchlistService, priceActionSignalEngine,
            new ObjectMapper(), "target/test-reports");
    }

    @Nested
    class InputValidation {

        @Test
        void runBacktest_withNullSymbol_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> engine.runBacktest(null, EXCHANGE, BacktestConfig.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Symbol cannot be null or blank");
        }

        @Test
        void runBacktest_withNullConfig_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> engine.runBacktest(SYMBOL, EXCHANGE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Config cannot be null");
        }

        @Test
        void runBacktest_withInsufficientCandles_throwsIllegalStateException() {
            List<OhlcvCandleEntity> tooFew = buildTrendingCandles(30, 100.0, 0.0, 1_000_000L);
            stub(tooFew);

            assertThatThrownBy(() -> engine.runBacktest(SYMBOL, EXCHANGE, BacktestConfig.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient candle history");
        }
    }

    @Nested
    class EntryAndExit {

        @Test
        @DisplayName("all entry rules satisfied -> a trade opens on the next day's open")
        void entrySignal_opensTradeAtNextOpen() {
            List<OhlcvCandleEntity> candles = buildEntrySetupCandles();
            appendFlatCandles(candles, 6, entryPrice(candles), 1_000_000L);
            stub(candles);

            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 5);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).isNotEmpty();
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.entryPrice().doubleValue()).isCloseTo(entryPrice(candles).doubleValue(), within(0.0001));
            assertThat(trade.quantity()).isPositive();
        }

        @Test
        @DisplayName("price crashes through the ATR stop -> STOP_LOSS exit at the stop price")
        void stopLossExit() {
            List<OhlcvCandleEntity> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            appendCandle(candles, entry, entry, entry.multiply(BigDecimal.valueOf(0.75)), entry.multiply(BigDecimal.valueOf(0.80)), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.STOP_LOSS);
            assertThat(trade.exitPrice()).isEqualByComparingTo(trade.stopLoss());
            assertThat(trade.pnl()).isNegative();
        }

        @Test
        @DisplayName("price rallies through the target -> TARGET_HIT exit at the target price")
        void targetHitExit() {
            List<OhlcvCandleEntity> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            appendCandle(candles, entry, entry.multiply(BigDecimal.valueOf(1.60)), entry, entry.multiply(BigDecimal.valueOf(1.50)), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.TARGET_HIT);
            assertThat(trade.exitPrice()).isEqualByComparingTo(trade.target());
            assertThat(trade.pnl()).isPositive();
        }

        @Test
        @DisplayName("position held flat past maxHoldingDays -> TIME_STOP exit at close")
        void timeStopExit() {
            List<OhlcvCandleEntity> candles = buildEntrySetupCandles();
            appendFlatCandles(candles, 6, entryPrice(candles), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 5);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.TIME_STOP);
            assertThat(trade.holdingDays()).isEqualTo(5);
        }

        @Test
        @DisplayName("close stays below EMA20 for 2 consecutive days -> TREND_BREAK exit")
        void trendBreakExit() {
            List<OhlcvCandleEntity> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            BigDecimal day1Close = entry.multiply(BigDecimal.valueOf(0.95));
            appendCandle(candles, entry, entry.multiply(BigDecimal.valueOf(1.001)), entry.multiply(BigDecimal.valueOf(0.94)), day1Close, 1_000_000L);
            BigDecimal day2Close = day1Close.multiply(BigDecimal.valueOf(0.95));
            appendCandle(candles, day1Close, day1Close.multiply(BigDecimal.valueOf(1.001)), day2Close.multiply(BigDecimal.valueOf(0.99)), day2Close, 1_000_000L);

            stub(candles);
            // Huge ATR multiplier pushes the stop far below entry so the down-days can never
            // trip STOP_LOSS first; this isolates the trend-break rule.
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 100.0, 2.5, 20);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.TREND_BREAK);
            assertThat(trade.holdingDays()).isEqualTo(2);
        }

        @Test
        @DisplayName("no second position opens while one is already open for the symbol")
        void onlyOnePositionAtATime() {
            List<OhlcvCandleEntity> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            // Repeat high-volume, at-the-high conditions while flat in price: these would
            // qualify as a fresh entry signal if a position weren't already open.
            appendFlatCandles(candles, 3, entry, 2_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 3);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            assertThat(result.trades().get(0).exitReason()).isEqualTo(ExitReason.TIME_STOP);
        }

        @Test
        @DisplayName("position size follows capital * riskPerTradePct / (entry - stopLoss)")
        void positionSizingMatchesRiskFormula() {
            List<OhlcvCandleEntity> candles = buildEntrySetupCandles();
            appendFlatCandles(candles, 3, entryPrice(candles), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 3);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            BacktestTrade trade = result.trades().get(0);
            BigDecimal riskPerShare = trade.entryPrice().subtract(trade.stopLoss());
            int expectedQuantity = (int) Math.floor((config.initialCapital() * config.riskPerTradePct()) / riskPerShare.doubleValue());

            assertThat(trade.quantity()).isEqualTo(expectedQuantity);
        }
    }

    // -----------------------------------------------------------------------
    // Test fixtures
    // -----------------------------------------------------------------------

    private void stub(List<OhlcvCandleEntity> chronologicalCandles) {
        List<OhlcvCandleEntity> descending = new ArrayList<>(chronologicalCandles);
        Collections.reverse(descending);
        when(candleRepository.findAllBySymbolOrderByDateDesc(SYMBOL)).thenReturn(descending);
    }

    /**
     * Builds a 301-candle chronological series: a 2-up/1-down zigzag uptrend (keeps RSI in the
     * 50-65 band) whose volume is constant everywhere except a final volume+high-proximity bump,
     * which is the only day meeting all 4 entry rules, followed by one more "entry execution"
     * day whose open equals the bump's close (used with slippagePct=0.0 in tests as the exact
     * entry price).
     */
    private List<OhlcvCandleEntity> buildEntrySetupCandles() {
        List<OhlcvCandleEntity> candles = buildZigzagUptrendCandles(299, 100.0, 0.5, 0.75, 1_000_000L);
        OhlcvCandleEntity lastZigzag = candles.get(candles.size() - 1);
        OhlcvCandleEntity bump = buildFinalCandle(lastZigzag, 1.005, 2_000_000L);
        candles.add(bump);

        BigDecimal bumpClose = bump.getClosePrice();
        appendCandle(candles, bumpClose, bumpClose.multiply(BigDecimal.valueOf(1.001)),
            bumpClose.multiply(BigDecimal.valueOf(0.999)), bumpClose, 1_000_000L);
        return candles;
    }

    /** The next-day-open entry price used with slippagePct=0.0 (equal to the entry day's open). */
    private BigDecimal entryPrice(List<OhlcvCandleEntity> candlesFromBuildEntrySetup) {
        return candlesFromBuildEntrySetup.get(300).getOpenPrice();
    }

    private void appendFlatCandles(List<OhlcvCandleEntity> candles, int count, BigDecimal price, long volume) {
        for (int i = 0; i < count; i++) {
            appendCandle(candles, price, price, price, price, volume);
        }
    }

    private void appendCandle(List<OhlcvCandleEntity> candles, BigDecimal open, BigDecimal high,
                               BigDecimal low, BigDecimal close, long volume) {
        OhlcvCandleEntity previous = candles.get(candles.size() - 1);
        OhlcvCandleEntity entity = new OhlcvCandleEntity();
        entity.setSymbol(SYMBOL);
        entity.setDate(previous.getDate().plusDays(1));
        setPrices(entity, open, high, low, close);
        entity.setVolume(volume);
        candles.add(entity);
    }

    private List<OhlcvCandleEntity> buildTrendingCandles(int count, double startPrice, double dailyDriftPercent, long volume) {
        List<OhlcvCandleEntity> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < count; i++) {
            double close = price * (1 + dailyDriftPercent / 100.0);
            double open = price;
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;

            OhlcvCandleEntity entity = new OhlcvCandleEntity();
            entity.setSymbol(SYMBOL);
            entity.setDate(date);
            setPrices(entity, BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low), BigDecimal.valueOf(close));
            entity.setVolume(volume);

            candles.add(entity);
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    private List<OhlcvCandleEntity> buildZigzagUptrendCandles(int count, double startPrice,
                                                               double upPercent, double downPercent, long volume) {
        List<OhlcvCandleEntity> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < count; i++) {
            double changePercent = (i % 3 == 2) ? -downPercent : upPercent;
            double open = price;
            double close = price * (1 + changePercent / 100.0);
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;

            OhlcvCandleEntity entity = new OhlcvCandleEntity();
            entity.setSymbol(SYMBOL);
            entity.setDate(date);
            setPrices(entity, BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low), BigDecimal.valueOf(close));
            entity.setVolume(volume);

            candles.add(entity);
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    private OhlcvCandleEntity buildFinalCandle(OhlcvCandleEntity previous, double closeMultiplier, long volume) {
        BigDecimal previousClose = previous.getClosePrice();
        BigDecimal close = previousClose.multiply(BigDecimal.valueOf(closeMultiplier));
        BigDecimal high = close.multiply(BigDecimal.valueOf(1.001));
        BigDecimal low = previousClose.multiply(BigDecimal.valueOf(0.999));

        OhlcvCandleEntity entity = new OhlcvCandleEntity();
        entity.setSymbol(SYMBOL);
        entity.setDate(previous.getDate().plusDays(1));
        setPrices(entity, previousClose, high, low, close);
        entity.setVolume(volume);
        return entity;
    }

    private void setPrices(OhlcvCandleEntity entity, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        entity.setOpenPrice(open);
        entity.setHighPrice(high);
        entity.setLowPrice(low);
        entity.setClosePrice(close);
    }
}
