package com.swingtrade.api.service;

/** How the signal tournament chooses among competing BUY signals for one symbol/day. */
public enum ArbitrationRule {
    /** Highest signal confidence wins (default). */
    HIGHEST_CONFIDENCE,
    /** Variant with the best trailing shadow-book return wins; confidence breaks ties. */
    EVIDENCE_RANKED
}
