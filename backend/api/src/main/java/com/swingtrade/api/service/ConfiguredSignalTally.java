package com.swingtrade.api.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-symbol tally of configured-variant outcomes. Renders the SIGNAL stage summary and the
 * structured {@code details} JSON, and tells the orchestrator whether the stage is DEGRADED
 * (any variant skipped or errored).
 */
final class ConfiguredSignalTally {

    private record VariantOutcome(String variantId, int version, String outcome, String reason,
                                  BigDecimal score, Boolean signal) {}

    private int evaluated;
    private int signals;
    private int skipped;
    private int errors;
    private final List<String> problems = new ArrayList<>();
    private final List<VariantOutcome> outcomes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /** A non-degrading warning surfaced in the stage details (e.g. the champion guard). */
    void warn(String warning) {
        warnings.add(warning);
    }

    void evaluated(String variantId, int version, boolean producedSignal, BigDecimal score) {
        evaluated++;
        if (producedSignal) signals++;
        outcomes.add(new VariantOutcome(variantId, version, "EVALUATED", null, score, producedSignal));
    }

    void skipped(String variantId, int version, String reason) {
        skipped++;
        problems.add(variantId + " skipped: " + reason);
        outcomes.add(new VariantOutcome(variantId, version, "SKIPPED", reason, null, null));
    }

    void error(String variantId, int version, String message) {
        errors++;
        problems.add(variantId + " error: " + message);
        outcomes.add(new VariantOutcome(variantId, version, "ERROR", message, null, null));
    }

    boolean degraded() {
        return skipped + errors > 0;
    }

    /** STRATEGY_ERROR when any variant errored, else STRATEGY_SKIPPED; null when not degraded. */
    String reasonCode() {
        if (errors > 0) return "STRATEGY_ERROR";
        return skipped > 0 ? "STRATEGY_SKIPPED" : null;
    }

    List<String> evaluatedVariantIds() {
        return idsWithOutcome("EVALUATED");
    }

    List<String> skippedVariantIds() {
        return idsWithOutcome("SKIPPED");
    }

    private List<String> idsWithOutcome(String outcome) {
        return outcomes.stream().filter(o -> o.outcome().equals(outcome)).map(VariantOutcome::variantId).toList();
    }

    String summary() {
        String summary = evaluated + " evaluated, " + signals + " signal(s), " + skipped + " skipped, "
            + errors + " error(s)";
        return problems.isEmpty() ? summary : summary + "; " + String.join("; ", problems);
    }

    /** Structured details: {@code {"strategies":[{variantId,version,outcome,reason?,score?,signal?}]}}. */
    String detailsJson() {
        List<Map<String, Object>> strategies = new ArrayList<>();
        for (VariantOutcome o : outcomes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("variantId", o.variantId());
            row.put("version", o.version());
            row.put("outcome", o.outcome());
            if (o.reason() != null) row.put("reason", o.reason());
            if (o.score() != null) row.put("score", o.score());
            if (o.signal() != null) row.put("signal", o.signal());
            strategies.add(row);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("strategies", strategies);
        if (degraded()) {
            details.put("reason", reasonCode());
        }
        List<String> allWarnings = new ArrayList<>(problems);
        allWarnings.addAll(warnings);
        if (!allWarnings.isEmpty()) {
            details.put("warnings", allWarnings);
        }
        return StageDetails.toJson(details);
    }

    /** Maps a free-text skip reason to a low-cardinality metric label. */
    static String skipReasonCode(String reason) {
        if (reason == null) return "OTHER";
        if (reason.startsWith("unsupported strategy type")) return "UNSUPPORTED_TYPE";
        if (reason.startsWith("insufficient candle history")) return "INSUFFICIENT_HISTORY";
        if (reason.startsWith("invalid parameters")) return "INVALID_PARAMS";
        return "OTHER";
    }
}
