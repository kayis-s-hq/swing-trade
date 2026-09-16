package com.swingtrade.api.service;

import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.FundamentalData;
import com.swingtrade.domain.source.FundamentalDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FundamentalScorerTest {

    @Test
    void compute_scores_sourced_valuation_without_price_history() {
        FundamentalDataSource source = mock(FundamentalDataSource.class);
        when(source.findBySymbol("TEST"))
            .thenReturn(Optional.of(new FundamentalData("TEST", 1_000_000L, BigDecimal.TEN)));

        CompositeAnalysis.FundamentalScore result = new FundamentalScorer(source).compute("test");

        assertEquals(25, result.score());
        assertTrue(result.factors().stream().anyMatch(f -> f.contains("P/E ratio attractive")));
    }

    @Test
    void compute_fails_closed_when_fundamentals_are_unavailable() {
        FundamentalDataSource source = mock(FundamentalDataSource.class);
        when(source.findBySymbol("TEST")).thenReturn(Optional.empty());

        CompositeAnalysis.FundamentalScore result = new FundamentalScorer(source).compute("TEST");

        assertEquals(0, result.score());
        assertEquals(java.util.List.of("Fundamentals unavailable"), result.factors());
    }

    @Test
    void compute_fails_closed_when_source_throws() {
        FundamentalDataSource source = mock(FundamentalDataSource.class);
        when(source.findBySymbol("TEST")).thenThrow(new RuntimeException("database unavailable"));

        CompositeAnalysis.FundamentalScore result = new FundamentalScorer(source).compute("TEST");

        assertEquals(0, result.score());
        assertEquals(java.util.List.of("Fundamentals unavailable"), result.factors());
    }

    @Test
    void compute_does_not_treat_non_positive_pe_as_bullish() {
        FundamentalDataSource source = mock(FundamentalDataSource.class);
        when(source.findBySymbol("TEST"))
            .thenReturn(Optional.of(new FundamentalData("TEST", 1L, BigDecimal.ZERO)));

        CompositeAnalysis.FundamentalScore result = new FundamentalScorer(source).compute("TEST");

        assertEquals(0, result.score());
        assertTrue(result.factors().stream().anyMatch(f -> f.contains("not positive")));
    }
}
