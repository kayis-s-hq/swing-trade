package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.strategy.ParamSchema;
import com.swingtrade.strategy.ParamSchemaValidator;
import com.swingtrade.strategy.ParamValidationResult;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyParamsView;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Lists the registered {@link SignalStrategy} types with their parameter schema, so clients (the
 * dashboard params editor) can render and validate strategy configs. {@code warmupBars} and
 * {@code requiredIndicators} are computed from the schema's default parameters.
 */
@RestController
@RequestMapping("/api/strategy-types")
public class StrategyTypeController {

    private final StrategyTypeRegistry registry;
    private final ParamSchemaValidator validator;

    public StrategyTypeController(StrategyTypeRegistry registry, ParamSchemaValidator validator) {
        this.registry = registry;
        this.validator = validator;
    }

    /** One strategy type; {@code warmupBars} is null when the defaults do not validate. */
    public record StrategyTypeResponse(String type, ParamSchema paramSchema, Integer warmupBars,
                                       List<String> requiredIndicators) {}

    @GetMapping
    public ResponseEntity<ApiResponse<List<StrategyTypeResponse>>> list() {
        List<StrategyTypeResponse> types = registry.all().stream()
            .sorted(Comparator.comparing(SignalStrategy::type))
            .map(this::describe)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(types));
    }

    private StrategyTypeResponse describe(SignalStrategy strategy) {
        ParamValidationResult defaults = validator.validate(
            strategy.paramSchema(), strategy.crossFieldRules(), java.util.Map.of());
        if (!defaults.valid()) {
            return new StrategyTypeResponse(strategy.type(), strategy.paramSchema(), null, List.of());
        }
        StrategyParamsView params = StrategyParamsView.of(defaults.resolvedParams());
        List<String> indicators = strategy.requiredIndicators(params).stream()
            .map(Enum::name).sorted().toList();
        return new StrategyTypeResponse(strategy.type(), strategy.paramSchema(),
            strategy.warmupBars(params), indicators);
    }
}
