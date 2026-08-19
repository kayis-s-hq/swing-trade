package com.swingtrade.api.controller;

import com.swingtrade.api.dto.FullAnalysisResult;
import com.swingtrade.api.service.AnalysisOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public AnalysisOrchestrationController(AnalysisOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/analysis/run-full")
    public SseEmitter runFullAnalysis(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "3") int backfillYears) {

        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout (Pi llama-server can be slow)

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