package com.swingtrade.api.dto;

import java.util.List;

/**
 * Response for /signals/generate-all showing generated signals and skipped symbols.
 */
public record GenerateAllResponse(
    List<SignalResponse> signals,
    List<SymbolResult> skipped
) {
    public record SymbolResult(
        String symbol,
        String reason
    ) {}

    public static GenerateAllResponse of(List<SignalResponse> signals, List<SymbolResult> skipped) {
        return new GenerateAllResponse(signals, skipped);
    }
}