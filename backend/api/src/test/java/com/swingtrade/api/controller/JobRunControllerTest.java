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
import java.util.UUID;

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
    }
}
