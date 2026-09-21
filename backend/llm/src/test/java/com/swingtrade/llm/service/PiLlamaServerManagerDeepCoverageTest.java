package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Deep lifecycle and transport coverage without contacting a Pi or starting a process. */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class PiLlamaServerManagerDeepCoverageTest {

    @Test
    void adoptsAnAlreadyReadyServerAndStartsIdleMonitoring() throws Exception {
        PiLlamaServerManager manager = manager();
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager);
        doReturn(true).when(spy).healthCheck();
        doReturn(true).when(spy).awaitServerReady(45);
        doReturn(true).when(spy).awaitServerStopped(60);

        spy.ensureRunning();

        assertThat(spy.isRunning()).isTrue();
        assertThat(spy.lifecycleStatus()).containsEntry("state", "READY")
                .containsEntry("failureReason", "");
        Process command = mock(Process.class);
        when(command.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);
        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                (builder, context) -> when(builder.start()).thenReturn(command))) {
            spy.stop();
        }
    }

    @Test
    void adoptsAHealthyServerThatIsBusyWithAnotherRequestWithoutPolling() throws Exception {
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager());
        doReturn(true).when(spy).healthCheck();
        doReturn(PiLlamaServerManager.InferenceProbe.BUSY).when(spy).probeInference();

        spy.ensureRunning();

        assertThat(spy.isRunning()).isTrue();
        assertThat(spy.lifecycleStatus()).containsEntry("state", "READY")
                .containsEntry("failureReason", "");
        // A busy server must not trigger the multi-second readiness polling loop.
        org.mockito.Mockito.verify(spy, org.mockito.Mockito.never()).awaitServerReady(45);
    }

    @Test
    void pollsForReadinessWhenAHealthyServerFailsTheProbeOutright() throws Exception {
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager());
        doReturn(true).when(spy).healthCheck();
        doReturn(PiLlamaServerManager.InferenceProbe.NOT_READY).when(spy).probeInference();
        doReturn(true).when(spy).awaitServerReady(45);

        spy.ensureRunning();

        assertThat(spy.isRunning()).isTrue();
        org.mockito.Mockito.verify(spy).awaitServerReady(45);
    }

    @Test
    void rejectsAHealthyButNotInferenceReadyServer() throws Exception {
        PiLlamaServerManager manager = manager();
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager);
        doReturn(true).when(spy).healthCheck();
        doReturn(false).when(spy).awaitServerReady(45);

        assertThatThrownBy(spy::ensureRunning)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not become inference-ready within 45s");
        assertThat(spy.lifecycleStatus()).containsEntry("state", "FAILED")
                .containsEntry("running", false);
    }

    @Test
    void waitsForAnotherStartingThreadToFinish() throws Exception {
        PiLlamaServerManager manager = org.mockito.Mockito.spy(manager());
        doReturn(false).when(manager).healthCheck();
        setField(manager, "starting", new java.util.concurrent.atomic.AtomicBoolean(true));
        Thread waiter = new Thread(manager::ensureRunning);
        waiter.start();
        Thread.sleep(25);
        setField(manager, "running", true);
        waiter.join(2_000);

        assertThat(waiter.isAlive()).isFalse();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void startupUsesConfiguredBinaryAndModelAndWrapsReadinessFailure() throws Exception {
        PiLlamaServerManager manager = manager();
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager);
        doReturn(false).when(spy).healthCheck();
        doReturn(false).when(spy).awaitServerReady(45);
        Process launch = mock(Process.class);
        when(launch.waitFor(15, TimeUnit.SECONDS)).thenReturn(true);
        AtomicReference<java.util.List<?>> constructorArguments = new AtomicReference<>();

        try (MockedConstruction<ProcessBuilder> builders = mockConstruction(ProcessBuilder.class,
                (builder, context) -> {
                    if (constructorArguments.get() == null) {
                        constructorArguments.set(context.arguments());
                    }
                    when(builder.start()).thenReturn(launch);
                })) {
            assertThatThrownBy(spy::ensureRunning)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("llama-server failed to start on Pi")
                    .hasMessageContaining("inference-ready");

            assertThat(builders.constructed()).hasSize(2);
            Object rawArguments = constructorArguments.get().get(0);
            String[] processArguments = (String[]) rawArguments;
            String command = processArguments[2];
            assertThat(command).contains("testuser@pi.invalid")
                    .contains("/bin/test-llama-server")
                    .contains("-m '/models/test.gguf'")
                    .contains("--port 8090")
                    .contains("-t 2")
                    .contains("-c 2048");
        }
    }

    @Test
    void stopSuccessCancelsMonitorAndClearsFailureState() throws Exception {
        PiLlamaServerManager manager = manager();
        setField(manager, "running", true);
        setField(manager, "lifecycleState", "READY");
        setField(manager, "lastFailureReason", "old failure");
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager);
        doReturn(true).when(spy).awaitServerStopped(60);
        Process command = mock(Process.class);
        when(command.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                (builder, context) -> when(builder.start()).thenReturn(command))) {
            spy.stop();
        }

        assertThat(spy.lifecycleStatus()).containsEntry("state", "STOPPED")
                .containsEntry("running", false)
                .containsEntry("failureReason", "");
    }

    @Test
    void stopFailureRecordsFailureAndReflectsReachableServer() throws Exception {
        PiLlamaServerManager manager = manager();
        setField(manager, "running", true);
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager);
        doReturn(true).when(spy).healthCheck();

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                (builder, context) -> doThrow(new IOException("ssh unavailable")).when(builder).start())) {
            spy.stop();
        }

        assertThat(spy.lifecycleStatus()).containsEntry("state", "FAILED")
                .containsEntry("running", true)
                .containsEntry("failureReason", "ssh unavailable");
    }

    @Test
    void restartStopsThenStartsWithNoRealProcess() throws Exception {
        PiLlamaServerManager manager = manager();
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager);
        doReturn(true).when(spy).awaitServerStopped(60);
        doReturn(true).when(spy).awaitServerReady(45);
        Process command = mock(Process.class);
        when(command.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                (builder, context) -> when(builder.start()).thenReturn(command))) {
            spy.restart();
        }

        assertThat(spy.lifecycleStatus()).containsEntry("state", "READY")
                .containsEntry("running", true);
    }

    @Test
    void restartAbortsWhenStopCannotBeConfirmed() throws Exception {
        PiLlamaServerManager manager = manager();
        PiLlamaServerManager spy = org.mockito.Mockito.spy(manager);
        doReturn(true).when(spy).healthCheck();

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                (builder, context) -> doThrow(new IOException("ssh unavailable")).when(builder).start())) {
            assertThatThrownBy(spy::restart)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("restart aborted")
                    .hasMessageContaining("ssh unavailable");
        }
    }

    @Test
    void inferenceConnectionHandlesSuccessfulEmptyAndFailedResponses() throws Exception {
        assertThat(inferenceResult(true, "{\"choices\":[{}]}", false)).isTrue();
        assertThat(inferenceResult(true, "", true)).isFalse();
        assertThat(inferenceResult(false, "{\"choices\":[{}]}", false)).isFalse();
    }

    @Test
    void inferenceConnectionReturnsFalseWhenTransportThrows() throws Exception {
        Call call = mock(Call.class);
        when(call.execute()).thenThrow(new IOException("offline"));
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any())).thenReturn(call);
        try (MockedConstruction<OkHttpClient.Builder> ignored = builderReturning(client)) {
            assertThat(manager().testInferenceConnection()).isFalse();
        }
    }

    @Test
    void healthCheckMapsHttpStatusAndTransportFailure() throws Exception {
        PiLlamaServerManager manager = manager();
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock();
        try (MockedStatic<HttpClient> clients = mockStatic(HttpClient.class)) {
            clients.when(HttpClient::newHttpClient).thenReturn(client);
            when(response.statusCode()).thenReturn(200);
            doReturn(response).when(client).send(any(), any());
            assertThat(manager.healthCheck()).isTrue();
            when(response.statusCode()).thenReturn(503);
            assertThat(manager.healthCheck()).isFalse();
            doThrow(new IOException("offline")).when(client).send(any(), any());
            assertThat(manager.healthCheck()).isFalse();
        }
    }

    @Test
    void zeroLengthWaitsUseFinalHealthState() throws Exception {
        PiLlamaServerManager manager = org.mockito.Mockito.spy(manager());
        doReturn(false).when(manager).healthCheck();
        assertThat(manager.awaitServerReady(0)).isFalse();
        assertThat(manager.awaitServerStopped(0)).isTrue();
    }

    private static MockedConstruction<OkHttpClient.Builder> builderReturning(OkHttpClient client) {
        return mockConstruction(OkHttpClient.Builder.class, (builder, context) -> {
            when(builder.callTimeout(anyLong(), any(TimeUnit.class))).thenReturn(builder);
            when(builder.build()).thenReturn(client);
        });
    }

    private boolean inferenceResult(boolean successful, String body, boolean nullBody) throws Exception {
        Call call = mock(Call.class);
        Response response = mock(Response.class);
        when(response.isSuccessful()).thenReturn(successful);
        when(response.body()).thenReturn(nullBody ? null : ResponseBody.create(body,
                okhttp3.MediaType.get("application/json")));
        when(call.execute()).thenReturn(response);
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any())).thenReturn(call);
        try (MockedConstruction<OkHttpClient.Builder> ignored = builderReturning(client)) {
            return manager().testInferenceConnection();
        }
    }

    private static PiLlamaServerManager manager() {
        Settings settings = new Settings();
        settings.values.put("llamacpp.model", "/models/test.gguf");
        settings.values.put("llamacpp.bin", "/bin/test-llama-server");
        return new PiLlamaServerManager(settings, "testuser", "pi.invalid", 8090, 17, 2, 2048);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Settings implements AppSettingsStore {
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
