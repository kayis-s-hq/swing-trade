package com.swingtrade.api.service;

import com.swingtrade.data.entity.GateEffectivenessAuditEntity;
import com.swingtrade.data.repository.GateEffectivenessAuditRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GateEffectivenessAuditServiceTest {
    private final GateEffectivenessAuditRepository repository = mock(GateEffectivenessAuditRepository.class);
    private final CandleStore candles = mock(CandleStore.class);
    private final GateEffectivenessAuditService service = new GateEffectivenessAuditService(repository, candles);
    private final LocalDate date = LocalDate.of(2026, 8, 3);

    @Test
    void reportSeparatesVerdictsAndComputesOnlyAvailableForwardReturns() {
        var allowed = new GateEffectivenessAuditEntity("TCS", date, "SENTIMENT", "ALLOW", null, null);
        var blocked = new GateEffectivenessAuditEntity("TCS", date.plusDays(1), "SENTIMENT", "SUPPRESS", "negative", null);
        when(repository.findByGateNameAndSignalDateBetweenOrderBySignalDateAsc(any(), any(), any()))
            .thenReturn(List.of(allowed, blocked));
        when(candles.findBySymbolAndDate("TCS", date))
            .thenReturn(Optional.of(candle(date, "100")));
        when(candles.findBySymbolAndDate("TCS", date.plusDays(1)))
            .thenReturn(Optional.of(candle(date.plusDays(1), "200")));
        when(candles.findNthBySymbolAndDateAfterOrderByDateAsc("TCS", date, 1))
            .thenReturn(Optional.of(candle(date.plusDays(2), "110")));
        when(candles.findNthBySymbolAndDateAfterOrderByDateAsc("TCS", date.plusDays(1), 1))
            .thenReturn(Optional.of(candle(date.plusDays(2), "190")));
        when(candles.findNthBySymbolAndDateAfterOrderByDateAsc(any(), any(), eq(5)))
            .thenReturn(Optional.empty());
        when(candles.findNthBySymbolAndDateAfterOrderByDateAsc(any(), any(), eq(20)))
            .thenReturn(Optional.empty());

        var report = service.report(date, date.plusDays(5), null);

        assertThat(report.auditCount()).isEqualTo(2);
        assertThat(report.verdicts()).containsKeys("ALLOW", "SUPPRESS");
        assertThat(report.verdicts().get("ALLOW").count()).isEqualTo(1);
        assertThat(report.verdicts().get("ALLOW").meanForwardReturnPct().get(1))
            .isEqualByComparingTo("10.0000");
        assertThat(report.verdicts().get("SUPPRESS").meanForwardReturnPct().get(1))
            .isEqualByComparingTo("-5.0000");
        assertThat(report.verdicts().get("ALLOW").meanForwardReturnPct()).doesNotContainKey(5);
        assertThat(report.verdicts().get("ALLOW").returnObservationCounts()).containsEntry(1, 1);
        assertThat(report.byStrategy()).containsKey("DEFAULT");
        assertThat(report.byRegime()).containsKey("UNKNOWN");
    }

    @Test
    void reportCanFilterByDerivedRegimeWithoutTreatingMissingOutcomesAsLosses() {
        var audit = new GateEffectivenessAuditEntity("TCS", date, "SENTIMENT", "ALLOW", null, null);
        when(repository.findByGateNameAndSymbolAndSignalDateBetweenOrderBySignalDateAsc(
            any(), eq("TCS"), any(), any())).thenReturn(List.of(audit));
        when(candles.findLastNBySymbolBeforeDateAsc("TCS", date, 20))
            .thenReturn(List.of(candle(date.minusDays(2), "100"), candle(date.minusDays(1), "103")));
        when(candles.findBySymbolAndDate("TCS", date)).thenReturn(Optional.of(candle(date, "100")));
        when(candles.findNthBySymbolAndDateAfterOrderByDateAsc("TCS", date, 1))
            .thenReturn(Optional.of(candle(date.plusDays(1), "102")));
        when(candles.findNthBySymbolAndDateAfterOrderByDateAsc(any(), any(), eq(5)))
            .thenReturn(Optional.empty());
        when(candles.findNthBySymbolAndDateAfterOrderByDateAsc(any(), any(), eq(20)))
            .thenReturn(Optional.empty());

        var report = service.report(date, date, "TCS", "DEFAULT", "BULL");

        assertThat(report.auditCount()).isEqualTo(1);
        assertThat(report.strategy()).isEqualTo("DEFAULT");
        assertThat(report.regime()).isEqualTo("BULL");
        assertThat(report.byRegime()).containsKey("BULL");
        assertThat(report.verdicts().get("ALLOW").meanForwardReturnPct()).containsEntry(1, new BigDecimal("2.0000"));
        assertThat(report.verdicts().get("ALLOW").returnObservationCounts())
            .containsEntry(1, 1).doesNotContainKey(5);
    }

    @Test
    void unsupportedStrategyFilterReturnsNoAuditRows() {
        when(repository.findByGateNameAndSignalDateBetweenOrderBySignalDateAsc(any(), any(), any()))
            .thenReturn(List.of(new GateEffectivenessAuditEntity("TCS", date, "SENTIMENT", "ALLOW", null, null)));

        var report = service.report(date, date, null, "OTHER", null);

        assertThat(report.auditCount()).isZero();
        assertThat(report.verdicts()).isEmpty();
    }

    private static OhlcvCandle candle(LocalDate date, String close) {
        BigDecimal value = new BigDecimal(close);
        return OhlcvCandle.of("TCS", date, value, value, value, value, 1L);
    }
}
