package com.swingtrade.api.service;

import com.swingtrade.domain.store.LlmAnalysisResultStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmAnalysisGateTest {
    private final LlmAnalysisResultStore store = mock(LlmAnalysisResultStore.class);
    private final LlmAnalysisGate gate = new LlmAnalysisGate(store);
    private final LocalDate date = LocalDate.of(2026, 8, 30);

    @Test void missingResultIsPending() {
        when(store.findBySymbolAndDate("TCS", date)).thenReturn(Optional.empty());
        assertThat(gate.evaluatePersisted("TCS", date).action())
            .isEqualTo(LlmAnalysisGate.LlmVerdict.Action.PENDING);
    }

    @Test void sellSuppresses() {
        when(store.findBySymbolAndDate("TCS", date)).thenReturn(Optional.of(result("SELL", true, false)));
        assertThat(gate.evaluatePersisted("TCS", date).action())
            .isEqualTo(LlmAnalysisGate.LlmVerdict.Action.SUPPRESS);
    }

    @Test void holdFlagsNeutral() {
        when(store.findBySymbolAndDate("TCS", date)).thenReturn(Optional.of(result("HOLD", true, false)));
        assertThat(gate.evaluatePersisted("TCS", date).action())
            .isEqualTo(LlmAnalysisGate.LlmVerdict.Action.FLAG_NEUTRAL);
    }

    @Test void buyAllows() {
        when(store.findBySymbolAndDate("TCS", date)).thenReturn(Optional.of(result("STRONG_BUY", true, false)));
        assertThat(gate.evaluatePersisted("TCS", date).action())
            .isEqualTo(LlmAnalysisGate.LlmVerdict.Action.ALLOW);
    }

    @Test void fallbackAllowsGracefully() {
        when(store.findBySymbolAndDate("TCS", date)).thenReturn(Optional.of(result(null, false, true)));
        assertThat(gate.evaluatePersisted("TCS", date).action())
            .isEqualTo(LlmAnalysisGate.LlmVerdict.Action.ALLOW_GRACEFUL);
    }

    private static com.swingtrade.domain.LlmAnalysisResult result(String recommendation, boolean success,
                                                                    boolean fallback) {
        return new com.swingtrade.domain.LlmAnalysisResult(null, null, "TCS", LocalDate.of(2026, 8, 30),
            recommendation, 0.8, "n", List.of(), List.of(), List.of(), 0, "HOLD", success, fallback, null);
    }
}
