package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.CandidateScanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateScanScheduler tests")
class CandidateScanSchedulerTest {

    @Mock
    private CandidateScanService candidateScanService;

    @Nested
    @DisplayName("runScheduledScan")
    class RunScheduledScan {

        @Test
        @DisplayName("Disabled master scheduler never touches the candidate scan service")
        void shouldSkipWhenMasterSchedulerDisabled() {
            new CandidateScanScheduler(candidateScanService, false, true).runScheduledScan();

            verifyNoInteractions(candidateScanService);
        }

        @Test
        @DisplayName("Disabled candidate scan scheduler never touches the candidate scan service")
        void shouldSkipWhenCandidateScanSchedulerDisabled() {
            new CandidateScanScheduler(candidateScanService, true, false).runScheduledScan();

            verifyNoInteractions(candidateScanService);
        }

        @Test
        @DisplayName("Both flags enabled starts a scheduled scan")
        void shouldStartScheduledScanWhenBothFlagsEnabled() {
            new CandidateScanScheduler(candidateScanService, true, true).runScheduledScan();

            verify(candidateScanService).start();
        }

        @Test
        @DisplayName("Already-running scan is handled gracefully without throwing")
        void shouldHandleAlreadyRunningScanGracefully() {
            when(candidateScanService.start()).thenThrow(new IllegalStateException("scan already running"));

            assertThatCode(() -> new CandidateScanScheduler(candidateScanService, true, true).runScheduledScan())
                .doesNotThrowAnyException();

            verify(candidateScanService).start();
        }
    }
}
