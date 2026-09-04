package com.swingtrade.llm.service;

import com.swingtrade.domain.store.AppSettingsStore;
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

        if (!starting.compareAndSet(false, true)) {
            logger.debug("Another thread is starting llama-server on Pi, waiting...");
            while (!isRunning()) {
                sleepQuietly(500);
            }
            starting.set(false);
            return;
        }

        try {
            String modelPath = getModelPath();
            logger.info("Starting llama-server on {}:{} (model: {}, threads: {}, context: {})",
                    sshHost, port, modelPath, threads, contextSize);
            startServer(modelPath);
            logger.info("llama-server started successfully on {}:{}", sshHost, port);
        } catch (Exception e) {
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
        } catch (Exception e) {
            logger.warn("Failed to stop llama-server on Pi: {}", e.getMessage());
        }
        running = false;
        cancelIdleMonitor();
        logger.info("llama-server stopped on Pi");
    }

    @Override
    public boolean isRunning() {
        return running;
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
            logger.error("Failed to restart llama-server on Pi: {}", e.getMessage(), e);
            throw new IllegalStateException("llama-server restart failed on Pi: " + e.getMessage(), e);
        }
    }

    private void startServer(String modelPath) throws Exception {
        String binPath = appSettingsStore.get("llamacpp.bin")
                .orElse("/home/dietpi/llama.cpp/build/bin/llama-server");

        // Use 'at now' to truly detach llama-server from the SSH session.
        // The SSH command just queues the job and exits; llama-server runs independently on Pi.
        String logDir = "/tmp";
        String cmd = String.format(
                "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=2 -o ServerAliveCountMax=3 %s@%s \"nohup '%s' " +
                "-m '%s' --host 0.0.0.0 --port %d -t %d -c %d -b 128 -ub 64 --mlock --timeout 0" +
                " > '%s/llama-server.log' 2>&1 & disown\"",
                sshUser, sshHost, binPath, modelPath, port, threads, contextSize, logDir);

        // Run the SSH command to launch llama-server on Pi, then exit.
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
        pb.redirectErrorStream(true);
        Process launchProc = pb.start();
        if (!launchProc.waitFor(15, TimeUnit.SECONDS)) {
            launchProc.destroyForcibly();
            throw new RuntimeException("SSH launch command timed out");
        }

        // Poll health endpoint — this is the real success indicator.
        // No SSH process to track; llama-server runs independently on Pi.
        long waited = 0;
        while (waited < STARTUP_TIMEOUT_SECONDS) {
            sleepQuietly(2000);
            if (healthCheck()) {
                logger.info("llama-server on Pi is healthy after {}s", waited);
                break;
            }
            waited += 2;
        }

        if (!healthCheck()) {
            String log = readPiLog();
            throw new RuntimeException("llama-server on Pi failed to become healthy within " + STARTUP_TIMEOUT_SECONDS + "s. Log: " + log);
        }

        running = true;
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

    private String getModelPath() {
        return appSettingsStore.get("llamacpp.model")
                .orElse("/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf");
    }

    private String readPiLog() {
        try {
            String cmd = String.format(
                    "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 -o ServerAliveInterval=2 -o ServerAliveCountMax=3 %s@%s \"tail -20 /tmp/llama-server.log\"",
                    sshUser, sshHost);
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
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
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
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