package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages llama-server on a remote Pi via SSH.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Start llama-server on Pi via SSH (lazy start on first request)</li>
 *   <li>Wait for model load via health endpoint polling</li>
 *   <li>Auto-stop after idle timeout to conserve Pi RAM</li>
 *   <li>Restart when model path changes</li>
 * </ul>
 */
@Service
public class PiLlamaServerManager implements LlmServerManager {

    private static final Logger logger = LoggerFactory.getLogger(PiLlamaServerManager.class);
    private static final int STARTUP_TIMEOUT_SECONDS = 45;
    private static final int STOP_TIMEOUT_SECONDS = 60;
    private static final int IDLE_CHECK_INTERVAL_SEC = 10;

    private final AppSettingsStore appSettingsStore;
    private final String sshUser;
    private final String sshHost;
    private final int port;
    private final int idleTimeoutSec;
    private final int threads;
    private final int contextSize;

    private volatile boolean running = false;
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> idleMonitor = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pi-llama-idle-monitor");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong idleCheckTime = new AtomicLong(0);
    private final AtomicLong startAttempts = new AtomicLong(0);
    private final AtomicLong idleStops = new AtomicLong(0);
    private volatile String lifecycleState = "STOPPED";
    private volatile String lastFailureReason;
    private volatile long lastReadinessMillis = -1;

    // Requests currently being served. The idle monitor refuses to stop the
    // server while this is non-zero (see beginRequest/endRequest).
    private final java.util.concurrent.atomic.AtomicInteger inFlightRequests =
            new java.util.concurrent.atomic.AtomicInteger(0);

    public PiLlamaServerManager(AppSettingsStore appSettingsStore,
                                @Value("${llamacpp.ssh.user:dietpi}") String sshUser,
                                @Value("${llamacpp.ssh.host:192.168.0.100}") String sshHost,
                                @Value("${llamacpp.port:8090}") int port,
                                @Value("${llamacpp.idle-timeout:3600}") int idleTimeoutSec,
                                @Value("${llamacpp.threads:2}") int threads,
                                @Value("${llamacpp.context:4096}") int contextSize) {
        this.appSettingsStore = appSettingsStore;
        this.sshUser = sshUser;
        this.sshHost = sshHost;
        this.port = port;
        this.idleTimeoutSec = idleTimeoutSec;
        this.threads = threads;
        this.contextSize = contextSize;
    }

    @Override
    public void ensureRunning() {
        // Every LLM request calls this first, so it doubles as the "last used"
        // marker — without it the idle clock only ever measured time since
        // startup, and a busy server got stopped mid-request.
        idleCheckTime.set(System.currentTimeMillis());

        if (isRunning()) {
            logger.debug("llama-server already running on {} port {}", sshHost, port);
            return;
        }

        // `running` only reflects what THIS JVM instance itself started — it
        // resets to false on every app restart, even though llama-server is a
        // detached ("nohup ... & disown") process on the Pi that keeps running
        // across app restarts. Without checking the real Pi state here, every
        // restart re-attempted a fresh SSH launch that failed to bind the
        // already-used port, silently masked because the health poll right
        // after found the pre-existing server healthy anyway — wasted, noisy
        // "Starting llama-server..." + bind-failure log churn on every restart.
        if (healthCheck()) {
            if (!awaitServerReady(STARTUP_TIMEOUT_SECONDS)) {
                throw new IllegalStateException("llama-server is listening on " + sshHost + ":" + port
                        + " but did not become inference-ready within " + STARTUP_TIMEOUT_SECONDS + "s");
            }
            logger.info("llama-server on Pi already inference-ready on {}:{} (adopting existing process)",
                    sshHost, port);
            running = true;
            lifecycleState = "READY";
            startIdleMonitor();
            return;
        }

        if (!starting.compareAndSet(false, true)) {
            logger.debug("Another thread is starting llama-server on Pi, waiting...");
            while (!isRunning()) {
                sleepQuietly(500);
            }
            starting.set(false);
            return;
        }

        try {
            startAttempts.incrementAndGet();
            lifecycleState = "STARTING";
            String modelPath = getModelPath();
            logger.info("Starting llama-server on {}:{} (model: {}, threads: {}, context: {})",
                    sshHost, port, modelPath, threads, contextSize);
            startServer(modelPath);
            logger.info("llama-server started successfully on {}:{}", sshHost, port);
        } catch (Exception e) {
            lifecycleState = "FAILED";
            lastFailureReason = e.getMessage();
            logger.error("Failed to start llama-server on Pi: {}", e.getMessage(), e);
            throw new IllegalStateException("llama-server failed to start on Pi: " + e.getMessage(), e);
        } finally {
            starting.set(false);
        }
    }

