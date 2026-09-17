package com.swingtrade.strategy;

import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.domain.SentimentResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GateEvaluatorTest {

    private final GateEvaluator evaluator = new GateEvaluator();
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    private StrategyDecision buyDecision() {
        return new StrategyDecision(SignalType.BUY, BigDecimal.ONE, List.of(), BigDecimal.valueOf(90),
            BigDecimal.valueOf(110), "test buy");
    }

    private StrategyDecision holdDecision() {
        return new StrategyDecision(SignalType.HOLD, BigDecimal.ZERO, List.of(), null, null, "test hold");
    }

    // --- regimeGate ---

    @Test
    void regimeGate_allowsBuy_whenIndexCloseAboveEma() {
        FakeIndexSeries index = FakeIndexSeries.withCloseAboveEma();
        GateEvaluator.GateResult result = evaluator.applyRegimeGate(
            buyDecision(), DATE, index, RegimeGateConfig.defaults());

        assertThat(result.anyBlocked()).isFalse();
        assertThat(result.decision().type()).isEqualTo(SignalType.BUY);
        assertThat(result.outcomes()).hasSize(1);
        assertThat(result.outcomes().get(0).blocked()).isFalse();
    }

    @Test
    void regimeGate_blocksBuy_whenIndexCloseBelowEma() {
        FakeIndexSeries index = FakeIndexSeries.withCloseBelowEma();
        GateEvaluator.GateResult result = evaluator.applyRegimeGate(
            buyDecision(), DATE, index, RegimeGateConfig.defaults());

        assertThat(result.anyBlocked()).isTrue();
        assertThat(result.decision().type()).isEqualTo(SignalType.HOLD);
        assertThat(result.outcomes().get(0).gate()).isEqualTo("regimeGate");
    }

    @Test
    void regimeGate_emaSlopeUpMode_blocksWhenSlopeIsFlatOrDown() {
        FakeIndexSeries index = FakeIndexSeries.withFlatEmaSlope();
        RegimeGateConfig config = new RegimeGateConfig(200, RegimeGateConfig.Mode.EMA_SLOPE_UP, 10);
        GateEvaluator.GateResult result = evaluator.applyRegimeGate(buyDecision(), DATE, index, config);

        assertThat(result.anyBlocked()).isTrue();
    }

    @Test
    void regimeGate_neverBlocksANonBuyDecision() {
        FakeIndexSeries index = FakeIndexSeries.withCloseBelowEma();
        GateEvaluator.GateResult result = evaluator.applyRegimeGate(
            holdDecision(), DATE, index, RegimeGateConfig.defaults());

        assertThat(result.anyBlocked()).isFalse();
        assertThat(result.outcomes()).isEmpty();
    }

    @Test
    void regimeGate_skipsGracefully_whenNoIndexDataAvailable() {
        GateEvaluator.GateResult result = evaluator.applyRegimeGate(
            buyDecision(), DATE, FakeIndexSeries.withNoData(), RegimeGateConfig.defaults());

        assertThat(result.anyBlocked()).isFalse();
        assertThat(result.decision().type()).isEqualTo(SignalType.BUY);
    }

    // --- sentimentGate ---

    @Test
    void sentimentGate_allowsBuy_whenSentimentMeetsMinimum() {
        SentimentResult positive = SentimentResult.create(
            "SYM", DATE, SentimentResult.SentimentScore.POSITIVE, "good", null, 0.8);
        GateEvaluator.GateResult result = evaluator.applySentimentGate(
            buyDecision(), DATE, Optional.of(positive), SentimentGateConfig.defaults());

        assertThat(result.anyBlocked()).isFalse();
        assertThat(result.decision().type()).isEqualTo(SignalType.BUY);
    }

    @Test
    void sentimentGate_blocksBuy_whenSentimentIsNegative() {
        SentimentResult negative = SentimentResult.create(
            "SYM", DATE, SentimentResult.SentimentScore.NEGATIVE, "bad news", null, 0.9);
        GateEvaluator.GateResult result = evaluator.applySentimentGate(
            buyDecision(), DATE, Optional.of(negative), SentimentGateConfig.defaults());

        assertThat(result.anyBlocked()).isTrue();
        assertThat(result.decision().type()).isEqualTo(SignalType.HOLD);
    }

    @Test
    void sentimentGate_blocksBuy_whenSentimentIsStale() {
        SentimentResult stale = SentimentResult.create(
            "SYM", DATE.minusDays(10), SentimentResult.SentimentScore.POSITIVE, "old", null, 0.8);
        GateEvaluator.GateResult result = evaluator.applySentimentGate(
            buyDecision(), DATE, Optional.of(stale), new SentimentGateConfig(
                SentimentResult.SentimentScore.NEUTRAL, 3));

        assertThat(result.anyBlocked()).isTrue();
        assertThat(result.outcomes().get(0).reason()).contains("stale");
    }

    @Test
    void sentimentGate_blocksBuy_whenNoSentimentAvailable() {
        GateEvaluator.GateResult result = evaluator.applySentimentGate(
            buyDecision(), DATE, Optional.empty(), SentimentGateConfig.defaults());

        assertThat(result.anyBlocked()).isTrue();
    }

    @Test
    void sentimentGate_neverBlocksANonBuyDecision() {
        GateEvaluator.GateResult result = evaluator.applySentimentGate(
            holdDecision(), DATE, Optional.empty(), SentimentGateConfig.defaults());

        assertThat(result.anyBlocked()).isFalse();
        assertThat(result.outcomes()).isEmpty();
    }

    // --- combined ---

    @Test
    void applyAll_aggregatesOutcomesFromBothGates() {
        SentimentResult positive = SentimentResult.create(
            "SYM", DATE, SentimentResult.SentimentScore.POSITIVE, "good", null, 0.8);
        GateEvaluator.GateResult result = evaluator.applyAll(
            buyDecision(), DATE, FakeIndexSeries.withCloseAboveEma(), RegimeGateConfig.defaults(),
            Optional.of(positive), SentimentGateConfig.defaults());

        assertThat(result.outcomes()).hasSize(2);
        assertThat(result.anyBlocked()).isFalse();
        assertThat(result.decision().type()).isEqualTo(SignalType.BUY);
    }

    @Test
    void applyAll_blocksIfEitherGateBlocks() {
        GateEvaluator.GateResult result = evaluator.applyAll(
            buyDecision(), DATE, FakeIndexSeries.withCloseAboveEma(), RegimeGateConfig.defaults(),
            Optional.empty(), SentimentGateConfig.defaults());

        assertThat(result.anyBlocked()).isTrue();
        assertThat(result.decision().type()).isEqualTo(SignalType.HOLD);
    }

    /** Simple fake used to unit-test regimeGate without real Nifty ingestion (plan §5.5 judgment call). */
    private static final class FakeIndexSeries implements IndexSeries {
        private final boolean hasData;
        private final BigDecimal close;
        private final BigDecimal ema;
        private final BigDecimal emaLookback;

        private FakeIndexSeries(boolean hasData, BigDecimal close, BigDecimal ema, BigDecimal emaLookback) {
            this.hasData = hasData;
            this.close = close;
            this.ema = ema;
            this.emaLookback = emaLookback;
        }

        static FakeIndexSeries withCloseAboveEma() {
            return new FakeIndexSeries(true, BigDecimal.valueOf(20000), BigDecimal.valueOf(19000), BigDecimal.valueOf(18500));
        }

        static FakeIndexSeries withCloseBelowEma() {
            return new FakeIndexSeries(true, BigDecimal.valueOf(18000), BigDecimal.valueOf(19000), BigDecimal.valueOf(18500));
        }

        static FakeIndexSeries withFlatEmaSlope() {
            return new FakeIndexSeries(true, BigDecimal.valueOf(20000), BigDecimal.valueOf(19000), BigDecimal.valueOf(19000));
        }

        static FakeIndexSeries withNoData() {
            return new FakeIndexSeries(false, null, null, null);
        }

        @Override
        public boolean hasDataOn(LocalDate date) {
            return hasData;
        }

        @Override
        public BigDecimal close(LocalDate date) {
            return close;
        }

        @Override
        public BigDecimal ema(int period, LocalDate date) {
            return ema;
        }

        @Override
        public BigDecimal emaLookback(int period, LocalDate date, int lookbackDays) {
            return emaLookback;
        }
    }
}
