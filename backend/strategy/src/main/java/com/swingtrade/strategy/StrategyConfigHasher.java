package com.swingtrade.strategy;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Computes the {@code params_hash} column of {@code strategy_config} (plan §4.1/§4.3): a sha256
 * of the canonical JSON of {@code (strategyType, params, overlays)}, used to dedupe a newly
 * created version whose resolved params are byte-for-byte identical to an existing version of
 * the same variant.
 *
 * <p>Canonical here means: keys sorted lexicographically at every map level (via {@link TreeMap})
 * before serialization, so the same logical param set always hashes the same regardless of
 * request body key order.
 */
@Component
public class StrategyConfigHasher {

    private final ObjectMapper objectMapper;

    public StrategyConfigHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(String strategyType, Map<String, Object> params, Map<String, Object> overlays) {
        SortedMap<String, Object> canonical = new TreeMap<>();
        canonical.put("strategyType", strategyType);
        canonical.put("params", canonicalize(params));
        canonical.put("overlays", canonicalize(overlays));
        String json = objectMapper.writeValueAsString(canonical);
        return sha256Hex(json);
    }

    private SortedMap<String, Object> canonicalize(Map<String, Object> map) {
        SortedMap<String, Object> sorted = new TreeMap<>();
        if (map != null) {
            sorted.putAll(map);
        }
        return sorted;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