    @Override
    public void stop() {
        try {
            logger.info("Stopping llama-server on {} port {}", sshHost, port);
            String cmd = String.format(
                    "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 -o ServerAliveInterval=2 -o ServerAliveCountMax=3 %s@%s \"pkill -f 'llama-server.*--port.*%d'\"",
                    sshUser, sshHost, port);
            runSshCommand(cmd, 10);
            if (!awaitServerStopped(STOP_TIMEOUT_SECONDS)) {
                throw new IllegalStateException("llama-server remained reachable on " + sshHost + ":" + port
                        + " after " + STOP_TIMEOUT_SECONDS + "s");
            }
        } catch (Exception e) {
            logger.warn("Failed to stop llama-server on Pi: {}", e.getMessage());
        }
        running = false;
        lifecycleState = "STOPPED";
        cancelIdleMonitor();
        logger.info("llama-server stopped on Pi");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Runs a minimal completion through the same OkHttp transport used by Spring AI. */
    public boolean testInferenceConnection() {
        return inferenceReadyCheck();
    }

    @Override
    public void restart() {
        logger.info("Restarting llama-server on Pi with updated model");
        stop();
        try {
            String modelPath = getModelPath();
            startServer(modelPath);
            logger.info("llama-server restarted on Pi");
        } catch (Exception e) {
            lifecycleState = "FAILED";
            lastFailureReason = e.getMessage();
            logger.error("Failed to restart llama-server on Pi: {}", e.getMessage(), e);
            throw new IllegalStateException("llama-server restart failed on Pi: " + e.getMessage(), e);
        }
    }

    private void startServer(String modelPath) throws Exception {
        long startedAt = System.currentTimeMillis();
        String binPath = appSettingsStore.get("llamacpp.bin")
                .orElse("/home/dietpi/llama.cpp/build/bin/llama-server");

        // Use 'at now' to truly detach llama-server from the SSH session.
        // The SSH command just queues the job and exits; llama-server runs independently on Pi.
        String logDir = "/tmp";
        String cmd = String.format(
                "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=2 -o ServerAliveCountMax=3 %s@%s \"nohup '%s' " +
                "-m '%s' --host 0.0.0.0 --port %d -t %d -c %d -b 128 -ub 64 --timeout 0" +
                " > '%s/llama-server.log' 2>&1 & disown\"",
                sshUser, sshHost, binPath, modelPath, port, threads, contextSize, logDir);

        // Run the SSH command to launch llama-server on Pi, then exit.
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
        pb.redirectErrorStream(true);
        Process launchProc = pb.start();
        if (!launchProc.waitFor(15, TimeUnit.SECONDS)) {
            launchProc.destroyForcibly();
            throw new RuntimeException("SSH launch command timed out");
        }

        // /health can return 200 just before the chat-completion endpoint is
        // usable. Require a real small completion too, retrying while the model
        // finishes loading/warming. No SSH process to track; llama-server runs
        // independently on Pi.
        if (!awaitServerReady(STARTUP_TIMEOUT_SECONDS)) {
            String log = readPiLog();
            throw new RuntimeException("llama-server on Pi failed to become inference-ready within "
                    + STARTUP_TIMEOUT_SECONDS + "s. Log: " + log);
        }

        running = true;
        lifecycleState = "READY";
        lastFailureReason = null;
        lastReadinessMillis = System.currentTimeMillis() - startedAt;
        idleCheckTime.set(System.currentTimeMillis());
        startIdleMonitor();
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
                    logger.debug("llama-server on Pi has {} in-flight request(s), deferring auto-stop", active);
                    return;
                }

                long idleSeconds = getIdleSeconds();
                if (idleSeconds < idleTimeoutSec) {
                    return;
                }

                logger.info("llama-server on Pi idle for {}s >= {}s, auto-stopping",
                        idleSeconds, idleTimeoutSec);
                idleStops.incrementAndGet();
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
        return idleCheckTime.get() > 0
                ? (System.currentTimeMillis() - idleCheckTime.get()) / 1000
                : 0;
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
                    .uri(URI.create(String.format("http://%s:%d/health", sshHost, port)))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    boolean awaitServerReady(int timeoutSeconds) {
        long waited = 0;
        while (waited < timeoutSeconds) {
            sleepQuietly(2000);
            if (healthCheck() && inferenceReadyCheck()) {
                logger.info("llama-server on Pi is inference-ready after {}s", waited + 2);
                return true;
            }
            waited += 2;
        }
        return healthCheck() && inferenceReadyCheck();
    }

    /**
     * llama.cpp acknowledges SIGTERM before it has released its HTTP listener when
     * a generation is still unwinding. Starting a replacement during that window
     * produces a server that can answer /health but cannot accept completions.
     */
    boolean awaitServerStopped(int timeoutSeconds) {
        long waited = 0;
        while (waited < timeoutSeconds) {
            if (!healthCheck()) {
                return true;
            }
            sleepQuietly(1000);
            waited++;
        }
        return !healthCheck();
    }

    private boolean inferenceReadyCheck() {
        try {
            String payload = "{\"model\":\"" + getModelPath()
                    + "\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply with exactly: OK\"}]"
                    + ",\"max_tokens\":8,\"temperature\":0.2}";
            OkHttpClient client = new OkHttpClient.Builder()
                    .callTimeout(10, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder()
                    .url(String.format("http://%s:%d/v1/chat/completions", sshHost, port))
                    .post(RequestBody.create(payload, MediaType.get("application/json")))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                return response.isSuccessful() && body.contains("\"choices\"");
            }
        } catch (Exception e) {
            logger.debug("Pi llama-server inference readiness check failed: {}", e.getMessage());
            return false;
        }
    }

    private String getModelPath() {
        return appSettingsStore.get("llamacpp.model")
                .orElse("/home/dietpi/.synapse/models/Qwen3.5-2B_Q4_k_m.gguf");
    }

    /** Additive status used by monitoring without changing LLM request behavior. */
    public java.util.Map<String, Object> lifecycleStatus() {
        return java.util.Map.ofEntries(
            java.util.Map.entry("state", lifecycleState),
            java.util.Map.entry("running", running),
            java.util.Map.entry("startAttempts", startAttempts.get()),
            java.util.Map.entry("idleStops", idleStops.get()),
            java.util.Map.entry("inFlightRequests", inFlightRequests.get()),
            java.util.Map.entry("modelPath", getModelPath()),
            java.util.Map.entry("readinessMillis", lastReadinessMillis),
            java.util.Map.entry("failureReason", lastFailureReason == null ? "" : lastFailureReason));
    }

    private String readPiLog() {
        try {
            String cmd = String.format(
                    "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 -o ServerAliveInterval=2 -o ServerAliveCountMax=3 %s@%s \"tail -20 /tmp/llama-server.log\"",
                    sshUser, sshHost);
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (p.waitFor(10, TimeUnit.SECONDS)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
            }
        } catch (Exception e) {
            return "failed to read log: " + e.getMessage();
        }
        return "no log available";
    }

    private void runSshCommand(String cmd, int timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("SSH command timed out after " + timeoutSec + "s");
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
