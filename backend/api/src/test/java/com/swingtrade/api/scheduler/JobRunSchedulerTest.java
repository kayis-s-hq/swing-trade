package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.domain.JobRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobRunScheduler tests")
class JobRunSchedulerTest {

    @Mock
    private JobOrchestratorService orchestratorService;

    private JobRun runningRun() {
        return new JobRun(
            UUID.randomUUID(), JobRun.TriggerType.MANUAL, JobRun.Status.RUNNING,
            LocalDateTime.now(), null, 5, 0, 0, null
        );
    }

    @Nested
    @DisplayName("runScheduledPipeline")
    class RunScheduledPipeline {

        @Test
        @DisplayName("Disabled scheduler never touches the orchestrator")
        void shouldSkipWhenSchedulerDisabled() {
            new JobRunScheduler(orchestratorService, false).runScheduledPipeline();

            verifyNoInteractions(orchestratorService);
        }

        @Test
        @DisplayName("Active run skips the scheduled trigger")
        void shouldSkipWhenAnotherRunIsAlreadyActive() {
            when(orchestratorService.findActiveRun()).thenReturn(Optional.of(runningRun()));

            new JobRunScheduler(orchestratorService, true).runScheduledPipeline();

            verify(orchestratorService, never()).startRun(any());
        }

        @Test
        @DisplayName("No active run starts a scheduled run")
        void shouldStartScheduledRunWhenNoneIsActive() {
            when(orchestratorService.findActiveRun()).thenReturn(Optional.empty());

            new JobRunScheduler(orchestratorService, true).runScheduledPipeline();

            verify(orchestratorService).startRun(JobRun.TriggerType.SCHEDULED);
        }
    }
}
