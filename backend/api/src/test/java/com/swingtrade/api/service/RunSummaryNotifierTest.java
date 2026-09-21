package com.swingtrade.api.service;

import com.swingtrade.api.service.JobOrchestratorService.DegradedStageBreakdown;
import com.swingtrade.api.service.JobOrchestratorService.JobRunSummary;
import com.swingtrade.api.service.JobOrchestratorService.SkippedStrategyBreakdown;
import com.swingtrade.broker.service.DiscordNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RunSummaryNotifier")
class RunSummaryNotifierTest {

    private static JobRunSummary summary(String status, int degraded, int skipped) {
        return new JobRunSummary(UUID.randomUUID(), status, 10, 10, 0, 1000L, Map.of(), List.of(), degraded, skipped,
            degraded == 0 ? List.of() : List.of(new DegradedStageBreakdown("SENTIMENT", "KEYWORD_FALLBACK", degraded)),
            skipped == 0 ? List.of()
                : List.of(new SkippedStrategyBreakdown("pullback-v1", "SKIPPED", "unsupported strategy type X", skipped)));
    }

    @Test
    void listsDegradedStagesAndSkippedStrategies() {
        String text = RunSummaryNotifier.format(summary("COMPLETED_WITH_WARNINGS", 10, 10));

        assertThat(text).contains("SENTIMENT (KEYWORD_FALLBACK): 10")
            .contains("pullback-v1 skipped: unsupported strategy type X (10 symbol(s))");
    }

    @Test
    void sendsOnWarningsAndStaysQuietOnCleanRuns() {
        var discord = mock(DiscordNotificationService.class);
        var notifier = new RunSummaryNotifier(discord);

        notifier.notifyRun(summary("COMPLETED", 0, 0));
        verify(discord, never()).sendEmbed(anyString(), anyString(), anyInt());

        notifier.notifyRun(summary("COMPLETED_WITH_WARNINGS", 2, 1));
        verify(discord).sendEmbed(contains("COMPLETED_WITH_WARNINGS"), contains("Degraded stages"), anyInt());
    }
}
