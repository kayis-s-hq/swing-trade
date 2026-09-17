package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deterministic coverage for the local llama-server lifecycle manager.
 *
 * <p>The process handle is seeded only where the existing implementation
 * requires one to exercise stop/status behavior. No child process is started.
 */
class LlamaServerManagerCoverageTest {

    private LlamaCppServerManager manager;

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void reportsStoppedUntilAnAliveProcessIsAssigned() throws Exception {
        manager = manager("/missing/llama-server", 18080, 30);
        assertThat(manager.isRunning()).isFalse();

        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        setProcess(process);

        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void stopGracefullyDestroysAliveProcessAndCancelsItsMonitor() throws Exception {
        manager = manager("/missing/llama-server", 18081, 30);
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(process.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);
        setProcess(process);

        manager.stop();

        verify(process).destroy();
        verify(process).waitFor(10, TimeUnit.SECONDS);
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void stopForciblyDestroysProcessThatDoesNotStopInTime() throws Exception {
        manager = manager("/missing/llama-server", 18082, 30);
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(process.waitFor(10, TimeUnit.SECONDS)).thenReturn(false);
        setProcess(process);

        manager.stop();

        verify(process).destroy();
        verify(process).destroyForcibly();
    }

    @Test
    void stopForciblyDestroysProcessWhenWaitIsInterrupted() throws Exception {
        manager = manager("/missing/llama-server", 18083, 30);
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        doThrow(new InterruptedException()).when(process).waitFor(10, TimeUnit.SECONDS);
        setProcess(process);

        manager.stop();

        verify(process).destroyForcibly();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void requestTrackingIsBalancedAndEndDoesNotUnderflow() {
        manager = manager("/missing/llama-server", 18084, 30);

        manager.endRequest();
        assertThat(manager.hasInFlightRequests()).isFalse();

        manager.beginRequest();
        manager.beginRequest();
        assertThat(manager.hasInFlightRequests()).isTrue();
        manager.endRequest();
        assertThat(manager.hasInFlightRequests()).isTrue();
        manager.endRequest();
        manager.endRequest();
        assertThat(manager.hasInFlightRequests()).isFalse();
        assertThat(manager.getIdleSeconds()).isZero();
    }

    @Test
    void healthCheckReturnsTrueOnlyForHttp200() throws Exception {
        manager = manager("/missing/llama-server", 18085, 30);
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock();
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(HttpClient::newHttpClient).thenReturn(client);
            when(response.statusCode()).thenReturn(200);
            doReturn(response).when(client).send(any(), any());
            assertThat(manager.healthCheck()).isTrue();

            when(response.statusCode()).thenReturn(503);
            assertThat(manager.healthCheck()).isFalse();
        }
    }

    @Test
    void healthCheckReturnsFalseWhenHttpClientFails() throws Exception {
        manager = manager("/missing/llama-server", 18086, 30);
        HttpClient client = mock(HttpClient.class);
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(HttpClient::newHttpClient).thenReturn(client);
            doThrow(new IOException("offline")).when(client).send(any(), any());

            assertThat(manager.healthCheck()).isFalse();
        }
    }

    @Test
    void ensureRunningAdoptsHealthyServerWithoutStartingAProcess() throws Exception {
        manager = manager("/missing/llama-server", 18087, 30);
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock();
        when(response.statusCode()).thenReturn(200);
        doReturn(response).when(client).send(any(), any());

        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(HttpClient::newHttpClient).thenReturn(client);

            manager.ensureRunning();

            assertThat(manager.isRunning()).isFalse();
        }
    }

    @Test
    void ensureRunningWrapsMissingBinaryFailure() {
        manager = manager("/definitely/missing/llama-server", 18088, 30);
        assertThatThrownBy(manager::ensureRunning)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("llama-server failed to start")
                .hasMessageContaining("binary not found");
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void restartWrapsMissingBinaryFailure() {
        manager = manager("/definitely/missing/llama-server", 18089, 30);
        assertThatThrownBy(manager::restart)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("llama-server restart failed")
                .hasMessageContaining("binary not found");
    }

    @Test
    void ensureRunningReportsPortAlreadyInUse() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            manager = manager("/bin/sh", socket.getLocalPort(), 30);
            assertThatThrownBy(manager::ensureRunning)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Port " + socket.getLocalPort())
                    .hasMessageContaining("already in use");
        }
    }

    private LlamaCppServerManager manager(String binary, int port, int timeout) {
        AppSettingsStore settings = mock(AppSettingsStore.class);
        when(settings.get("llamacpp.model")).thenReturn(Optional.of("/test/model.gguf"));
        return new LlamaCppServerManager(settings, binary, port, timeout);
    }

    private void setProcess(Process process) throws Exception {
        Field field = LlamaCppServerManager.class.getDeclaredField("serverProcess");
        field.setAccessible(true);
        field.set(manager, process);
    }
}
