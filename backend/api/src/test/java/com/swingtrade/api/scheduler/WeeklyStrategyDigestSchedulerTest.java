package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.StrategyAttributionService;
import com.swingtrade.api.service.StrategyAttributionService.Report;
import com.swingtrade.api.service.StrategyAttributionService.Totals;
import com.swingtrade.api.service.StrategyAttributionService.VariantReport;
import com.swingtrade.broker.service.DiscordNotificationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class WeeklyStrategyDigestSchedulerTest {

    private static Report sample() {
        var v = new VariantReport("breakout-v1", "BREAKOUT", "SHADOW", 4, 2, 3, 60.0, 1, 1, 100.0,
            new BigDecimal("120.00"), 2, 1, 50.0, new BigDecimal("30.00"), 50.0, null, 0, 0, 0);
        return new Report(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19), List.of(v),
            new Totals(5, 2, 1, 2, 1, new BigDecimal("120.00"), null));
    }

    @Test
    void doesNothingWhenDisabled() {
        var service = mock(StrategyAttributionService.class);
        var discord = mock(DiscordNotificationService.class);
        new WeeklyStrategyDigestScheduler(service, discord, false).sendWeeklyStrategyDigest();
        verifyNoInteractions(service, discord);
    }

    @Test
    void sendsDigestWhenEnabled() {
        var service = mock(StrategyAttributionService.class);
        var discord = mock(DiscordNotificationService.class);
        when(service.report(any(), any())).thenReturn(sample());
        new WeeklyStrategyDigestScheduler(service, discord, true).sendWeeklyStrategyDigest();
        verify(discord).sendEmbed(anyString(), anyString(), anyInt());
    }

    @Test
    void digestListsEachVariantWithWinsAndShadowStats() {
        String text = WeeklyStrategyDigestScheduler.formatDigest(sample());
        assertThat(text).contains("5 tournaments", "**breakout-v1**", "won 3", "50.0% win", "P&L 30.00");
    }
}
