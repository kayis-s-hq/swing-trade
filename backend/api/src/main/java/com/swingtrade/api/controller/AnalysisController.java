package com.swingtrade.api.controller;

import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.api.service.CompositeAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisController.class);

    @Autowired
    private CompositeAnalysisService analysisService;

    @PostMapping("/analysis/analyze")
    public ResponseEntity<CompositeAnalysis> analyze(@RequestParam String symbol) {
        try {
            CompositeAnalysis result = analysisService.analyze(symbol);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Analysis failed for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}