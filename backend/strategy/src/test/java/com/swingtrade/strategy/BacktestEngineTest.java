package com.swingtrade.strategy;

import tools.jackson.databind.ObjectMapper;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.WatchlistStore;
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
    private CandleStore candleStore;

    @Mock
    private WatchlistStore watchlistStore;

    private BacktestEngine engine;

    @BeforeEach
    void setUp() {
        PriceActionStrategy priceActionStrategy = new PriceActionStrategy();
            PriceActionSignalEngine priceActionSignalEngine = new PriceActionSignalEngine(candleStore, org.mockito.Mockito.mock(com.swingtrade.core.metrics.SignalMetrics.class), priceActionStrategy);
            StrategyRegistry strategyRegistry = new StrategyRegistry(java.util.List.of(priceActionStrategy), priceActionStrategy);
        engine = new BacktestEngine(candleStore, watchlistStore, priceActionSignalEngine, strategyRegistry,
            new ObjectMapper(), "target/test-reports");
    }

    // -----------------------------------------------------------------------
    // Test fixtures
    // -----------------------------------------------------------------------

    private void stub(List<OhlcvCandle> chronologicalCandles) {
        List<OhlcvCandle> descending = new ArrayList<>(chronologicalCandles);
        Collections.reverse(descending);
        when(candleStore.findTopBySymbolOrderByDateDesc(SYMBOL, 1000)).thenReturn(descending);
    }

    /**
     * Builds a 301-candle chronological series: a 2-up/1-down zigzag uptrend (keeps RSI in the
     * 50-65 band) whose volume is constant everywhere except a final volume+high-proximity bump,
     * which is the only day meeting all 4 entry rules, followed by one more "entry execution"
     * day whose open equals the bump's close (used with slippagePct=0.0 in tests as the exact
     * entry price).
     */
    private List<OhlcvCandle> buildEntrySetupCandles() {
        List<OhlcvCandle> candles = buildZigzagUptrendCandles(299, 100.0, 0.5, 0.75, 1_000_000L);
        OhlcvCandle lastZigzag = candles.get(candles.size() - 1);
        OhlcvCandle bump = buildFinalCandle(lastZigzag, 1.005, 2_000_000L);
        candles.add(bump);

        BigDecimal bumpClose = bump.close();
        appendCandle(candles, bumpClose, bumpClose.multiply(BigDecimal.valueOf(1.001)),
            bumpClose.multiply(BigDecimal.valueOf(0.999)), bumpClose, 1_000_000L);
        return candles;
    }

    /** The next-day-open entry price used with slippagePct=0.0 (equal to the entry day's open). */
    private BigDecimal entryPrice(List<OhlcvCandle> candlesFromBuildEntrySetup) {
        return candlesFromBuildEntrySetup.get(300).open();
    }

    private void appendFlatCandles(List<OhlcvCandle> candles, int count, BigDecimal price, long volume) {
        for (int i = 0; i < count; i++) {
            appendCandle(candles, price, price, price, price, volume);
        }
    }

    private void appendCandle(List<OhlcvCandle> candles, BigDecimal open, BigDecimal high,
                               BigDecimal low, BigDecimal close, long volume) {
        OhlcvCandle previous = candles.get(candles.size() - 1);
        OhlcvCandle candle = OhlcvCandle.of(SYMBOL, previous.date().plusDays(1), open, high, low, close, volume);
        candles.add(candle);
    }

    private List<OhlcvCandle> buildTrendingCandles(int count, double startPrice, double dailyDriftPercent, long volume) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < count; i++) {
            double close = price * (1 + dailyDriftPercent / 100.0);
            double open = price;
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;

            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, date,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), volume);

            candles.add(candle);
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    private List<OhlcvCandle> buildZigzagUptrendCandles(int count, double startPrice,
                                                               double upPercent, double downPercent, long volume) {
        return buildZigzagUptrendCandles(count, startPrice, upPercent, downPercent, volume, LocalDate.of(2024, 1, 1));
    }

    /**
     * Overload accepting an explicit start date, so callers that chain multiple zigzag blocks
     * into one chronological series (e.g. one per backtest cycle) can keep dates strictly
     * increasing across blocks instead of every block restarting at the same hardcoded date
     * (which ta4j's {@code BaseBarSeries} rejects as an out-of-order bar).
     */
    private List<OhlcvCandle> buildZigzagUptrendCandles(int count, double startPrice,
                                                               double upPercent, double downPercent, long volume,
                                                               LocalDate startDate) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = startDate;
        for (int i = 0; i < count; i++) {
            double changePercent = (i % 3 == 2) ? -downPercent : upPercent;
            double open = price;
            double close = price * (1 + changePercent / 100.0);
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;

            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, date,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), volume);

            candles.add(candle);
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    private OhlcvCandle buildFinalCandle(OhlcvCandle previous, double closeMultiplier, long volume) {
        BigDecimal previousClose = previous.close();
        BigDecimal close = previousClose.multiply(BigDecimal.valueOf(closeMultiplier));
        BigDecimal high = close.multiply(BigDecimal.valueOf(1.001));
        BigDecimal low = previousClose.multiply(BigDecimal.valueOf(0.999));

        OhlcvCandle candle = OhlcvCandle.of(SYMBOL, previous.date().plusDays(1),
            previousClose, high, low, close, volume);
        return candle;
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
            List<OhlcvCandle> tooFew = buildTrendingCandles(30, 100.0, 0.0, 1_000_000L);
            stub(tooFew);

            assertThatThrownBy(() -> engine.runBacktest(SYMBOL, EXCHANGE, BacktestConfig.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient candle history");
        }

        @Test
        @DisplayName("windowed backtest keeps warm-up history but only evaluates the requested dates")
        void runBacktestWindow_usesEvaluationBoundary() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            LocalDate evaluationStart = candles.get(240).date();
            LocalDate evaluationEnd = candles.get(candles.size() - 1).date();
            List<OhlcvCandle> descending = new ArrayList<>(candles);
            Collections.reverse(descending);
            when(candleStore.findBySymbolAndDateRange(SYMBOL, evaluationStart.minusDays(400), evaluationEnd))
                .thenReturn(descending);

            BacktestResult result = engine.runBacktestWindow(SYMBOL, EXCHANGE, BacktestConfig.defaults(),
                evaluationStart, evaluationEnd);

            assertThat(result.trades()).isNotEmpty();
            assertThat(result.trades()).allMatch(trade -> !trade.entryDate().isBefore(evaluationStart));
        }
    }

    @Nested
    class EntryAndExit {

        @Test
        @DisplayName("all entry rules satisfied -> a trade opens on the next day's open")
        void entrySignal_opensTradeAtNextOpen() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            appendFlatCandles(candles, 6, entryPrice(candles), 1_000_000L);
            stub(candles);

            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 5, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).isNotEmpty();
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.entryPrice().doubleValue()).isCloseTo(entryPrice(candles).doubleValue(), within(0.0001));
            assertThat(trade.quantity()).isPositive();
        }

        @Test
        @DisplayName("price crashes through the ATR stop -> STOP_LOSS exit at the stop price")
        void stopLossExit() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            appendCandle(candles, entry, entry, entry.multiply(BigDecimal.valueOf(0.75)), entry.multiply(BigDecimal.valueOf(0.80)), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.STOP_LOSS);
            assertThat(trade.exitPrice()).isEqualByComparingTo(trade.stopLoss());
            assertThat(trade.pnl()).isNegative();
        }

        @Test
        @DisplayName("stop gap exits at the opening price with adverse slippage")
        void stopLossGapUsesOpeningPriceAndSlippage() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            BigDecimal gapOpen = entry.multiply(BigDecimal.valueOf(0.75));
            appendCandle(candles, gapOpen, entry.multiply(BigDecimal.valueOf(0.80)),
                entry.multiply(BigDecimal.valueOf(0.70)), gapOpen, 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.01, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20, false, 2);
            BacktestTrade trade = engine.runBacktest(SYMBOL, EXCHANGE, config).trades().get(0);

            assertThat(trade.exitReason()).isEqualTo(ExitReason.STOP_LOSS);
            assertThat(trade.exitPrice()).isEqualByComparingTo(gapOpen.multiply(BigDecimal.valueOf(0.99)));
        }

        @Test
        @DisplayName("price rallies through the target -> TARGET_HIT exit at the target price")
        void targetHitExit() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            appendCandle(candles, entry, entry.multiply(BigDecimal.valueOf(1.60)), entry, entry.multiply(BigDecimal.valueOf(1.50)), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.TARGET_HIT);
            assertThat(trade.exitPrice()).isEqualByComparingTo(trade.target());
            assertThat(trade.pnl()).isPositive();
        }

        @Test
        @DisplayName("target gap exits at the opening price with adverse slippage")
        void targetGapUsesOpeningPriceAndSlippage() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            BigDecimal gapOpen = entry.multiply(BigDecimal.valueOf(1.60));
            appendCandle(candles, gapOpen, gapOpen.multiply(BigDecimal.valueOf(1.01)),
                entry, gapOpen, 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.01, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20, false, 2);
            BacktestTrade trade = engine.runBacktest(SYMBOL, EXCHANGE, config).trades().get(0);

            assertThat(trade.exitReason()).isEqualTo(ExitReason.TARGET_HIT);
            assertThat(trade.exitPrice()).isEqualByComparingTo(gapOpen.multiply(BigDecimal.valueOf(0.99)));
        }

        @Test
        @DisplayName("position held flat past maxHoldingDays -> TIME_STOP exit at close")
        void timeStopExit() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            appendFlatCandles(candles, 6, entryPrice(candles), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 5, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.TIME_STOP);
            assertThat(trade.holdingDays()).isEqualTo(5);
        }

        @Test
        @DisplayName("close stays below EMA20 for 2 consecutive days -> TREND_BREAK exit")
        void trendBreakExit() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            BigDecimal day1Close = entry.multiply(BigDecimal.valueOf(0.95));
            appendCandle(candles, entry, entry.multiply(BigDecimal.valueOf(1.001)), entry.multiply(BigDecimal.valueOf(0.94)), day1Close, 1_000_000L);
            BigDecimal day2Close = day1Close.multiply(BigDecimal.valueOf(0.95));
            appendCandle(candles, day1Close, day1Close.multiply(BigDecimal.valueOf(1.001)), day2Close.multiply(BigDecimal.valueOf(0.99)), day2Close, 1_000_000L);

            stub(candles);
            // Huge ATR multiplier pushes the stop far below entry so the down-days can never
            // trip STOP_LOSS first; this isolates the trend-break rule.
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 100.0, 2.5, 20, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.TREND_BREAK);
            assertThat(trade.holdingDays()).isEqualTo(2);
        }

        @Test
        @DisplayName("no second position opens while one is already open for the symbol")
        void onlyOnePositionAtATime() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            // Repeat high-volume, at-the-high conditions while flat in price: these would
            // qualify as a fresh entry signal if a position weren't already open.
            appendFlatCandles(candles, 3, entry, 2_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 3, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            assertThat(result.trades().get(0).exitReason()).isEqualTo(ExitReason.TIME_STOP);
        }

        @Test
        @DisplayName("position size follows capital * riskPerTradePct / (entry - stopLoss)")
        void positionSizingMatchesRiskFormula() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            appendFlatCandles(candles, 3, entryPrice(candles), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 3, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            BacktestTrade trade = result.trades().get(0);
            BigDecimal riskPerShare = trade.entryPrice().subtract(trade.stopLoss());
            int expectedQuantity = (int) Math.floor((config.initialCapital() * config.riskPerTradePct()) / riskPerShare.doubleValue());

            assertThat(trade.quantity()).isEqualTo(expectedQuantity);
        }
    }

    @Nested
    @DisplayName("Config parameter tests")
    class ConfigParameters {

        @Test
        @DisplayName("slippageAffectsEntryPrice — entryPrice = nextOpen × (1 + slippagePct)")
        void slippageAffectsEntryPrice() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            appendFlatCandles(candles, 6, entryPrice(candles), 1_000_000L);
            stub(candles);

            BacktestConfig config = new BacktestConfig(0.005, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 5, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).isNotEmpty();
            BacktestTrade trade = result.trades().get(0);
            BigDecimal expectedEntry = entryPrice(candles).multiply(BigDecimal.valueOf(1.005));
            assertThat(trade.entryPrice()).isEqualByComparingTo(expectedEntry);
        }

        @Test
        @DisplayName("deliveryCostsReduceNetPnl — netPnl includes brokerage and statutory charges")
        void brokerageReducesNetPnl() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            // Add a candle that hits the target so we get a winning trade
            appendCandle(candles, entry, entry.multiply(BigDecimal.valueOf(1.60)), entry, entry.multiply(BigDecimal.valueOf(1.50)), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 20.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            // Gross PnL = (exitPrice - entryPrice) × quantity
            BigDecimal grossPnl = trade.exitPrice().subtract(trade.entryPrice()).multiply(BigDecimal.valueOf(trade.quantity()));
            BigDecimal costs = new ZerodhaDeliveryCostModel().roundTripCost(
                trade.entryPrice(), trade.exitPrice(), trade.quantity(), BigDecimal.valueOf(20.0));
            double expectedPnl = grossPnl.doubleValue() - costs.doubleValue();
            assertThat(trade.pnl()).isCloseTo(expectedPnl, within(0.01));
        }

        @Test
        @DisplayName("forcedCloseAtLastBar — position open at last candle exits at close price")
        void forcedCloseAtLastBar() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            // Add exactly 1 flat candle after entry so entry is on second-to-last bar
            appendFlatCandles(candles, 1, entry, 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            // The forced close uses the last candle's close price
            OhlcvCandle lastCandle = candles.get(candles.size() - 1);
            assertThat(trade.exitPrice()).isEqualByComparingTo(lastCandle.close());
        }

        @Test
        @DisplayName("metrics_include_sharpe_drawdown_expectancy — all metrics non-zero when trades exist")
        void metricsIncludeSharpeDrawdownExpectancy() {
            // Build candles that produce 3+ trades with mixed wins/losses
            List<OhlcvCandle> candles = new ArrayList<>();
            LocalDate nextCycleStart = LocalDate.of(2024, 1, 1);
            // Create 3 entry setups spread across the candle series
            for (int cycle = 0; cycle < 3; cycle++) {
                // Zigzag uptrend to build indicators. Each cycle's block must start strictly
                // after the previous cycle's last date — buildZigzagUptrendCandles otherwise
                // always restarts at a fixed date, which would produce out-of-order bars once
                // concatenated into a single chronological series.
                candles.addAll(buildZigzagUptrendCandles(60, 100.0 + cycle * 10, 0.5, 0.75, 1_000_000L, nextCycleStart));
                // Volume bump to trigger entry
                OhlcvCandle last = candles.get(candles.size() - 1);
                BigDecimal bumpClose = last.close().multiply(BigDecimal.valueOf(1.005));
                candles.add(OhlcvCandle.of(SYMBOL, last.date().plusDays(1),
                    last.close(), bumpClose.multiply(BigDecimal.valueOf(1.001)),
                    bumpClose.multiply(BigDecimal.valueOf(0.999)), bumpClose, 2_000_000L));
                // Entry execution day
                OhlcvCandle entryDay = candles.get(candles.size() - 1);
                BigDecimal entryPrice = entryDay.close();
                candles.add(OhlcvCandle.of(SYMBOL, entryDay.date().plusDays(1),
                    entryPrice, entryPrice.multiply(BigDecimal.valueOf(1.001)),
                    entryPrice.multiply(BigDecimal.valueOf(0.999)), entryPrice, 1_000_000L));
                // Add exit: alternate between target hit and stop loss
                if (cycle % 2 == 0) {
                    // Winning trade: target hit
                    BigDecimal winPrice = entryPrice.multiply(BigDecimal.valueOf(1.05));
                    candles.add(OhlcvCandle.of(SYMBOL, entryDay.date().plusDays(2),
                        entryPrice, winPrice, entryPrice, winPrice, 1_000_000L));
                } else {
                    // Losing trade: crash through stop loss
                    BigDecimal lossPrice = entryPrice.multiply(BigDecimal.valueOf(0.90));
                    candles.add(OhlcvCandle.of(SYMBOL, entryDay.date().plusDays(2),
                        entryPrice, entryPrice, lossPrice, lossPrice, 1_000_000L));
                }
                // Flat period between cycles
                OhlcvCandle prev = candles.get(candles.size() - 1);
                candles.add(OhlcvCandle.of(SYMBOL, prev.date().plusDays(1),
                    prev.close(), prev.close(), prev.close(), prev.close(), 1_000_000L));

                nextCycleStart = candles.get(candles.size() - 1).date().plusDays(1);
            }

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 20, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.totalTrades()).isGreaterThan(2);
            assertThat(result.sharpeRatio()).isGreaterThan(0.0);
            assertThat(result.maxDrawdownPct()).isGreaterThan(0.0);
            assertThat(result.expectancy()).isNotEqualTo(0.0);
        }

        @Test
        @DisplayName("positionSizing_respects_maxHoldingDays — position closes at exactly maxHoldingDays bars")
        void positionSizingRespectsMaxHoldingDays() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            appendFlatCandles(candles, 10, entryPrice(candles), 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 2.0, 2.5, 3, false, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).isNotEmpty();
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.holdingDays()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("SignalDrivenExit")
    class SignalDrivenExit {

        @Test
        @DisplayName("close drops below EMA20 the day after entry -> SIGNAL_EXIT exits next day at close")
        void signalExit_closeBelowEma20_exitsNextDayAtClose() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            OhlcvCandle lastCandle = candles.get(candles.size() - 1);
            BigDecimal drop = lastCandle.close().multiply(BigDecimal.valueOf(0.97));
            appendCandle(candles, lastCandle.close(), lastCandle.close().multiply(BigDecimal.valueOf(1.001)),
                drop.multiply(BigDecimal.valueOf(0.999)), drop, 1_000_000L);

            stub(candles);
            // Huge atrMultiplierStop/rewardRiskRatio so STOP_LOSS/TARGET_HIT can never preempt —
            // isolates the signal-driven exit rule, same technique as trendBreakExit.
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 100.0, 100.0, 20, true, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.SIGNAL_EXIT);
            assertThat(trade.holdingDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("RSI drifts below 50 after entry -> SIGNAL_EXIT exits at close")
        void signalExit_rsiDropsBelowFifty_exitsAtClose() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            // Mild continual negative drift after entry eases RSI below 50 without an abrupt
            // single-day crash.
            BigDecimal price = candles.get(candles.size() - 1).close();
            for (int i = 0; i < 10; i++) {
                price = price.multiply(BigDecimal.valueOf(0.985));
                appendCandle(candles, price, price.multiply(BigDecimal.valueOf(1.001)),
                    price.multiply(BigDecimal.valueOf(0.999)), price, 1_000_000L);
            }

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 100.0, 100.0, 20, true, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).isNotEmpty();
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.SIGNAL_EXIT);
        }

        @Test
        @DisplayName("signal-driven exit takes priority over the 2-day TREND_BREAK streak rule when enabled")
        void signalExit_takesPriorityOverTrendBreak_whenEnabled() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            BigDecimal entry = entryPrice(candles);
            BigDecimal day1Close = entry.multiply(BigDecimal.valueOf(0.95));
            appendCandle(candles, entry, entry.multiply(BigDecimal.valueOf(1.001)), entry.multiply(BigDecimal.valueOf(0.94)), day1Close, 1_000_000L);
            BigDecimal day2Close = day1Close.multiply(BigDecimal.valueOf(0.95));
            appendCandle(candles, day1Close, day1Close.multiply(BigDecimal.valueOf(1.001)), day2Close.multiply(BigDecimal.valueOf(0.99)), day2Close, 1_000_000L);

            stub(candles);
            BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 100.0, 2.5, 20, true, 2);
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isEqualTo(ExitReason.SIGNAL_EXIT);
            assertThat(trade.holdingDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("disabled by default -> existing exit behavior is unchanged")
        void signalExit_disabledByDefault_doesNotChangeExistingBehavior() {
            List<OhlcvCandle> candles = buildEntrySetupCandles();
            OhlcvCandle lastCandle = candles.get(candles.size() - 1);
            BigDecimal drop = lastCandle.close().multiply(BigDecimal.valueOf(0.97));
            appendCandle(candles, lastCandle.close(), lastCandle.close().multiply(BigDecimal.valueOf(1.001)),
                drop.multiply(BigDecimal.valueOf(0.999)), drop, 1_000_000L);

            stub(candles);
            BacktestConfig config = BacktestConfig.defaults();
            BacktestResult result = engine.runBacktest(SYMBOL, EXCHANGE, config);

            assertThat(result.trades()).hasSize(1);
            BacktestTrade trade = result.trades().get(0);
            assertThat(trade.exitReason()).isNotEqualTo(ExitReason.SIGNAL_EXIT);
        }
    }

}
