package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Additional lifecycle, configuration, and HTTP coverage without starting a process or using the network. */
class LlamaServerManagerDeepCoverageTest {

    private LlamaCppServerManager manager;

    @AfterEach
    void stopManager() {
        if (manager != null) {
            manager.stop();
        }
        Thread.interrupted();
    }

    @Test
    void startsWithConfiguredArgumentsAndRegistersIdleMonitor() throws Exception {
        int configuredPort = -1;
        {
            manager = manager("/bin/sh", configuredPort, "/models/custom.gguf");
            LlamaCppServerManager spy = org.mockito.Mockito.spy(manager);
            doReturn(true).when(spy).healthCheck();
            Process process = process(true, "model loaded\n");
            java.util.concurrent.atomic.AtomicReference<List<String>> command = new java.util.concurrent.atomic.AtomicReference<>();
            setProcess(spy, process);

            try (MockedConstruction<ProcessBuilder> builders = mockProcessBuilder(process, command)) {
                invokeStartServer(spy);

                assertThat(spy.isRunning()).isTrue();
                assertThat(field(spy, "idleMonitor")).isNotNull();
                assertThat(command.get()).containsExactly(
                        "/bin/sh", "-m", "/models/custom.gguf", "--host", "0.0.0.0",
                        "--port", String.valueOf(configuredPort), "-t", "4", "-c", "2048",
                        "-b", "512", "-ub", "256", "--mlock", "--timeout", "0");
            }
        }
    }

    @Test
    void usesDefaultModelWhenSettingsHaveNoModel() throws Exception {
        {
            manager = manager("/bin/sh", -1, null);
            LlamaCppServerManager spy = org.mockito.Mockito.spy(manager);
            doReturn(true).when(spy).healthCheck();
            java.util.concurrent.atomic.AtomicReference<List<String>> command = new java.util.concurrent.atomic.AtomicReference<>();
            setProcess(spy, process(true, ""));

            try (MockedConstruction<ProcessBuilder> ignored = mockProcessBuilder(process(true, "model loaded\n"), command)) {
                invokeStartServer(spy);

                assertThat(command.get())
                        .contains("/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf");
            }
        }
    }

    @Test
    void restartStopsOldProcessAndStartsUsingCurrentConfiguration() throws Exception {
        {
            manager = manager("/bin/sh", -1, "/models/restarted.gguf");
            Process oldProcess = process(true, "");
            setProcess(oldProcess);
            when(oldProcess.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);
            LlamaCppServerManager spy = org.mockito.Mockito.spy(manager);
            doReturn(true).when(spy).healthCheck();
            doNothing().when(spy).stop();
            setProcess(spy, oldProcess);
            java.util.concurrent.atomic.AtomicReference<List<String>> command = new java.util.concurrent.atomic.AtomicReference<>();

            try (MockedConstruction<ProcessBuilder> builders = mockProcessBuilder(process(true, "model loaded\n"), command)) {
                spy.restart();

                verify(spy).stop();
                assertThat(spy.isRunning()).isTrue();
                assertThat(builders.constructed()).hasSize(1);
                assertThat(command.get()).contains("/models/restarted.gguf");
            }
        }
    }

    @Test
    void wrapsProcessExitDuringStartupWithExitCode() throws Exception {
        manager = manager("/bin/sh", -1, "/models/test.gguf");
        LlamaCppServerManager spy = org.mockito.Mockito.spy(manager);
        doNothing().when(spy).stop();
        setProcess(spy, process(true, ""));
        Process process = process(false, "");
        when(process.exitValue()).thenReturn(23);

        try (MockedConstruction<ProcessBuilder> ignored = mockProcessBuilder(process, new java.util.concurrent.atomic.AtomicReference<>())) {
            assertThatThrownBy(spy::restart)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("llama-server restart failed")
                    .hasMessageContaining("llama-server exited with code 23");
        }
        assertThat(spy.isRunning()).isFalse();
    }

    @Test
    void concurrentEnsureRunningWaitsForTheStartingThread() throws Exception {
        manager = manager("/missing/llama-server", -1, "/models/test.gguf");
        LlamaCppServerManager spy = org.mockito.Mockito.spy(manager);
        doReturn(false).when(spy).healthCheck();
        setField(spy, "starting", new AtomicBoolean(true));
        Process process = process(true, "");
        Thread waiter = new Thread(spy::ensureRunning);

        waiter.start();
        Thread.sleep(30);
        setProcess(spy, process);
        waiter.join(2_000);

        assertThat(waiter.isAlive()).isFalse();
        assertThat(spy.isRunning()).isTrue();
    }

    @Test
    void stopClearsADeadProcessWithoutTryingToDestroyIt() throws Exception {
        manager = manager("/missing/llama-server", -1, "/models/test.gguf");
        Process process = process(false, "");
        setProcess(process);

        manager.stop();

        org.mockito.Mockito.verify(process, org.mockito.Mockito.never()).destroy();
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void healthCheckBuildsLocalGetRequestWithThreeSecondTimeout() throws Exception {
        manager = manager("/missing/llama-server", 18123, "/models/test.gguf");
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock();
        when(response.statusCode()).thenReturn(200);
        doReturn(response).when(client).send(any(), any());

        try (MockedStatic<HttpClient> clients = mockStatic(HttpClient.class)) {
            clients.when(HttpClient::newHttpClient).thenReturn(client);

            assertThat(manager.healthCheck()).isTrue();

            ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
            verify(client).send(request.capture(), any());
            assertThat(request.getValue().uri().toString()).isEqualTo("http://127.0.0.1:18123/health");
            assertThat(request.getValue().method()).isEqualTo("GET");
            assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(3));
        }
    }

    private MockedConstruction<ProcessBuilder> mockProcessBuilder(
            Process process, java.util.concurrent.atomic.AtomicReference<List<String>> command) {
        return mockConstruction(ProcessBuilder.class, (builder, context) -> {
            @SuppressWarnings("unchecked")
            List<String> arguments = (List<String>) context.arguments().get(0);
            command.set(arguments);
            when(builder.redirectErrorStream(anyBoolean())).thenReturn(builder);
            when(builder.redirectOutput(any(File.class))).thenReturn(builder);
            when(builder.start()).thenReturn(process);
        });
    }

    private Process process(boolean alive, String output) {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(alive);
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(output.getBytes()));
        return process;
    }

    private LlamaCppServerManager manager(String binary, int port, String model) {
        AppSettingsStore settings = mock(AppSettingsStore.class);
        when(settings.get("llamacpp.model")).thenReturn(Optional.ofNullable(model));
        return new LlamaCppServerManager(settings, binary, port, 30);
    }

    private Object field(Object target, String name) throws Exception {
        Field field = LlamaCppServerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setProcess(Process process) throws Exception {
        setProcess(manager, process);
    }

    private void setProcess(LlamaCppServerManager target, Process process) throws Exception {
        setField(target, "serverProcess", process);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = LlamaCppServerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void invokeStartServer(LlamaCppServerManager target) throws Exception {
        java.lang.reflect.Method method = LlamaCppServerManager.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        method.invoke(target);
    }
}
