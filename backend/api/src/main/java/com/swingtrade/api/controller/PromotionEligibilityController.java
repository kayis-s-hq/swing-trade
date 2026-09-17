package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.api.dto.PromotionEligibilityResponse;
import com.swingtrade.api.service.PromotionEligibilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Champion/challenger promotion-eligibility endpoint (plan &sect;7.4).
 *
 * <p>See {@link PromotionEligibilityService} for the documented data-model gap: closed-trade
 * P&amp;L is not yet attributable per strategy variant on main, so the expectancy/drawdown/
 * walk-forward inputs are currently degraded. The response's {@code dataLimitations} field
 * always documents this rather than silently returning zeros.</p>
 */
@RestController
@RequestMapping("/api/strategy-configs")
public class PromotionEligibilityController {
    private final PromotionEligibilityService service;

    public PromotionEligibilityController(PromotionEligibilityService service) {
        this.service = service;
    }

    @GetMapping("/{variantId}/promotion-eligibility")
    public ResponseEntity<ApiResponse<PromotionEligibilityResponse>> check(@PathVariable String variantId) {
        return ResponseEntity.ok(ApiResponse.ok(service.check(variantId)));
    }
}
