package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the llama-server process lifecycle from Java.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Start llama-server as a child process on first LLM request (lazy start)</li>
 *   <li>Wait for model load before returning</li>
 *   <li>Auto-stop after idle timeout to conserve Pi RAM</li>
 *   <li>Restart when model path changes</li>
 * </ul>
 */
@Service
public class LlamaCppServerManager implements LlmServerManager {

    private static final Logger logger = LoggerFactory.getLogger(LlamaCppServerManager.class);

    private static final String HEALTH_URL = "http://127.0.0.1:%d/health";
    private static final int STARTUP_TIMEOUT_SECONDS = 120;
    private static final int IDLE_CHECK_INTERVAL_SEC = 5;

    private final AppSettingsStore appSettingsStore;

    private final String binPath;
    private final int port;
    private final int idleTimeoutSec;

    private volatile Process serverProcess;
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> idleMonitor = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "llama-idle-monitor");
        t.setDaemon(true);
        return t;
    });

    public LlamaCppServerManager(AppSettingsStore appSettingsStore,
                                 @Value("${llamacpp.bin:/home/dietpi/llama.cpp/build/bin/llama-server}") String binPath,
                                 @Value("${llamacpp.port:8080}") int port,
                                 @Value("${llamacpp.idle-timeout:300}") int idleTimeoutSec) {
        this.appSettingsStore = appSettingsStore;
        this.binPath = binPath;
        this.port = port;
        this.idleTimeoutSec = idleTimeoutSec;
    }

    /**
     * Ensures the llama-server is running. Blocks until the server is ready.
     * If the server is already running, returns immediately.
     *
     * @throws IllegalStateException if the server fails to start
     */
    public void ensureRunning() {
        // Every LLM request calls this first, so it doubles as the "last used"
        // marker — without it the idle clock only ever measured time since
        // startup, and a busy server got stopped mid-request.
        idleCheckTime.set(System.currentTimeMillis());

        if (isRunning()) {
            logger.debug("llama-server already running on port {}", port);
            return;
        }

        // `isRunning()` tracks a Process handle owned by THIS JVM instance — it
        // is null after every app restart even though llama-server is a
        // separate OS process that outlives the JVM that spawned it. Without
        // this check, a restart with a still-alive server from the previous
        // instance would hit startServer()'s "port already in use by another
        // process" IllegalStateException instead of just working.
        if (healthCheck()) {
            logger.info("llama-server already running and healthy on port {} (adopting existing process)", port);
            startIdleMonitor();
            return;
        }

        if (!starting.compareAndSet(false, true)) {
            // Another thread is already starting — wait for it
            logger.debug("Another thread is starting llama-server, waiting...");
            while (!isRunning()) {
                sleepQuietly(500);
            }
            starting.set(false);
            return;
        }

        try {
            logger.info("Starting llama-server on port {} (model: {})", port, getModelPath());
            startServer();
            logger.info("llama-server started successfully on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start llama-server: {}", e.getMessage(), e);
            throw new IllegalStateException("llama-server failed to start: " + e.getMessage(), e);
        } finally {
            starting.set(false);
        }
    }

    /**
     * Stops the llama-server process.
     */
    public void stop() {
        Process p = serverProcess;
        if (p != null && p.isAlive()) {
            logger.info("Stopping llama-server (pid={})", p.pid());
            p.destroy();
            try {
                if (!p.waitFor(10, TimeUnit.SECONDS)) {
                    logger.warn("llama-server did not stop gracefully, forcing...");
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }
        serverProcess = null;
        cancelIdleMonitor();
        logger.info("llama-server stopped");
    }

    /**
     * Checks if the server process is running.
     */
    public boolean isRunning() {
        return serverProcess != null && serverProcess.isAlive();
    }

    /**
     * Restarts the server with the current model from settings.
     * Stops the current process then starts a new one.
     */
    public void restart() {
        logger.info("Restarting llama-server with updated model");
        stop();
        try {
            startServer();
            logger.info("llama-server restarted successfully");
        } catch (Exception e) {
            logger.error("Failed to restart llama-server: {}", e.getMessage(), e);
            throw new IllegalStateException("llama-server restart failed: " + e.getMessage(), e);
        }
    }

    private void startServer() throws Exception {
        String modelPath = getModelPath();

        // Check if binary exists
        File binFile = new File(binPath);
        if (!binFile.exists()) {
            throw new IllegalStateException("llama-server binary not found at: " + binPath);
        }

        // Check if port is already in use by something else
        if (isPortInUse(port) && !isRunning()) {
            throw new IllegalStateException("Port " + port + " is already in use by another process");
        }

        // Build command
        List<String> command = new ArrayList<>();
        command.add(binPath);
        command.add("-m");
        command.add(modelPath);
        command.add("--host");
        command.add("0.0.0.0");
        command.add("--port");
        command.add(String.valueOf(port));
        command.add("-t");
        command.add("4");
        command.add("-c");
        command.add("2048");
        command.add("-b");
        command.add("512");
        command.add("-ub");
        command.add("256");
        command.add("--mlock");
        command.add("--timeout");
        command.add("0");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File("/dev/null"));

        serverProcess = pb.start();
        logger.info("llama-server process started (pid={})", serverProcess.pid());

        // Drain stderr to detect early failures
        try (BufferedReader errReader = new BufferedReader(new InputStreamReader(serverProcess.getErrorStream()))) {
            String line;
            long start = System.currentTimeMillis();
            while ((line = errReader.readLine()) != null) {
                if (line.contains("model loaded")) {
                    logger.info("Model loaded from log output");
                    break;
                }
                if (System.currentTimeMillis() - start > 5000) {
                    logger.debug("Log: {}", line);
                }
            }
        }

        // Wait for health endpoint
        long waited = 0;
        while (waited < STARTUP_TIMEOUT_SECONDS) {
            if (isPortInUse(port)) {
                // Give it a moment more for the HTTP layer to be ready
                sleepQuietly(2000);
                if (healthCheck()) {
                    logger.info("llama-server is healthy after {}s", waited);
                    break;
                }
            }
            sleepQuietly(1000);
            waited++;

            // Check if process died
            if (!serverProcess.isAlive()) {
                int exitCode = serverProcess.exitValue();
                throw new IllegalStateException("llama-server exited with code " + exitCode);
            }
        }

        if (!isRunning() || !healthCheck()) {
            stop();
            throw new IllegalStateException("llama-server failed to become healthy within " + STARTUP_TIMEOUT_SECONDS + "s");
        }

        // Start idle monitor
        startIdleMonitor();
        idleCheckTime.set(System.currentTimeMillis());
    }

    private void startIdleMonitor() {
        cancelIdleMonitor();
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
            if (!isRunning()) return;
            try {
                // Never stop a server that is mid-request. This cannot be inferred
                // from /health — llama.cpp serves several slots and answers health
                // checks happily while one slot is generating, so "responsive" does
                // not mean "idle".
                int active = inFlightRequests.get();
                if (active > 0) {
                    logger.debug("llama-server has {} in-flight request(s), deferring auto-stop", active);
                    return;
                }

                long idleSeconds = getIdleSeconds();
                if (idleSeconds < idleTimeoutSec) {
                    return;
                }

                logger.info("llama-server idle for {}s >= {}s, auto-stopping", idleSeconds, idleTimeoutSec);
                stop();
            } catch (Exception e) {
                logger.debug("Idle monitor check failed: {}", e.getMessage());
            }
        }, IDLE_CHECK_INTERVAL_SEC, IDLE_CHECK_INTERVAL_SEC, TimeUnit.SECONDS);
        idleMonitor.set(future);
    }

    private void cancelIdleMonitor() {
        ScheduledFuture<?> f = idleMonitor.getAndSet(null);
        if (f != null) f.cancel(false);
    }

    @Override
    public void beginRequest() {
        inFlightRequests.incrementAndGet();
        idleCheckTime.set(System.currentTimeMillis());
    }

    @Override
    public void endRequest() {
        inFlightRequests.updateAndGet(n -> n > 0 ? n - 1 : 0);
        idleCheckTime.set(System.currentTimeMillis());
    }

    /** True while at least one request is being served. */
    boolean hasInFlightRequests() {
        return inFlightRequests.get() > 0;
    }

    long getIdleSeconds() {
        // "Last activity" = the most recent ensureRunning()/beginRequest()/
        // endRequest(). llama.cpp doesn't expose a request timestamp, and its
        // /health stays responsive mid-generation, so activity has to be tracked
        // on this side rather than inferred from the server.
        return idleCheckTime.get() > 0 ? (System.currentTimeMillis() - idleCheckTime.get()) / 1000 : 0;
    }

    void setIdleCheckTime(long time) {
        idleCheckTime.set(time);
    }

    int getIdleTimeoutSec() {
        return idleTimeoutSec;
    }

    boolean healthCheck() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(String.format(HEALTH_URL, port)))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String getModelPath() {
        return appSettingsStore.get("llamacpp.model")
                .orElse("/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf");
    }

    private boolean isPortInUse(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Last successful health check timestamp (milliseconds). */
    private final AtomicLong idleCheckTime = new AtomicLong(0);

    // Requests currently being served. The idle monitor refuses to stop the
    // server while this is non-zero (see beginRequest/endRequest).
    private final java.util.concurrent.atomic.AtomicInteger inFlightRequests =
            new java.util.concurrent.atomic.AtomicInteger(0);
}
