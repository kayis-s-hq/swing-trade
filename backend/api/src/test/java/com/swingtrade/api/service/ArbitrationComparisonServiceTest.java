package com.swingtrade.api.service;

import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.domain.ShadowClosedTrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArbitrationComparisonServiceTest {

    private static ShadowClosedTrade trade(LocalDate entry, LocalDate exit, String pnl) {
        return new ShadowClosedTrade("v", "SBIN", entry, exit, new BigDecimal("100"), null, null, null, 10,
            "TARGET_HIT", new BigDecimal(pnl));
    }

    private static Map<String, Object> cand(String id, String signal, String confidence) {
        return Map.of("variantId", id, "signal", signal, "confidence", new BigDecimal(confidence));
    }

    @Test
    void replaysEachRuleOnRecordedSlateWithoutLookahead() {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        // "proven" earned +5% on 5 trades that all exited before the selection date.
        List<ShadowClosedTrade> provenHistory = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            provenHistory.add(trade(d0.minusDays(20 - i), d0.minusDays(15 - i), "50"));
        }
        // The trade taken right after the selection: flashy lost, proven won.
        LocalDate selectionDate = d0;
        provenHistory.add(trade(selectionDate.plusDays(1), selectionDate.plusDays(4), "80"));
        var flashyTrades = List.of(trade(selectionDate.plusDays(1), selectionDate.plusDays(4), "-60"));

        SignalSelectionEntity sel = new SignalSelectionEntity("SBIN", selectionDate, "flashy", 1, 1L,
            new BigDecimal("0.90"),
            List.of(cand("flashy", "BUY", "0.90"), cand("proven", "BUY", "0.60")), "test");

        var comparison = ArbitrationComparisonService.replay(selectionDate, selectionDate, List.of(sel),
            Map.of("flashy", flashyTrades, "proven", provenHistory));

        var confidence = comparison.rules().get(0);
        var evidence = comparison.rules().get(1);
        assertThat(confidence.rule()).isEqualTo("HIGHEST_CONFIDENCE");
        assertThat(confidence.avgReturnPct()).isEqualTo(-6.0);
        assertThat(confidence.differsFromHighestConfidence()).isZero();
        assertThat(evidence.rule()).isEqualTo("EVIDENCE_RANKED");
        assertThat(evidence.avgReturnPct()).isEqualTo(8.0);
        assertThat(evidence.differsFromHighestConfidence()).isEqualTo(1);
        assertThat(comparison.tournaments()).isEqualTo(1);
    }

    @Test
    void emptyRangeYieldsNoOutcomes() {
        var comparison = ArbitrationComparisonService.replay(LocalDate.now(), LocalDate.now(), List.of(), Map.of());
        assertThat(comparison.rules()).allSatisfy(r -> {
            assertThat(r.decisions()).isZero();
            assertThat(r.avgReturnPct()).isNull();
        });
    }
}
