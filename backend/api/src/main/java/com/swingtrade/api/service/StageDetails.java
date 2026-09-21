package com.swingtrade.api.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and reads the structured {@code details} JSON stored on a job-run stage. Shape (all
 * keys optional): {@code source} ("LLM" or "KEYWORD_FALLBACK"), {@code reason} (a reason code),
 * {@code warnings} (list of strings) and {@code strategies} (per-variant outcomes, SIGNAL stage
 * only).
 */
final class StageDetails {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private StageDetails() {}

    static String toJson(Map<String, Object> details) {
        return MAPPER.writeValueAsString(details);
    }

    /** Sentiment stage details: where the score came from and, when degraded, why. */
    static String sentiment(String source, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("source", source == null ? "LLM" : source);
        if (reason != null) {
            details.put("reason", reason);
            details.put("warnings", List.of(reason));
        }
        return toJson(details);
    }

    /** Generic degraded details with a single reason code and warning text. */
    static String degraded(String reasonCode, String warning) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reasonCode);
        details.put("warnings", List.of(warning));
        return toJson(details);
    }

    /** Parses a stored details string; returns an empty map for null/blank/invalid JSON. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /** Strategy rows of a SIGNAL stage's details, or an empty list. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> strategies(String json) {
        Object rows = parse(json).get("strategies");
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    /** The {@code reason} code of a stage's details, or null. */
    static String reason(String json) {
        return parse(json).get("reason") instanceof String s ? s : null;
    }
}
