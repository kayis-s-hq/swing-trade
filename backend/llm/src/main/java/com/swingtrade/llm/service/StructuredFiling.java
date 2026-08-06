package com.swingtrade.llm.service;


/**
 * Represents a structured corporate filing from NSE/BSE announcements.
 * Unlike raw articles, filings are structured data (board meetings, shareholding changes, etc.)
 * that get injected as a separate section in the LLM prompt.
 */
public record StructuredFiling(
    FilingType type,
    LocalDate date,
    String title,
    String description,
    String link
) {
    public enum FilingType {
        BOARD_MEETING,
        SHAREHOLDING,
        DIVIDEND,
        CORPORATE_ACTION,
        PERFORMANCE_RESULT,
        OTHER
    }

    /**
     * Returns a human-readable label for the filing type.
     */
    public String typeLabel() {
        return switch (type) {
            case BOARD_MEETING -> "Board Meeting";
            case SHAREHOLDING -> "Shareholding Pattern";
            case DIVIDEND -> "Dividend";
            case CORPORATE_ACTION -> "Corporate Action";
            case PERFORMANCE_RESULT -> "Quarterly Results";
            case OTHER -> "Announcement";
        };
    }
}