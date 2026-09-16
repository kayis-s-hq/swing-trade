package com.swingtrade.api.controller;

import com.swingtrade.api.dto.strategy.CloneRequest;
import com.swingtrade.api.dto.strategy.CreateVariantRequest;
import com.swingtrade.api.dto.strategy.CreateVersionRequest;
import com.swingtrade.api.dto.strategy.ModeChangeRequest;
import com.swingtrade.api.dto.strategy.ParamDefResponse;
import com.swingtrade.api.dto.strategy.StrategyTypeResponse;
import com.swingtrade.api.dto.strategy.StrategyVariantResponse;
import com.swingtrade.api.dto.strategy.ValidateParamsRequest;
import com.swingtrade.api.dto.strategy.ValidateParamsResponse;
import com.swingtrade.api.service.StrategyConfigService;
import com.swingtrade.domain.StrategyConfig;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for the configurable multi-strategy framework's config model (plan §4.4). Mode/param
 * changes here take effect at the next job run - this controller/service does not itself
 * trigger signal generation, backtests, or paper trading.
 */
@RestController
@RequestMapping("/api")
public class StrategyConfigController {

    private final StrategyConfigService service;

    public StrategyConfigController(StrategyConfigService service) {
        this.service = service;
    }

    @GetMapping("/strategy-types")
    public List<StrategyTypeResponse> listTypes() {
        return service.listTypes().stream()
            .map(s -> new StrategyTypeResponse(
                s.type(),
                s.paramSchema().params().stream().map(ParamDefResponse::from).toList()))
            .toList();
    }

    @GetMapping("/strategies")
    public List<StrategyVariantResponse> listCurrent() {
        return service.listCurrent().stream().map(StrategyVariantResponse::from).toList();
    }

    @GetMapping("/strategies/{variantId}/versions")
    public List<StrategyVariantResponse> listVersions(@PathVariable String variantId) {
        return service.listVersions(variantId).stream().map(StrategyVariantResponse::from).toList();
    }

    @PostMapping("/strategies")
    public StrategyVariantResponse createVariant(@Valid @RequestBody CreateVariantRequest request) {
        StrategyConfig created = service.createVariant(
            request.variantId(), request.strategyType(), request.params(), request.overlays(),
            request.paperCapital(), request.notes());
        return StrategyVariantResponse.from(created);
    }

    @PostMapping("/strategies/{variantId}/versions")
    public StrategyVariantResponse createVersion(
        @PathVariable String variantId, @RequestBody CreateVersionRequest request
    ) {
        StrategyConfig created = service.createVersion(
            variantId, request.params(), request.overlays(), request.paperCapital(),
            request.portfolioAction(), request.notes());
        return StrategyVariantResponse.from(created);
    }

    @PostMapping("/strategies/{variantId}/clone")
    public StrategyVariantResponse clone(@PathVariable String variantId, @Valid @RequestBody CloneRequest request) {
        StrategyConfig cloned = service.clone(variantId, request.newVariantId(), request.notes());
        return StrategyVariantResponse.from(cloned);
    }

    @PutMapping("/strategies/{variantId}/mode")
    public StrategyVariantResponse changeMode(@PathVariable String variantId, @Valid @RequestBody ModeChangeRequest request) {
        StrategyConfig updated = service.changeMode(variantId, request.mode(), request.confirm());
        return StrategyVariantResponse.from(updated);
    }

    @PostMapping("/strategies/validate")
    public ValidateParamsResponse validate(@Valid @RequestBody ValidateParamsRequest request) {
        return ValidateParamsResponse.from(service.validateParams(request.strategyType(), request.params()));
    }
}
