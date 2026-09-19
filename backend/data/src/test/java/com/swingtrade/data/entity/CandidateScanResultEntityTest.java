package com.swingtrade.data.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateScanResultEntityTest {
    @Test
    void storesScanMetricsAndEligibilityFields() {
        CandidateScanResultEntity entity = new CandidateScanResultEntity();
        UUID run = UUID.randomUUID();
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 1);
        entity.setRunId(run); entity.setSymbol("ABC"); entity.setDataStatus("READY");
        entity.setCandleCount(500); entity.setSignalType("BUY"); entity.setTotalTrades(20);
        entity.setWinRate(.65); entity.setTotalReturn(22.5); entity.setMaxDrawdownPct(8.2);
        entity.setOosStartDate(start); entity.setOosEndDate(end); entity.setOosTotalTrades(8);
        entity.setOosWinRate(.75); entity.setOosTotalReturn(11.5); entity.setOosMaxDrawdownPct(4.2);
        entity.setQualified(true); entity.setActivated(true); entity.setReason("passed gates");
        entity.setErrorMessage(null); entity.setSourceOutcome("DATA_RECEIVED"); entity.setInvalidRows(2);
        entity.setFirstAvailableDate(start); entity.setLastAvailableDate(end);
        entity.setRetryAfter(end.plusDays(1));
        LocalDateTime created = LocalDateTime.now();
        entity.setCreatedAt(created);

        assertThat(entity.getRunId()).isEqualTo(run); assertThat(entity.getSymbol()).isEqualTo("ABC");
        assertThat(entity.getDataStatus()).isEqualTo("READY"); assertThat(entity.getCandleCount()).isEqualTo(500);
        assertThat(entity.getSignalType()).isEqualTo("BUY"); assertThat(entity.getTotalTrades()).isEqualTo(20);
        assertThat(entity.getWinRate()).isEqualTo(.65); assertThat(entity.getTotalReturn()).isEqualTo(22.5);
        assertThat(entity.getMaxDrawdownPct()).isEqualTo(8.2); assertThat(entity.getOosStartDate()).isEqualTo(start);
        assertThat(entity.getOosEndDate()).isEqualTo(end); assertThat(entity.getOosTotalTrades()).isEqualTo(8);
        assertThat(entity.getOosWinRate()).isEqualTo(.75); assertThat(entity.getOosTotalReturn()).isEqualTo(11.5);
        assertThat(entity.getOosMaxDrawdownPct()).isEqualTo(4.2); assertThat(entity.isQualified()).isTrue();
        assertThat(entity.isActivated()).isTrue(); assertThat(entity.getReason()).isEqualTo("passed gates");
        assertThat(entity.getErrorMessage()).isNull(); assertThat(entity.getSourceOutcome()).isEqualTo("DATA_RECEIVED");
        assertThat(entity.getInvalidRows()).isEqualTo(2); assertThat(entity.getFirstAvailableDate()).isEqualTo(start);
        assertThat(entity.getLastAvailableDate()).isEqualTo(end); assertThat(entity.getRetryAfter()).isEqualTo(end.plusDays(1));
        assertThat(entity.getCreatedAt()).isEqualTo(created);
    }
}
