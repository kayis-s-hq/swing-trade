package com.swingtrade.broker.config;

import java.math.BigDecimal;
import com.swingtrade.data.service.AppSettingsService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTradingPropertiesTest {
    @Test
    void bindsPaperTradingSettings() {
        PaperTradingProperties p = new PaperTradingProperties();
        p.setEnabled(false); p.setMaxConcurrentPositions(2); p.setMaxCapitalPerPosition(BigDecimal.TEN);
        p.setInitialBalance(BigDecimal.valueOf(99)); p.setSignalExecutionDelay(7);
        p.setPositionSizeLimitsEnabled(false); p.setPositionSizeAlertThreshold(70);
        p.setOrderExecutionEnabled(false); p.setOrderExecutionLatencyMillis(8);
        p.setStatePersistenceEnabled(false); p.setSnapshotCron("snap"); p.setMonitorCron("mon");
        p.setRiskManagementEnabled(true); p.setBreakevenRiskMultiple(2); p.setTrailingStopPct(.2);
        p.setPartialExitRiskMultiple(3); p.setPartialExitRatio(BigDecimal.valueOf(.4)); p.setChandelierAtrMultiple(4);
        assertThat(p.isEnabled()).isFalse(); assertThat(p.getMaxConcurrentPositions()).isEqualTo(2);
        assertThat(p.getMaxCapitalPerPosition()).isEqualByComparingTo("10"); assertThat(p.getInitialBalance()).isEqualByComparingTo("99");
        assertThat(p.getSignalExecutionDelay()).isEqualTo(7); assertThat(p.isPositionSizeLimitsEnabled()).isFalse();
        assertThat(p.getPositionSizeAlertThreshold()).isEqualTo(70); assertThat(p.isOrderExecutionEnabled()).isFalse();
        assertThat(p.getOrderExecutionLatencyMillis()).isEqualTo(8); assertThat(p.isStatePersistenceEnabled()).isFalse();
        assertThat(p.getSnapshotCron()).isEqualTo("snap"); assertThat(p.getMonitorCron()).isEqualTo("mon");
        assertThat(p.isRiskManagementEnabled()).isTrue(); assertThat(p.getBreakevenRiskMultiple()).isEqualTo(2);
        assertThat(p.getTrailingStopPct()).isEqualTo(.2); assertThat(p.getPartialExitRiskMultiple()).isEqualTo(3);
        assertThat(p.getPartialExitRatio()).isEqualByComparingTo(".4"); assertThat(p.getChandelierAtrMultiple()).isEqualTo(4);
    }

    @Test
    void loadsOnlyValidPositivePersistedCapital() {
        AppSettingsService settings = mock(AppSettingsService.class);
        PaperTradingProperties p = new PaperTradingProperties();
        p.setAppSettingsService(settings);
        when(settings.get("trading.initial_capital", null)).thenReturn("1234");
        p.loadPersistedInitialBalance();
        assertThat(p.getInitialBalance()).isEqualByComparingTo("1234");

        when(settings.get("trading.initial_capital", null)).thenReturn("-1");
        p.setInitialBalance(BigDecimal.TEN);
        p.loadPersistedInitialBalance();
        assertThat(p.getInitialBalance()).isEqualByComparingTo("10");
        when(settings.get("trading.initial_capital", null)).thenReturn("bad");
        p.loadPersistedInitialBalance();
        assertThat(p.getInitialBalance()).isEqualByComparingTo("10");
    }
}
