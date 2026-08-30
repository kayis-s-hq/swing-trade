package com.swingtrade.api.controller;

import com.swingtrade.api.service.CandidateScanService;
import com.swingtrade.data.entity.CandidateScanResultEntity;
import com.swingtrade.data.entity.CandidateScanRunEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidate-scans")
public class CandidateScanController {
    private final CandidateScanService service;

    public CandidateScanController(CandidateScanService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<?> start() {
        try { return ResponseEntity.ok(service.start()); }
        catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public List<CandidateScanRunEntity> history() { return service.getHistory(); }

    @GetMapping("/{runId}")
    public ResponseEntity<?> run(@PathVariable UUID runId) {
        CandidateScanRunEntity run = service.getRun(runId);
        return run == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(run);
    }

    @GetMapping("/{runId}/results")
    public ResponseEntity<List<CandidateScanResultEntity>> results(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {
        if (service.getRun(runId) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.getResults(runId, offset, limit));
    }

    @GetMapping("/{runId}/stream")
    public ResponseEntity<SseEmitter> stream(@PathVariable UUID runId) {
        SseEmitter emitter = service.stream(runId);
        return emitter == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(emitter);
    }

    @PostMapping("/{runId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID runId) {
        if (service.getRun(runId) == null) return ResponseEntity.notFound().build();
        service.cancel(runId);
        return ResponseEntity.ok(service.getRun(runId));
    }
}
