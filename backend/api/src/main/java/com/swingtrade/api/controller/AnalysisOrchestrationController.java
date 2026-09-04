package com.swingtrade.api.controller;

import com.swingtrade.api.dto.FullAnalysisResult;
import com.swingtrade.api.service.AnalysisOrchestratorService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class AnalysisOrchestrationController {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisOrchestrationController.class);

    private final AnalysisOrchestratorService orchestrator;

    /**
     * SSE stream lifetime. Must exceed the worst case of the whole pipeline, not
     * just one stage: sentiment and synthesis each allow up to
     * ANALYSIS_TIMEOUT_SECONDS (930s) against the CPU-bound local backend, so a
     * slow run legitimately outlives the old hardcoded 10 minutes. When it did,
     * the emitter closed underneath the still-running analysis and every later
     * stage failed with "ResponseBodyEmitter has already completed".
     */
    private final long streamTimeoutMs;

    public AnalysisOrchestrationController(
            AnalysisOrchestratorService orchestrator,
            @Value("${analysis.stream.timeout-ms:2400000}") long streamTimeoutMs) {
        this.orchestrator = orchestrator;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    @PostMapping("/analysis/run-full")
    @RateLimiter(name = "llmAnalysis")
    public SseEmitter runFullAnalysis(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "3") int backfillYears) {

        SseEmitter emitter = new SseEmitter(streamTimeoutMs);

        // Send keep-alive pings every 30s to prevent connection drop
        ScheduledExecutorService keepAlive = Executors.newSingleThreadScheduledExecutor();
        keepAlive.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("ping").data("keepalive"));
            } catch (IOException e) {
                keepAlive.shutdown();
            }
        }, 30, 30, TimeUnit.SECONDS);

        try {
            emitter.send(SseEmitter.event()
                .name("started")
                .data(Map.of("symbol", symbol, "backfillYears", backfillYears)));
        } catch (IOException e) {
            keepAlive.shutdown();
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                FullAnalysisResult result = orchestrator.runFullAnalysis(symbol, emitter, backfillYears);
                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(result));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", e.getMessage())));
                } catch (IOException ioEx) {
                    logger.error("Failed to send error: {}", ioEx.getMessage());
                }
                emitter.completeWithError(e);
            } finally {
                keepAlive.shutdown();
            }
        });

        emitter.onCompletion(() -> {
            keepAlive.shutdown();
            logger.info("Client disconnected from analysis stream for {}", symbol);
        });
        emitter.onTimeout(() -> {
            keepAlive.shutdown();
            logger.warn("Analysis stream timed out for {}", symbol);
        });
        emitter.onError(e -> {
            keepAlive.shutdown();
            logger.error("Analysis stream error for {}: {}", symbol, e.getMessage());
        });

        return emitter;
    }
}