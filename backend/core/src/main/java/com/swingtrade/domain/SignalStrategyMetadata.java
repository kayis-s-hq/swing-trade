package com.swingtrade.domain;

/** Immutable strategy provenance attached to a persisted signal. */
public record SignalStrategyMetadata(String strategy, Integer version) {
    public SignalStrategyMetadata {
        strategy = strategy == null || strategy.isBlank() ? "DEFAULT" : strategy;
        version = version == null || version < 1 ? 1 : version;
    }
}
