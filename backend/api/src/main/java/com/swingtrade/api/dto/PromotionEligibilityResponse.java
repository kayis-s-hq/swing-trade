package com.swingtrade.api.dto;

import com.swingtrade.strategy.PromotionEligibilityChecker;

import java.util.List;

/**
 * Response for {@code GET /api/strategy-configs/{variantId}/promotion-eligibility}.
 *
 * <p>{@code dataLimitations} documents which inputs main's current data model cannot yet supply
 * per-variant (see the endpoint's class-level Javadoc): closed-trade P&amp;L is not attributable
 * to a specific strategy variant today (positions carry no variant/config linkage), and no
 * walk-forward run persistence exists yet. Those fields degrade to empty/absent rather than
 * silently reporting zeros that would look like real "no edge" measurements.</p>
 */
public record PromotionEligibilityResponse(
    String challengerVariantId,
    String championVariantId,
    PromotionEligibilityChecker.Status status,
    List<PromotionEligibilityChecker.ConditionResult> conditions,
    List<String> notes,
    List<String> dataLimitations
) {
}
