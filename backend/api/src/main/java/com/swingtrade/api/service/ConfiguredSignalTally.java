package com.swingtrade.api.service;

import java.util.ArrayList;
import java.util.List;

/** Per-symbol tally of configured-variant outcomes, rendered as the SIGNAL stage summary. */
final class ConfiguredSignalTally {

    private int evaluated;
    private int signals;
    private int skipped;
    private int errors;
    private final List<String> problems = new ArrayList<>();

    void evaluated(boolean producedSignal) {
        evaluated++;
        if (producedSignal) signals++;
    }

    void skipped(String variantId, String reason) {
        skipped++;
        problems.add(variantId + " skipped: " + reason);
    }

    void error(String variantId, String message) {
        errors++;
        problems.add(variantId + " error: " + message);
    }

    String summary() {
        String summary = evaluated + " evaluated, " + signals + " signal(s), " + skipped + " skipped, "
            + errors + " error(s)";
        return problems.isEmpty() ? summary : summary + "; " + String.join("; ", problems);
    }
}
