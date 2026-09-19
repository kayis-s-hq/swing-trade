package com.swingtrade.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, versioned configuration for a strategy variant. */
public record StrategyConfig(
    Long id,
    String variantId,
    int version,
    String strategyType,
    Map<String, Object> params,
    Map<String, Object> overlays,
    String paramsHash,
    Mode mode,
    BigDecimal paperCapital,
    boolean current,
    String notes,
    LocalDateTime createdAt
) {
    public enum Mode { OFF, BACKTEST_ONLY, SHADOW, CHAMPION }

    public StrategyConfig {
        if (variantId == null || variantId.isBlank() || variantId.length() > 40) {
            throw new IllegalArgumentException("Variant id must contain 1-40 characters");
        }
        if (version < 1) throw new IllegalArgumentException("Version must be positive");
        if (strategyType == null || strategyType.isBlank() || strategyType.length() > 30) {
            throw new IllegalArgumentException("Strategy type must contain 1-30 characters");
        }
        params = immutableMap(params, "Params");
        overlays = immutableMap(overlays, "Overlays");
        if (paramsHash == null || !paramsHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Params hash must be a SHA-256 hex digest");
        }
        Objects.requireNonNull(mode, "Mode is required");
        if (paperCapital == null || paperCapital.signum() < 0) {
            throw new IllegalArgumentException("Paper capital must be non-negative");
        }
        if (createdAt == null) throw new IllegalArgumentException("Created at is required");
    }

    public static StrategyConfig create(String variantId, int version, String strategyType,
                                        Map<String, Object> params, Map<String, Object> overlays,
                                        Mode mode, BigDecimal paperCapital, boolean current,
                                        String notes, LocalDateTime createdAt) {
        return new StrategyConfig(null, variantId, version, strategyType, params, overlays,
            hash(strategyType, params, overlays), mode, paperCapital, current, notes, createdAt);
    }

    public static String hash(String strategyType, Map<String, Object> params,
                              Map<String, Object> overlays) {
        String canonical = canonical(strategyType) + canonical(params) + canonical(overlays);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " are required");
        return Collections.unmodifiableMap(new TreeMap<>(value));
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .sorted((a, b) -> String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey())))
                .map(e -> canonical(String.valueOf(e.getKey())) + canonical(e.getValue()))
                .reduce("{}", (a, b) -> a + b);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(StrategyConfig::canonical).reduce("[]", (a, b) -> a + b);
        }
        return value == null ? "null" : value.getClass().getName() + ":" + value;
    }
}
