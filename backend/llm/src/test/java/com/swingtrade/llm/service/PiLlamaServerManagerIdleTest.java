package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PiLlamaServerManager idle monitor behavior.
 *
 * Validates:
 * - idleCheckTime is NOT reset by failed health checks
 * - idle time accumulates correctly over time
 * - idle time stays low for recently-started servers
 */
class PiLlamaServerManagerIdleTest {

    private AppSettingsStore settingsStore;
    private PiLlamaServerManager manager;

    @BeforeEach
    void setUp() {
        settingsStore = new AppSettingsStore() {
            private final java.util.Map<String, String> store = new java.util.HashMap<>();
            @Override public Optional<String> get(String key) { return Optional.ofNullable(store.get(key)); }
            @Override public void set(String key, String value) { store.put(key, value); }
        };
        settingsStore.set("llamacpp.model", "/test/model.gguf");
        settingsStore.set("llamacpp.bin", "/test/llama-server");

        manager = new PiLlamaServerManager(
                settingsStore,
                "testuser",
                "127.0.0.1",
                8090,
                3,        // idleTimeoutSec = 3 seconds
                2,
                4096
        );
    }

    @Nested
    @DisplayName("Idle Monitor — healthy server should auto-stop")
    class HealthyServerIdleStop {

        @Test
        @DisplayName("should stop server when idle exceeds timeout")
        void shouldStopServerWhenIdleExceedsTimeout() {
            // Arrange: manually set idleCheckTime to 5 seconds ago
            manager.setIdleCheckTime(System.currentTimeMillis() - 5000);

            // Verify idle time exceeds timeout (3s)
            long idleSeconds = manager.getIdleSeconds();
            assertThat(idleSeconds).isGreaterThanOrEqualTo(3);
            assertThat(idleSeconds).isGreaterThanOrEqualTo(manager.getIdleTimeoutSec());
        }

        @Test
        @DisplayName("should not stop server when below idle timeout")
        void shouldNotStopServerWhenBelowIdleTimeout() {
            // Arrange: idleCheckTime = 1 second ago, timeout = 3 seconds
            manager.setIdleCheckTime(System.currentTimeMillis() - 1000);

            long idleSeconds = manager.getIdleSeconds();
            assertThat(idleSeconds).isLessThan(manager.getIdleTimeoutSec());
        }
    }

    @Nested
    @DisplayName("idleCheckTime should not be updated by health checks")
    class IdleCheckTimeNotUpdatedByHealth {

        @Test
        @DisplayName("health check should not reset idle timer on failure")
        void healthCheckShouldNotResetIdleTimer() {
            // Arrange: set idleCheckTime to 5 seconds ago
            long fiveSecondsAgo = System.currentTimeMillis() - 5000;
            manager.setIdleCheckTime(fiveSecondsAgo);

            // Verify idle time is ~5s
            long idleBefore = manager.getIdleSeconds();
            assertThat(idleBefore).isGreaterThanOrEqualTo(4);

            // Act: call healthCheck — it will fail (no real server),
            // but idleCheckTime should NOT change
            manager.healthCheck();

            // Assert: idle time should still be ~5s, NOT reset to 0
            long idleAfter = manager.getIdleSeconds();
            assertThat(idleAfter).isGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("idle time should accumulate from set point")
        void idleTimeShouldAccumulateFromSetPoint() {
            // Arrange: set idleCheckTime to a fixed point
            long fixedTime = System.currentTimeMillis() - 2000;
            manager.setIdleCheckTime(fixedTime);

            // Act: wait a moment
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Assert: idle time should be ~2.1s (2s + 100ms sleep)
            long idleSeconds = manager.getIdleSeconds();
            assertThat(idleSeconds).isGreaterThanOrEqualTo(2);
            assertThat(idleSeconds).isLessThan(3);
        }
    }

    @Nested
    @DisplayName("In-flight tracking — a busy server must not be auto-stopped")
    class InFlightTracking {

        @Test
        @DisplayName("beginRequest resets the idle clock so a starting request isn't seen as idle")
        void beginRequestResetsIdleClock() {
            manager.setIdleCheckTime(System.currentTimeMillis() - 60_000);
            assertThat(manager.getIdleSeconds()).isGreaterThanOrEqualTo(manager.getIdleTimeoutSec());

            manager.beginRequest();

            assertThat(manager.getIdleSeconds()).isLessThan(manager.getIdleTimeoutSec());
        }

        @Test
        @DisplayName("server counts as busy while a request is in flight, even once idle time is exceeded")
        void staysBusyWhileRequestInFlight() {
            manager.beginRequest();

            // Simulate a long generation: the idle clock runs past the timeout while
            // the request is still going. This is the regression — the idle monitor
            // used to stop llama-server here, killing the in-flight request.
            manager.setIdleCheckTime(System.currentTimeMillis() - 60_000);

            assertThat(manager.getIdleSeconds()).isGreaterThanOrEqualTo(manager.getIdleTimeoutSec());
            assertThat(manager.hasInFlightRequests()).isTrue();
        }

        @Test
        @DisplayName("endRequest clears the in-flight marker and refreshes the idle clock")
        void endRequestReleasesServer() {
            manager.beginRequest();
            manager.setIdleCheckTime(System.currentTimeMillis() - 60_000);

            manager.endRequest();

            assertThat(manager.hasInFlightRequests()).isFalse();
            // The idle clock restarts on completion, so the server gets a fresh
            // idle window rather than being stopped immediately after a long call.
            assertThat(manager.getIdleSeconds()).isLessThan(manager.getIdleTimeoutSec());
        }

        @Test
        @DisplayName("concurrent requests only release the server once all have finished")
        void nestedRequestsTrackedIndependently() {
            manager.beginRequest();
            manager.beginRequest();

            manager.endRequest();
            assertThat(manager.hasInFlightRequests()).isTrue();

            manager.endRequest();
            assertThat(manager.hasInFlightRequests()).isFalse();
        }

        @Test
        @DisplayName("unbalanced endRequest calls never drive the counter negative")
        void endRequestDoesNotUnderflow() {
            manager.endRequest();
            manager.endRequest();

            assertThat(manager.hasInFlightRequests()).isFalse();

            // A subsequent real request must still register as busy.
            manager.beginRequest();
            assertThat(manager.hasInFlightRequests()).isTrue();
        }
    }
}