package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.llm.SynthesisEvaluationSummary;
import com.swingtrade.llm.service.SynthesisEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only reporting endpoint for durable synthesis evaluation outcomes. */
@RestController
@RequestMapping("/api/synthesis/evaluations")
public class SynthesisEvaluationController {
    private final SynthesisEvaluationService evaluationService;

    public SynthesisEvaluationController(SynthesisEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SynthesisEvaluationSummary>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.summary()));
    }
}
