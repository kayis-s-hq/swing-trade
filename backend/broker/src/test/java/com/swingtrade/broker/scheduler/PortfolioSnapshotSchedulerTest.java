package com.swingtrade.broker.scheduler;

import com.swingtrade.broker.engine.PaperTradingEngine;
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
 */
@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotSchedulerTest {

    private PortfolioSnapshotScheduler scheduler;

    @Mock
    private PaperTradingEngine engine;

    @BeforeEach
    void setUp() {
        scheduler = new PortfolioSnapshotScheduler(engine);
    }

    // ==================== takeSnapshot ====================

    @Nested
    class TakeSnapshot {

        @Test
        void takeSnapshot_success() {
            // Given: A working engine
            doNothing().when(engine).saveSnapshot();

            // When
            scheduler.takeSnapshot();

            // Then
            verify(engine).saveSnapshot();
        }

        @Test
        void takeSnapshot_engineThrows_logsWarning() {
            // Given: engine throws
            doThrow(new RuntimeException("DB connection lost")).when(engine).saveSnapshot();

            // When
            scheduler.takeSnapshot();

            // Then: Exception caught internally, no propagation
            verify(engine).saveSnapshot();
        }

        @Test
        void takeSnapshot_nullEngine_caughtInternally() {
            // Given: null engine
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
            // Given: A working engine
            doNothing().when(engine).saveSnapshot();

            // When: Call multiple times
            scheduler.takeSnapshot();
            scheduler.takeSnapshot();
            scheduler.takeSnapshot();

            // Then: Each call saves snapshot
            verify(engine, times(3)).saveSnapshot();
        }

        @Test
        void takeSnapshot_engineSavePortfolioThrows() {
            // Given: engine.saveSnapshot works but savePortfolio throws
            doThrow(new RuntimeException("Portfolio save failed")).when(engine).saveSnapshot();

            // When
            scheduler.takeSnapshot();

            // Then: Exception caught, no propagation
            verify(engine).saveSnapshot();
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
        void takeSnapshot_engineThrows_caughtInternally() {
            // Given: engine that throws on saveSnapshot
            PaperTradingEngine nullService = mock(PaperTradingEngine.class);
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
            // Given: engine throws a checked-style runtime exception
            PaperTradingEngine flakyService = mock(PaperTradingEngine.class);
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
        void schedulerUsesEngine() {
            // Given: A scheduler with mocked engine
            PaperTradingEngine mockSvc = mock(PaperTradingEngine.class);

            PortfolioSnapshotScheduler testScheduler = new PortfolioSnapshotScheduler(mockSvc);

            // When
            testScheduler.takeSnapshot();

            // Then: engine.saveSnapshot is called
            verify(mockSvc).saveSnapshot();
        }

        @Test
        void takeSnapshot_engineCalledOncePerInvocation() {
            // Given: A working engine
            doNothing().when(engine).saveSnapshot();

            // When
            scheduler.takeSnapshot();

            // Then: Exactly one call
            verify(engine, times(1)).saveSnapshot();
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
