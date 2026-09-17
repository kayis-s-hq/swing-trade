package com.swingtrade.strategy;

import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.domain.SentimentResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Composable overlay layer (plan §5.5) applied on top of any {@link SignalStrategy}'s
 * {@link StrategyDecision}: {@code regimeGate} and {@code sentimentGate} can each independently
 * block a BUY, without being baked into any strategy type's own rule logic. Every gate's outcome
 * is recorded via {@link GateOutcome} - including a pass - so "what gate prevented" a signal is
 * measurable after the fact (plan §5.5 analytics requirement).
 *
 * <p>This class only evaluates gates given already-resolved inputs (an {@link IndexSeries} for
 * {@code regimeGate}, an {@link Optional} {@link SentimentResult} for {@code sentimentGate|}); it
 * does not fetch either itself. Wiring it into the live/backtest orchestrator so it runs
 * automatically for every variant is deferred to the phase that does live shadow execution (plan
 * §7) - this phase only needs a structured, serialisable, unit-testable result shape compatible
 * with the {@code signals.gate_outcomes} JSONB column (see {@link GateOutcome}).
 */
@Component
public class GateEvaluator {

    /**
     * Applies {@code regimeGate} if {@code regimeConfig} is present. A non-BUY decision is passed
     * through unchanged (gates only ever block BUYs).
     */
    public GateResult applyRegimeGate(StrategyDecision decision, LocalDate date, IndexSeries indexSeries,
                                       RegimeGateConfig regimeConfig) {
        if (decision.type() != SignalType.BUY || regimeConfig == null) {
            return GateResult.unblocked(decision, List.of());
        }
        if (indexSeries == null || !indexSeries.hasDataOn(date)) {
            GateOutcome outcome = GateOutcome.pass("regimeGate", "No index data available; gate skipped");
            return GateResult.unblocked(decision, List.of(outcome));
        }

        boolean regimeOk = switch (regimeConfig.mode()) {
            case CLOSE_ABOVE -> indexSeries.close(date).compareTo(indexSeries.ema(regimeConfig.indexEma(), date)) > 0;
            case EMA_SLOPE_UP -> indexSeries.ema(regimeConfig.indexEma(), date).compareTo(
                indexSeries.emaLookback(regimeConfig.indexEma(), date, regimeConfig.slopeLookbackDays())) > 0;
        };

        if (regimeOk) {
            return GateResult.unblocked(decision,
                List.of(GateOutcome.pass("regimeGate", "Index regime is up (" + regimeConfig.mode() + ")")));
        }
        GateOutcome outcome = GateOutcome.block("regimeGate",
            "Index regime is down (" + regimeConfig.mode() + "); BUY blocked");
        return GateResult.blocked(downgrade(decision, outcome.reason()), List.of(outcome));
    }

    /**
     * Applies {@code sentimentGate} if {@code sentimentConfig} is present. A non-BUY decision is
     * passed through unchanged.
     */
    public GateResult applySentimentGate(StrategyDecision decision, LocalDate date,
                                          Optional<SentimentResult> sentiment,
                                          SentimentGateConfig sentimentConfig) {
        if (decision.type() != SignalType.BUY || sentimentConfig == null) {
            return GateResult.unblocked(decision, List.of());
        }
        if (sentiment == null || sentiment.isEmpty()) {
            GateOutcome outcome = GateOutcome.block("sentimentGate", "No sentiment available; BUY blocked");
            return GateResult.blocked(downgrade(decision, outcome.reason()), List.of(outcome));
        }

        SentimentResult result = sentiment.get();
        long ageDays = ChronoUnit.DAYS.between(result.date(), date);
        if (ageDays > sentimentConfig.maxAgeDays()) {
            GateOutcome outcome = GateOutcome.block("sentimentGate",
                "Sentiment is " + ageDays + " days stale (max " + sentimentConfig.maxAgeDays() + "); BUY blocked");
            return GateResult.blocked(downgrade(decision, outcome.reason()), List.of(outcome));
        }

        int rank = SentimentGateConfig.rank(result.score());
        int minRank = SentimentGateConfig.rank(sentimentConfig.minScore());
        if (rank < minRank) {
            GateOutcome outcome = GateOutcome.block("sentimentGate",
                "Sentiment " + result.score() + " below minimum " + sentimentConfig.minScore() + "; BUY blocked");
            return GateResult.blocked(downgrade(decision, outcome.reason()), List.of(outcome));
        }

        return GateResult.unblocked(decision,
            List.of(GateOutcome.pass("sentimentGate", "Sentiment " + result.score() + " meets minimum")));
    }

    /**
     * Applies both overlays in sequence (regime then sentiment), each independently capable of
     * blocking the BUY, and aggregates all gate outcomes for recording.
     */
    public GateResult applyAll(StrategyDecision decision, LocalDate date, IndexSeries indexSeries,
                                RegimeGateConfig regimeConfig, Optional<SentimentResult> sentiment,
                                SentimentGateConfig sentimentConfig) {
        List<GateOutcome> outcomes = new ArrayList<>();
        GateResult afterRegime = applyRegimeGate(decision, date, indexSeries, regimeConfig);
        outcomes.addAll(afterRegime.outcomes());

        GateResult afterSentiment = applySentimentGate(afterRegime.decision(), date, sentiment, sentimentConfig);
        outcomes.addAll(afterSentiment.outcomes());

        return new GateResult(afterSentiment.decision(), List.copyOf(outcomes));
    }

    private StrategyDecision downgrade(StrategyDecision decision, String reason) {
        return new StrategyDecision(SignalType.HOLD, decision.score(), decision.rules(), null, null,
            "Gate-blocked: " + reason);
    }

    /**
     * @param decision the (possibly gate-downgraded to HOLD) decision
     * @param outcomes every gate outcome recorded for this evaluation, pass or block
     */
    public record GateResult(StrategyDecision decision, List<GateOutcome> outcomes) {
        public GateResult {
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }

        public static GateResult unblocked(StrategyDecision decision, List<GateOutcome> outcomes) {
            return new GateResult(decision, outcomes);
        }

        public static GateResult blocked(StrategyDecision decision, List<GateOutcome> outcomes) {
            return new GateResult(decision, outcomes);
        }

        public boolean anyBlocked() {
            return outcomes.stream().anyMatch(GateOutcome::blocked);
        }
    }
}
