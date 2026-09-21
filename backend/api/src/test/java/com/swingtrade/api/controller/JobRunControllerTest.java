package com.swingtrade.api.controller;

import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.domain.JobRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobRunController tests")
class JobRunControllerTest {

    @Mock
    private JobOrchestratorService orchestratorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new JobRunController(orchestratorService)).build();
    }

    private JobRun runningRun(JobRun.TriggerType triggerType) {
        return new JobRun(
            UUID.randomUUID(), triggerType, JobRun.Status.RUNNING, LocalDateTime.now(),
            null, 5, 0, 0, null
        );
    }

    @Nested
    @DisplayName("POST /api/job/runs/start")
    class StartRun {

        @Test
        @DisplayName("Defaults to a manual trigger and returns the accepted run")
        void shouldDefaultToManualTrigger() throws Exception {
            JobRun run = runningRun(JobRun.TriggerType.MANUAL);
            when(orchestratorService.startRun(JobRun.TriggerType.MANUAL)).thenReturn(run);

            mockMvc.perform(post("/api/job/runs/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(run.runId().toString()))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.symbolsCount").value(5));

            verify(orchestratorService).startRun(JobRun.TriggerType.MANUAL);
        }

        @Test
        @DisplayName("Passes a JSON body's scoping to the orchestrator")
        void shouldPassBodyScoping() throws Exception {
            JobRun run = runningRun(JobRun.TriggerType.MANUAL);
            when(orchestratorService.startRun(any(JobRun.TriggerType.class),
                any(com.swingtrade.api.service.RunRequest.class))).thenReturn(run);

            mockMvc.perform(post("/api/job/runs/start").contentType("application/json")
                    .content("{\"symbols\":[\"TCS\"],\"variantIds\":[\"breakout-v1\"],\"skipLlm\":true,\"dryRun\":true}"))
                .andExpect(status().isOk());

            var captor = org.mockito.ArgumentCaptor.forClass(com.swingtrade.api.service.RunRequest.class);
            verify(orchestratorService).startRun(any(JobRun.TriggerType.class), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue()).isEqualTo(
                new com.swingtrade.api.service.RunRequest(List.of("TCS"), List.of("breakout-v1"), null, true, true));
        }

        @Test
        @DisplayName("Accepts comma-separated scoping query parameters (dev-stack.sh run)")
        void shouldPassQueryScoping() throws Exception {
            JobRun run = runningRun(JobRun.TriggerType.MANUAL);
            when(orchestratorService.startRun(any(JobRun.TriggerType.class),
                any(com.swingtrade.api.service.RunRequest.class))).thenReturn(run);

            mockMvc.perform(post("/api/job/runs/start").contentType("application/json").content("{}")
                    .param("symbols", "TCS,INFY").param("variantIds", "a-v1,b-v1").param("skipLlm", "true"))
                .andExpect(status().isOk());

            var captor = org.mockito.ArgumentCaptor.forClass(com.swingtrade.api.service.RunRequest.class);
            verify(orchestratorService).startRun(any(JobRun.TriggerType.class), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue()).isEqualTo(
                new com.swingtrade.api.service.RunRequest(List.of("TCS", "INFY"), List.of("a-v1", "b-v1"), null,
                    true, null));
        }

        @Test
        @DisplayName("Returns 400 for unknown symbols, variants or stages")
        void shouldReturn400ForUnknownIds() throws Exception {
            when(orchestratorService.startRun(any(JobRun.TriggerType.class),
                any(com.swingtrade.api.service.RunRequest.class)))
                .thenThrow(new com.swingtrade.api.service.InvalidRunRequestException("unknown variantIds: [ghost]"));

            mockMvc.perform(post("/api/job/runs/start").param("variantIds", "ghost"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Accepts the scheduled trigger query parameter")
        void shouldAcceptScheduledTriggerType() throws Exception {
            JobRun run = runningRun(JobRun.TriggerType.SCHEDULED);
            when(orchestratorService.startRun(JobRun.TriggerType.SCHEDULED)).thenReturn(run);

            mockMvc.perform(post("/api/job/runs/start")
                    .param("triggerType", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerType").value("SCHEDULED"));

            verify(orchestratorService).startRun(JobRun.TriggerType.SCHEDULED);
        }

        @Test
        @DisplayName("Rejects with 409 when another run is already active")
        void shouldReject409WhenAnotherRunIsAlreadyActive() throws Exception {
            JobRun active = runningRun(JobRun.TriggerType.SCHEDULED);
            when(orchestratorService.findActiveRun()).thenReturn(Optional.of(active));

            mockMvc.perform(post("/api/job/runs/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"));

            verify(orchestratorService, never()).startRun(any());
        }
    }
}
