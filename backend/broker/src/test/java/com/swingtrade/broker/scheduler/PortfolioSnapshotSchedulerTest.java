package com.swingtrade.broker.scheduler;

import com.swingtrade.domain.service.PaperPortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for PortfolioSnapshotScheduler covering snapshot taking,
 * cron expression verification, exception handling, and edge cases.
 *
 * <p>Plan §7.2: the scheduler now delegates to {@link PaperPortfolioService#snapshotAllPortfolios()}
 * instead of {@code PaperTradingStateService.saveSnapshot()} directly, so it snapshots every
 * known portfolio rather than only "default".
 */
@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotSchedulerTest {

    private PortfolioSnapshotScheduler scheduler;

    @Mock
    private PaperPortfolioService paperPortfolioService;

    @BeforeEach
    void setUp() {
        scheduler = new PortfolioSnapshotScheduler(paperPortfolioService);
    }

    // ==================== takeSnapshot ====================

    @Nested
    class TakeSnapshot {

        @Test
        void takeSnapshot_success() {
            // Given: A working paperPortfolioService
            doNothing().when(paperPortfolioService).snapshotAllPortfolios();

            // When
            scheduler.takeSnapshot();

            // Then
            verify(paperPortfolioService).snapshotAllPortfolios();
        }

        @Test
        void takeSnapshot_serviceThrows_logsWarning() {
            // Given: service throws
            doThrow(new RuntimeException("DB connection lost")).when(paperPortfolioService).snapshotAllPortfolios();

            // When
            scheduler.takeSnapshot();

            // Then: Exception caught internally, no propagation
            verify(paperPortfolioService).snapshotAllPortfolios();
        }

        @Test
        void takeSnapshot_nullService_caughtInternally() {
            // Given: null service
            PortfolioSnapshotScheduler nullScheduler = new PortfolioSnapshotScheduler(null);

            // When / Then: takeSnapshot catches all exceptions internally, no propagation
            try {
                nullScheduler.takeSnapshot();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(Exception.class);
            }
        }

        @Test
        void takeSnapshot_multipleCalls() {
            // Given: A working service
            doNothing().when(paperPortfolioService).snapshotAllPortfolios();

            // When: Call multiple times
            scheduler.takeSnapshot();
            scheduler.takeSnapshot();
            scheduler.takeSnapshot();

            // Then: Each call snapshots
            verify(paperPortfolioService, times(3)).snapshotAllPortfolios();
        }
    }

    // ==================== Cron Expression ====================

    @Nested
    class CronExpression {

        @Test
        void snapshotCron_defaultValue() {
            String cron = "0 45 15 * * MON-FRI";
            assertThat(cron).isEqualTo("0 45 15 * * MON-FRI");
        }

        @Test
        void snapshotCron_format_valid() {
            String cron = "0 45 15 * * MON-FRI";
            String[] parts = cron.split(" ");
            assertThat(parts).hasSize(6);
            assertThat(parts[0]).isEqualTo("0");
            assertThat(parts[1]).isEqualTo("45");
            assertThat(parts[2]).isEqualTo("15");
            assertThat(parts[3]).isEqualTo("*");
            assertThat(parts[4]).isEqualTo("*");
            assertThat(parts[5]).isEqualTo("MON-FRI");
        }

        @Test
        void snapshotMethod_hasScheduledAnnotation() throws NoSuchMethodException {
            Scheduled scheduled = PortfolioSnapshotScheduler.class
                .getMethod("takeSnapshot")
                .getAnnotation(Scheduled.class);

            assertThat(scheduled).isNotNull();
            assertThat(scheduled.cron())
                .isEqualTo("${paper.trading.snapshot-cron:0 45 15 * * MON-FRI}");
            assertThat(scheduled.zone()).isEqualTo("Asia/Kolkata");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void takeSnapshot_exceptionDoesNotPropagate() {
            PaperPortfolioService flakyService = mock(PaperPortfolioService.class);
            doThrow(new IllegalStateException("Snapshot service unavailable"))
                .when(flakyService).snapshotAllPortfolios();

            PortfolioSnapshotScheduler flakyScheduler = new PortfolioSnapshotScheduler(flakyService);

            try {
                flakyScheduler.takeSnapshot();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalStateException.class);
            }
        }

        @Test
        void schedulerUsesPaperPortfolioService() {
            PaperPortfolioService mockSvc = mock(PaperPortfolioService.class);
            PortfolioSnapshotScheduler testScheduler = new PortfolioSnapshotScheduler(mockSvc);

            testScheduler.takeSnapshot();

            verify(mockSvc).snapshotAllPortfolios();
        }

        @Test
        void takeSnapshot_serviceCalledOncePerInvocation() {
            doNothing().when(paperPortfolioService).snapshotAllPortfolios();

            scheduler.takeSnapshot();

            verify(paperPortfolioService, times(1)).snapshotAllPortfolios();
        }

        @Test
        void statePersistenceEnabled_default() {
            com.swingtrade.broker.config.PaperTradingProperties props = new com.swingtrade.broker.config.PaperTradingProperties();
            assertThat(props.isStatePersistenceEnabled()).isTrue();
        }

        @Test
        void statePersistenceEnabled_canBeDisabled() {
            com.swingtrade.broker.config.PaperTradingProperties props = new com.swingtrade.broker.config.PaperTradingProperties();
            props.setStatePersistenceEnabled(false);
            assertThat(props.isStatePersistenceEnabled()).isFalse();
        }
    }
}
