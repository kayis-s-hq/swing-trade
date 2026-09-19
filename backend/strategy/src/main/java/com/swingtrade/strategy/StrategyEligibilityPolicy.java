package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RelativeStrengthAssessment;
import com.swingtrade.domain.policy.RelativeStrengthPolicy;

import java.util.List;
import java.util.Objects;

/**
 * Entry gate for strategy signals that requires relative strength against the
 * configured market index. The assessment is supplied to the strategy layer so
 * this policy does not fetch market data or invent an index default.
 */
public final class StrategyEligibilityPolicy {

    private final RelativeStrengthPolicy relativeStrengthPolicy;

    public StrategyEligibilityPolicy() {
        this(new BoundedRelativeStrengthPolicy());
    }

    public StrategyEligibilityPolicy(RelativeStrengthPolicy relativeStrengthPolicy) {
        this.relativeStrengthPolicy = Objects.requireNonNull(relativeStrengthPolicy,
            "Relative-strength policy is required");
    }

    /** Assesses relative strength; missing index data is handled by the policy. */
    public RelativeStrengthAssessment assessRelativeStrength(List<OhlcvCandle> stockCandles,
                                                              List<OhlcvCandle> indexCandles) {
        return relativeStrengthPolicy.assess(stockCandles, indexCandles);
    }

    /**
     * Applies technical entry rules and the relative-strength assessment.
     * A null or unavailable assessment fails closed.
     */
    public boolean isEntryEligible(TradingStrategy strategy, Indicators indicators,
                                   RelativeStrengthAssessment relativeStrength) {
        return strategy != null
            && relativeStrength != null
            && relativeStrength.eligible()
            && strategy.isEntrySignal(indicators);
    }
}
