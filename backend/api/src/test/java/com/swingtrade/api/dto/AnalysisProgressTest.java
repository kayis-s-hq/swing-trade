package com.swingtrade.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for AnalysisProgress DTO, focused on the null-message guard in
 * each static factory method. The SSE wire contract requires message to
 * always be a non-blank string (dashboard/src/api/analysis.ts rejects
 * a null message and aborts the stream), so every factory must substitute
 * a sensible fallback when given null.
 */
class AnalysisProgressTest {

    @Test
    void completedWithNullMessageFallsBackToNonBlankMessage() {
        AnalysisProgress progress = AnalysisProgress.completed(1, "Technical", null);

        assertEquals("completed", progress.status());
        assertNotNull(progress.message());
        assertFalse(progress.message().isBlank());
    }

    @Test
    void completedWithDetailsAndNullMessageFallsBackToNonBlankMessage() {
        AnalysisProgress.StageDetails details = new AnalysisProgress.StageDetails("technical", null);
        AnalysisProgress progress = AnalysisProgress.completed(1, "Technical", null, details);

        assertEquals("completed", progress.status());
        assertNotNull(progress.message());
        assertFalse(progress.message().isBlank());
        assertEquals(details, progress.details());
    }

    @Test
    void skippedWithNullMessageFallsBackToNonBlankMessage() {
        AnalysisProgress progress = AnalysisProgress.skipped(4, "Sentiment", null);

        assertEquals("skipped", progress.status());
        assertNotNull(progress.message());
        assertFalse(progress.message().isBlank());
    }

    @Test
    void errorWithNullMessageFallsBackToNonBlankMessage() {
        // Reproduces the live stage-9 synthesis failure: a bare NullPointerException
        // (e.g. from Map.of receiving a null value) has a null getMessage().
        AnalysisProgress progress = AnalysisProgress.error(9, "Synthesis", null);

        assertEquals("error", progress.status());
        assertNotNull(progress.message());
        assertFalse(progress.message().isBlank());
    }

    @Test
    void runningNeverProducesNullMessage() {
        AnalysisProgress progress = AnalysisProgress.running(2, "Fundamentals");

        assertEquals("running", progress.status());
        assertNotNull(progress.message());
        assertFalse(progress.message().isBlank());
    }

    @Test
    void nonNullMessagesArePassedThroughUnchanged() {
        assertEquals("All good", AnalysisProgress.completed(1, "Technical", "All good").message());
        assertEquals("Skipped by config", AnalysisProgress.skipped(4, "Sentiment", "Skipped by config").message());
        assertEquals("Boom", AnalysisProgress.error(9, "Synthesis", "Boom").message());
    }
}
