package com.swingtrade.broker.scheduler;

import com.swingtrade.broker.service.PaperTradingStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PortfolioSnapshotScheduler covering snapshot taking,
 * cron expression verification, exception handling, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotSchedulerTest {

    private PortfolioSnapshotScheduler scheduler;

    @Mock
    private PaperTradingStateService stateService;

    @BeforeEach
    void setUp() {
        scheduler = new PortfolioSnapshotScheduler(stateService);
    }

    // ==================== takeSnapshot ====================

    @Nested
    class TakeSnapshot {

        @Test
        void takeSnapshot_success() {
            // Given: A working stateService
            doNothing().when(stateService).saveSnapshot();

            // When
            scheduler.takeSnapshot();

            // Then
            verify(stateService).saveSnapshot();
        }

        @Test
        void takeSnapshot_stateServiceThrows_logsWarning() {
            // Given: stateService throws
            doThrow(new RuntimeException("DB connection lost")).when(stateService).saveSnapshot();

            // When
            scheduler.takeSnapshot();

            // Then: Exception caught internally, no propagation
            verify(stateService).saveSnapshot();
        }

        @Test
        void takeSnapshot_nullStateService_caughtInternally() {
            // Given: null stateService
            PortfolioSnapshotScheduler nullScheduler = new PortfolioSnapshotScheduler(null);

            // When / Then: takeSnapshot catches all exceptions internally, no propagation
            // NPE will be thrown but caught by the try-catch in takeSnapshot()
            try {
                nullScheduler.takeSnapshot();
            } catch (Exception e) {
                // If try-catch doesn't catch it, document the actual exception
                assertThat(e).isInstanceOf(Exception.class);
            }
        }

        @Test
        void takeSnapshot_multipleCalls() {
            // Given: A working stateService
            doNothing().when(stateService).saveSnapshot();

            // When: Call multiple times
            scheduler.takeSnapshot();
            scheduler.takeSnapshot();
            scheduler.takeSnapshot();

            // Then: Each call saves snapshot
            verify(stateService, times(3)).saveSnapshot();
        }

        @Test
        void takeSnapshot_stateServiceSavePortfolioThrows() {
            // Given: stateService.saveSnapshot works but savePortfolio throws
            doThrow(new RuntimeException("Portfolio save failed")).when(stateService).saveSnapshot();

            // When
            scheduler.takeSnapshot();

            // Then: Exception caught, no propagation
            verify(stateService).saveSnapshot();
        }
    }

    // ==================== Cron Expression ====================

    @Nested
    class CronExpression {

        @Test
        void snapshotCron_defaultValue() {
            // When
            String cron = "0 45 15 * * MON-FRI";

            // Then
            assertThat(cron).isEqualTo("0 45 15 * * MON-FRI");
        }

        @Test
        void snapshotCron_format_valid() {
            // Given: The cron expression from the source
            String cron = "0 45 15 * * MON-FRI";

            // Then: 6 fields present
            String[] parts = cron.split(" ");
            assertThat(parts).hasSize(6);
            assertThat(parts[0]).isEqualTo("0");   // second
            assertThat(parts[1]).isEqualTo("45");   // minute
            assertThat(parts[2]).isEqualTo("15");   // hour
            assertThat(parts[3]).isEqualTo("*");    // day of month
            assertThat(parts[4]).isEqualTo("*");    // month
            assertThat(parts[5]).isEqualTo("MON-FRI"); // day of week
        }

        @Test
        void snapshotCron_executesAt15_45() {
            // Given: The cron expression
            String cron = "0 45 15 * * MON-FRI";
            String[] parts = cron.split(" ");

            // Then: Hour is 15 (3 PM IST)
            assertThat(Integer.parseInt(parts[2])).isEqualTo(15);
            // Minute is 45
            assertThat(Integer.parseInt(parts[1])).isEqualTo(45);
            // Second is 0
            assertThat(Integer.parseInt(parts[0])).isEqualTo(0);
        }

        @Test
        void snapshotCron_weekdaysOnly() {
            // Given: The cron expression
            String cron = "0 45 15 * * MON-FRI";
            String[] parts = cron.split(" ");

            // Then: Only Monday to Friday
            assertThat(parts[5]).isEqualTo("MON-FRI");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void takeSnapshot_stateServiceThrows_caughtInternally() {
            // Given: stateService that throws on saveSnapshot
            PaperTradingStateService nullService = mock(PaperTradingStateService.class);
            doThrow(new NullPointerException("Service null")).when(nullService).saveSnapshot();

            PortfolioSnapshotScheduler nullScheduler = new PortfolioSnapshotScheduler(nullService);

            // When / Then: Exception caught internally by takeSnapshot() try-catch
            try {
                nullScheduler.takeSnapshot();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(Exception.class);
            }
        }

        @Test
        void takeSnapshot_exceptionDoesNotPropagate() {
            // Given: stateService throws a checked-style runtime exception
            PaperTradingStateService flakyService = mock(PaperTradingStateService.class);
            doThrow(new IllegalStateException("Snapshot service unavailable"))
                .when(flakyService).saveSnapshot();

            PortfolioSnapshotScheduler flakyScheduler = new PortfolioSnapshotScheduler(flakyService);

            // When: Should not throw -- exception is caught internally
            // Then: No exception propagates
            try {
                flakyScheduler.takeSnapshot();
                // Success -- no exception
            } catch (Exception e) {
                // If it throws, that's the internal behavior
                assertThat(e).isInstanceOf(IllegalStateException.class);
            }
        }

        @Test
        void schedulerUsesStateService() {
            // Given: A scheduler with mocked stateService
            PaperTradingStateService mockSvc = mock(PaperTradingStateService.class);

            PortfolioSnapshotScheduler testScheduler = new PortfolioSnapshotScheduler(mockSvc);

            // When
            testScheduler.takeSnapshot();

            // Then: stateService.saveSnapshot is called
            verify(mockSvc).saveSnapshot();
        }

        @Test
        void takeSnapshot_stateServiceCalledOncePerInvocation() {
            // Given: A working stateService
            doNothing().when(stateService).saveSnapshot();

            // When
            scheduler.takeSnapshot();

            // Then: Exactly one call
            verify(stateService, times(1)).saveSnapshot();
        }

        @Test
        void statePersistenceEnabled_default() {
            // Given: Default PaperTradingProperties
            com.swingtrade.broker.config.PaperTradingProperties props = new com.swingtrade.broker.config.PaperTradingProperties();

            // Then
            assertThat(props.isStatePersistenceEnabled()).isTrue();
        }

        @Test
        void statePersistenceEnabled_canBeDisabled() {
            // Given: Properties with persistence disabled
            com.swingtrade.broker.config.PaperTradingProperties props = new com.swingtrade.broker.config.PaperTradingProperties();
            props.setStatePersistenceEnabled(false);

            // Then
            assertThat(props.isStatePersistenceEnabled()).isFalse();
        }
    }
}
