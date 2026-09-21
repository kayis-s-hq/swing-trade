package com.swingtrade.api.service;

import java.util.List;

/**
 * Optional scoping of a manual run ({@code POST /api/job/runs/start}). Every field is optional;
 * an all-null/false request means "the full run", exactly as before.
 *
 * @param symbols    restrict the run to these watchlist symbols
 * @param variantIds restrict SIGNAL/PAPER_TRADE to these configured strategy variants
 * @param stages     run only these stages (names of {@code JobRunStage.StageName}); others are SKIPPED
 * @param skipLlm    skip the SENTIMENT and LLM_ANALYSIS stages
 * @param dryRun     evaluate and report only: no signals, paper trades, sentiment or news are persisted
 */
public record RunRequest(List<String> symbols, List<String> variantIds, List<String> stages,
                         Boolean skipLlm, Boolean dryRun) {

    public static final RunRequest NONE = new RunRequest(null, null, null, null, null);

    /** True when the request narrows nothing (the legacy full run). */
    public boolean isEmpty() {
        return isBlank(symbols) && isBlank(variantIds) && isBlank(stages)
            && !Boolean.TRUE.equals(skipLlm) && !Boolean.TRUE.equals(dryRun);
    }

    private static boolean isBlank(List<String> list) {
        return list == null || list.isEmpty();
    }
}
