package com.swingtrade.api.service;

import com.swingtrade.domain.JobRunStage.StageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A validated {@link RunRequest}: the symbols to process, the variants to evaluate and the
 * stages that are skipped for this run.
 */
final class RunScope {

    static final RunScope FULL = new RunScope(null, Set.of(), Map.of(), false, null);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    /** Stages that persist news/sentiment/LLM output or trades and so never run in a dry run. */
    private static final Set<StageName> DRY_RUN_SKIPPED =
        EnumSet.of(StageName.NEWS, StageName.SENTIMENT, StageName.LLM_ANALYSIS, StageName.PAPER_TRADE);
    private static final Set<StageName> LLM_STAGES = EnumSet.of(StageName.SENTIMENT, StageName.LLM_ANALYSIS);

    private final List<String> symbols;
    private final Set<String> variantIds;
    private final Map<StageName, String> skippedStages;
    private final boolean dryRun;
    private final String requestJson;

    private RunScope(List<String> symbols, Set<String> variantIds, Map<StageName, String> skippedStages,
                     boolean dryRun, String requestJson) {
        this.symbols = symbols;
        this.variantIds = variantIds;
        this.skippedStages = skippedStages;
        this.dryRun = dryRun;
        this.requestJson = requestJson;
    }

    /**
     * Validates {@code request} against the active watchlist and the live variant ids.
     *
     * @throws InvalidRunRequestException naming every unknown id
     */
    static RunScope resolve(RunRequest request, List<String> activeSymbols, Set<String> knownVariants) {
        if (request == null || request.isEmpty()) {
            return FULL;
        }
        List<String> errors = new ArrayList<>();
        List<String> symbols = null;
        if (request.symbols() != null && !request.symbols().isEmpty()) {
            symbols = new ArrayList<>(new LinkedHashSet<>(request.symbols().stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT)).toList()));
            List<String> unknown = symbols.stream().filter(s -> !activeSymbols.contains(s)).toList();
            if (!unknown.isEmpty()) errors.add("unknown symbols (not in the active watchlist): " + unknown);
        }
        Set<String> variants = new LinkedHashSet<>();
        if (request.variantIds() != null) {
            request.variantIds().forEach(v -> variants.add(v.trim()));
            List<String> unknown = variants.stream().filter(v -> !knownVariants.contains(v)).toList();
            if (!unknown.isEmpty()) errors.add("unknown variantIds: " + unknown);
        }
        Set<StageName> requested = EnumSet.noneOf(StageName.class);
        if (request.stages() != null) {
            for (String name : request.stages()) {
                try {
                    requested.add(StageName.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    errors.add("unknown stage: " + name);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidRunRequestException(String.join("; ", errors));
        }
        boolean dry = Boolean.TRUE.equals(request.dryRun());
        Map<StageName, String> skipped = new java.util.EnumMap<>(StageName.class);
        for (StageName stage : StageName.values()) {
            if (!requested.isEmpty() && !requested.contains(stage)) skipped.put(stage, "not requested");
            if (Boolean.TRUE.equals(request.skipLlm()) && LLM_STAGES.contains(stage)) {
                skipped.put(stage, "skipLlm requested");
            }
            if (dry && DRY_RUN_SKIPPED.contains(stage)) skipped.put(stage, "dry run");
        }
        return new RunScope(symbols, variants, skipped, dry, toJson(request));
    }

    private static String toJson(RunRequest request) {
        return MAPPER.writeValueAsString(request);
    }

    /** The symbols to process, or {@code null} for the whole active watchlist. */
    List<String> symbols() {
        return symbols;
    }

    /** True when {@code variantId} is in scope (an empty selection means all variants). */
    boolean includesVariant(String variantId) {
        return variantIds.isEmpty() || variantIds.contains(variantId);
    }

    /** The reason {@code stage} is skipped for this run, or {@code null} when it runs. */
    String skipReason(StageName stage) {
        return skippedStages.get(stage);
    }

    boolean dryRun() {
        return dryRun;
    }

    /** The original request as JSON for {@code job_runs.trigger_options}; null for a full run. */
    String requestJson() {
        return requestJson;
    }
}
