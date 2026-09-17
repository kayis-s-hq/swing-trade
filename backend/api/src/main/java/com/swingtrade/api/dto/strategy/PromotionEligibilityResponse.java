package com.swingtrade.api.dto.strategy;

import com.swingtrade.strategy.PromotionEligibilityChecker.ConditionResult;
import com.swingtrade.strategy.PromotionEligibilityChecker.PromotionEligibilityResult;

/**
 * {@code GET /api/strategies/{variantId}/promotion-eligibility} response (plan §7.4/§8):
 * mirrors {@link PromotionEligibilityResult} 1:1, plus the champion variantId compared against
 * and the tenure figure used for condition 1, so the UI has everything it needs without a second
 * call.
 */
public record PromotionEligibilityResponse(
    String challengerVariantId,
    String championVariantId,
    long tenureCalendarDays,
    String status,
    Condition tenureAndSampleSize,
    Condition expectancyVsChampion,
    Condition drawdownGuard,
    Condition walkForwardAndOverfitting
) {
    public static PromotionEligibilityResponse from(
        String challengerVariantId, String championVariantId, long tenureCalendarDays,
        PromotionEligibilityResult result
    ) {
        return new PromotionEligibilityResponse(
            challengerVariantId,
            championVariantId,
            tenureCalendarDays,
            result.status().name(),
            Condition.from(result.tenureAndSampleSize()),
            Condition.from(result.expectancyVsChampion()),
            Condition.from(result.drawdownGuard()),
            Condition.from(result.walkForwardAndOverfitting())
        );
    }

    public record Condition(boolean met, String actualValue, String threshold) {
        static Condition from(ConditionResult r) {
            return new Condition(r.met(), r.actualValue(), r.threshold());
        }
    }
}
