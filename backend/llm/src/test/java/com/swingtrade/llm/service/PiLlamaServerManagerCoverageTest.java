package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic coverage for the Pi manager's local lifecycle and status logic.
 *
 * <p>The manager's SSH and HTTP clients are intentionally not exercised here:
 * these tests verify the branches that can be observed without a Pi, a local
 * llama-server, or a network listener.</p>
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class PiLlamaServerManagerCoverageTest {

    private TestSettingsStore settings;
    private PiLlamaServerManager manager;

    @BeforeEach
    void setUp() {
        settings = new TestSettingsStore();
        settings.values.put("llamacpp.model", "/models/test.gguf");
        settings.values.put("llamacpp.bin", "/bin/test-llama-server");
        manager = newManager(settings, 17, 5, 2048);
    }

    @Test
    void initialStatusReportsStoppedAndConfiguredValues() {
        Map<String, Object> status = manager.lifecycleStatus();

        assertThat(status).containsEntry("state", "STOPPED")
                .containsEntry("running", false)
                .containsEntry("startAttempts", 0L)
                .containsEntry("idleStops", 0L)
                .containsEntry("inFlightRequests", 0)
                .containsEntry("modelPath", "/models/test.gguf")
                .containsEntry("readinessMillis", -1L)
                .containsEntry("failureReason", "");
    }

    @Test
    void statusUsesDefaultModelWhenSettingsDoNotContainOne() {
        TestSettingsStore emptySettings = new TestSettingsStore();
        PiLlamaServerManager defaultManager = newManager(emptySettings, 17, 5, 2048);

        assertThat(defaultManager.lifecycleStatus().get("modelPath"))
                .isEqualTo("/home/dietpi/.synapse/models/Qwen3.5-2B_Q4_k_m.gguf");
    }

    @Test
    void ensureRunningReturnsImmediatelyForManagerOwnedRunningServer() throws Exception {
        setField(manager, "running", true);
        setField(manager, "lifecycleState", "READY");

        manager.ensureRunning();

        assertThat(manager.isRunning()).isTrue();
        assertThat(manager.lifecycleStatus()).containsEntry("state", "READY")
                .containsEntry("modelPath", "/models/test.gguf");
        assertThat(manager.getIdleSeconds()).isZero();
    }

    @Test
    void requestLifecycleTracksBusyRequestsAndRefreshesIdleClock() {
        manager.beginRequest();
        manager.beginRequest();

        assertThat(manager.hasInFlightRequests()).isTrue();
        assertThat(manager.lifecycleStatus()).containsEntry("inFlightRequests", 2);

        manager.endRequest();
        assertThat(manager.lifecycleStatus()).containsEntry("inFlightRequests", 1);
        manager.endRequest();
        manager.endRequest(); // The defensive underflow branch.

        assertThat(manager.hasInFlightRequests()).isFalse();
        assertThat(manager.lifecycleStatus()).containsEntry("inFlightRequests", 0);
        assertThat(manager.getIdleSeconds()).isZero();
    }

    @Test
    void statusExposesFailureReasonAndLifecycleCounters() throws Exception {
        setField(manager, "lifecycleState", "FAILED");
        setField(manager, "lastFailureReason", "SSH launch failed");
        setField(manager, "startAttempts", new java.util.concurrent.atomic.AtomicLong(2));
        setField(manager, "idleStops", new java.util.concurrent.atomic.AtomicLong(1));
        setField(manager, "lastReadinessMillis", 321L);

        assertThat(manager.lifecycleStatus()).containsEntry("state", "FAILED")
                .containsEntry("failureReason", "SSH launch failed")
                .containsEntry("startAttempts", 2L)
                .containsEntry("idleStops", 1L)
                .containsEntry("readinessMillis", 321L);
    }

    @Test
    void statusNormalizesMissingFailureReasonToEmptyString() throws Exception {
        setField(manager, "lifecycleState", "FAILED");
        setField(manager, "lastFailureReason", null);

        assertThat(manager.lifecycleStatus()).containsEntry("failureReason", "");
    }

    private static PiLlamaServerManager newManager(AppSettingsStore store,
                                                    int port,
                                                    int idleTimeout,
                                                    int contextSize) {
        return new PiLlamaServerManager(store, "testuser", "pi.invalid", port,
                idleTimeout, 2, contextSize);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestSettingsStore implements AppSettingsStore {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void set(String key, String value) {
            values.put(key, value);
        }
    }
}
