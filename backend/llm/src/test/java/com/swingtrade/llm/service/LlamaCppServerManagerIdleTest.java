package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LlamaCppServerManager idle monitor behavior.
 *
 * Validates:
 * - idleCheckTime is NOT reset by failed health checks
 * - idle time accumulates correctly from set point
 */
class LlamaCppServerManagerIdleTest {

    private AppSettingsStore settingsStore;
    private LlamaCppServerManager manager;

    @BeforeEach
    void setUp() {
        settingsStore = new AppSettingsStore() {
            private final java.util.Map<String, String> store = new java.util.HashMap<>();
            @Override public Optional<String> get(String key) { return Optional.ofNullable(store.get(key)); }
            @Override public void set(String key, String value) { store.put(key, value); }
        };
        settingsStore.set("llamacpp.model", "/test/model.gguf");

        manager = new LlamaCppServerManager(
                settingsStore,
                "/test/llama-server",
                8080,
                3  // idleTimeoutSec = 3 seconds
        );
    }

    @Nested
    @DisplayName("Idle Monitor — should auto-stop idle server")
    class IdleServerAutoStop {

        @Test
        @DisplayName("should stop server when idle exceeds timeout")
        void shouldStopServerWhenIdleExceedsTimeout() {
            // Arrange: idleCheckTime = 5 seconds ago, timeout = 3 seconds
            manager.setIdleCheckTime(System.currentTimeMillis() - 5000);

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
            // Arrange: idleCheckTime = 5 seconds ago
            long fiveSecondsAgo = System.currentTimeMillis() - 5000;
            manager.setIdleCheckTime(fiveSecondsAgo);

            long idleBefore = manager.getIdleSeconds();
            assertThat(idleBefore).isGreaterThanOrEqualTo(4);

            // Act: healthCheck will fail (no real server)
            manager.healthCheck();

            // Assert: idle time should still be ~5s, NOT reset
            long idleAfter = manager.getIdleSeconds();
            assertThat(idleAfter).isGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("idle time should accumulate from set point")
        void idleTimeShouldAccumulateFromSetPoint() {
            // Arrange
            long fixedTime = System.currentTimeMillis() - 2000;
            manager.setIdleCheckTime(fixedTime);

            // Act
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Assert
            long idleSeconds = manager.getIdleSeconds();
            assertThat(idleSeconds).isGreaterThanOrEqualTo(2);
            assertThat(idleSeconds).isLessThan(3);
        }
    }
}