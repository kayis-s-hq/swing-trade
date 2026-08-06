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

        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        try {
            emitter.send(SseEmitter.event()
                .name("started")
                .data(Map.of("symbol", symbol, "backfillYears", backfillYears)));
        } catch (IOException e) {
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
            }
        });

        emitter.onCompletion(() -> logger.info("Client disconnected from analysis stream for {}", symbol));
        emitter.onTimeout(() -> logger.warn("Analysis stream timed out for {}", symbol));
        emitter.onError(e -> logger.error("Analysis stream error for {}: {}", symbol, e.getMessage()));

        return emitter;
    }
}