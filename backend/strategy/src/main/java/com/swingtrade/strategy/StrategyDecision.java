package com.swingtrade.strategy;

import com.swingtrade.domain.Signal.SignalType;

import java.math.BigDecimal;
import java.util.List;

/**
 * The outcome of {@link SignalStrategy#evaluateEntry}: a scored, itemised entry decision for a
 * single bar. See plan §3.1/§3.2.
 *
 * @param type            BUY or HOLD (entry evaluation never itself produces SELL - exits are
 *                         {@link ExitDecision})
 * @param score            {@code Σ(weight·passed)/Σweight} across {@link #rules()}, in [0,1]
 * @param rules            the individual rule outcomes that produced {@link #score()}
 * @param suggestedStop    ATR-based stop suggested for this entry, or {@code null} when not BUY
 * @param suggestedTarget  reward:risk based target suggested for this entry, or {@code null}
 *                         when not BUY
 * @param reasoning        human-readable summary of the decision
 */
public record StrategyDecision(
    SignalType type,
    BigDecimal score,
    List<RuleOutcome> rules,
    BigDecimal suggestedStop,
    BigDecimal suggestedTarget,
    String reasoning
) {
    public StrategyDecision {
        if (type == null) {
            throw new IllegalArgumentException("Decision type cannot be null");
        }
        if (score == null || score.signum() < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Score must be in [0,1]: " + score);
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
