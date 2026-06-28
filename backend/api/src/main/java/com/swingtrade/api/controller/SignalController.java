package com.swingtrade.api.controller;

import com.swingtrade.api.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for trading signals and analysis.
 * Provides endpoints for signal generation, scanning, and analysis.
 */
@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private static final Logger logger = LoggerFactory.getLogger(SignalController.class);

    @Autowired
    private com.swingtrade.api.SignalService signalService;

    @Autowired
    private com.swingtrade.api.ScanService scanService;

    @Autowired
    private com.swingtrade.data.repository.SignalRepository signalRepository;

    /**
     * Get the latest trading signals for today.
     *
     * @return List of latest signals
     */
    @GetMapping("/latest")
    public ResponseEntity<List<SignalResponse>> getLatestSignals() {
        logger.debug("Fetching latest signals from DB");
        List<com.swingtrade.data.entity.SignalEntity> all = signalRepository.findAll();
        all.sort(java.util.Comparator.comparing(
            (com.swingtrade.data.entity.SignalEntity e) -> e.getDate() != null ? e.getDate() : java.time.LocalDate.now()
        ).reversed());
        List<SignalResponse> signals = all.stream().map(this::entityToResponse).collect(Collectors.toList());
        return ResponseEntity.ok(signals);
    }

    /**
     * Get signals for a specific symbol.
     *
     * @param symbol Stock symbol
     * @return List of signals for the symbol
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<SignalResponse>> getSignalsBySymbol(@PathVariable String symbol) {
        logger.debug("Fetching signals for symbol: {}", symbol);
        List<SignalResponse> signals = convertSignalsToResponses(signalService.getSignalsBySymbol(symbol));
        return ResponseEntity.ok(signals);
    }

    /**
     * Get signals by date range.
     *
     * @param startDate Start date (ISO format)
     * @param endDate End date (ISO format)
     * @return List of signals in date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<SignalResponse>> getSignalsByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        logger.debug("Fetching signals for date range: {} to {}", startDate, endDate);
        List<SignalResponse> signals = convertSignalsToResponses(signalService.getSignalsByDateRange(startDate, endDate));
        return ResponseEntity.ok(signals);
    }

    /**
     * Get signals by type (BUY, SELL, HOLD).
     *
     * @param type Signal type
     * @return List of signals of specified type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<SignalResponse>> getSignalsByType(@PathVariable SignalResponse.SignalType type) {
        logger.debug("Fetching signals of type: {}", type);
        List<SignalResponse> signals = convertSignalsToResponses(signalService.getSignalsByType(type.name()));
        return ResponseEntity.ok(signals);
    }

    /**
     * Get high confidence signals.
     *
     * @param minConfidence Minimum confidence threshold (0.0 to 1.0)
     * @return List of high confidence signals
     */
    @GetMapping("/high-confidence")
    public ResponseEntity<List<SignalResponse>> getHighConfidenceSignals(
            @RequestParam(defaultValue = "0.7") Double minConfidence
    ) {
        logger.debug("Fetching high confidence signals (min: {})", minConfidence);

        if (minConfidence < 0.0 || minConfidence > 1.0) {
            return ResponseEntity.badRequest().build();
        }

        List<SignalResponse> signals = convertSignalsToResponses(signalService.getHighConfidenceSignals(minConfidence));
        return ResponseEntity.ok(signals);
    }

    /**
     * Generate a new signal for a symbol.
     *
     * @param request Symbol request
     * @return Generated signal
     */
    @PostMapping("/generate")
    public ResponseEntity<SignalResponse> generateSignal(
            @Valid @RequestBody SymbolRequest request
    ) {
        logger.info("Generating signal for symbol: {}", request.getSymbol());

        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        com.swingtrade.domain.Signal domainSignal = signalService.generateSignal(request.getSymbol());
        SignalResponse signal = signalService.convertSignalToResponse(domainSignal);
        return ResponseEntity.ok(signal);
    }

    /**
     * Trigger a full market scan.
     *
     * @param request Scan request (optional parameters)
     * @return Scan result
     */
    @PostMapping("/scan")
    public ResponseEntity<ScanResponse> triggerScan(
            @Valid @RequestBody(required = false) ScanRequest request
    ) {
        logger.info("Triggering market scan");
        ScanResponse scanResult = scanService.triggerScan();

        if (scanResult.getSignalsFound() == 0) {
            scanResult.setStatus(ScanResponse.ScanStatus.NO_SIGNALS);
        }

        return ResponseEntity.ok(scanResult);
    }

    /**
     * Get scan history.
     *
     * @return List of scan results
     */
    @GetMapping("/scan/history")
    public ResponseEntity<List<ScanResponse>> getScanHistory() {
        logger.debug("Fetching scan history");
        List<ScanResponse> history = scanService.getScanHistory();
        return ResponseEntity.ok(history);
    }

    /**
     * Get technical analysis for a symbol.
     *
     * @param symbol Stock symbol
     * @return Technical analysis result
     */
    @GetMapping("/analysis/{symbol}")
    public ResponseEntity<TechnicalAnalysisResponse> getTechnicalAnalysis(@PathVariable String symbol) {
        logger.debug("Fetching technical analysis for symbol: {}", symbol);
        TechnicalAnalysisResponse analysis = convertTechnicalAnalysis(signalService.getTechnicalAnalysis(symbol));

        if (analysis == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(analysis);
    }

    /**
     * Get sentiment analysis for a symbol.
     *
     * @param symbol Stock symbol
     * @return Sentiment analysis result
     */
    @GetMapping("/sentiment/{symbol}")
    public ResponseEntity<SentimentAnalysisResponse> getSentimentAnalysis(@PathVariable String symbol) {
        logger.debug("Fetching sentiment analysis for symbol: {}", symbol);
        SentimentAnalysisResponse sentiment = convertSentimentAnalysis(signalService.getSentimentAnalysis(symbol));

        if (sentiment == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(sentiment);
    }

    /**
     * Get combined signal with LLM sentiment.
     *
     * @param symbol Stock symbol
     * @return Combined signal analysis
     */
    @GetMapping("/combined/{symbol}")
    public ResponseEntity<CombinedSignalResponse> getCombinedSignal(@PathVariable String symbol) {
        logger.debug("Fetching combined signal for symbol: {}", symbol);
        CombinedSignalResponse combined = convertCombinedSignal(signalService.getCombinedSignal(symbol));

        if (combined == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(combined);
    }

    /**
     * Convert SignalService.Signal to SignalResponse DTO
     */
    private SignalResponse convertSignalToResponse(com.swingtrade.api.SignalService.Signal signal) {
        if (signal == null) {
            return null;
        }
        SignalResponse response = new SignalResponse();
        response.setSymbol(signal.getSymbol());
        response.setSignalType(SignalResponse.SignalType.valueOf(signal.getType().toString()));
        response.setConfidence(signal.getConfidence() != null ? BigDecimal.valueOf(signal.getConfidence()) : null);
        response.setGeneratedAt(signal.getDate());
        response.setReasoning(signal.getReasoning());
        return response;
    }

    /**
     * Convert SignalEntity from DB to SignalResponse DTO
     */
    private SignalResponse entityToResponse(com.swingtrade.data.entity.SignalEntity entity) {
        SignalResponse response = new SignalResponse();
        response.setId(entity.getId());
        response.setSymbol(entity.getSymbol());
        response.setDate(entity.getDate());
        response.setSignalType(SignalResponse.SignalType.valueOf(entity.getSignalType()));
        response.setConfidence(entity.getConfidenceScore());
        response.setReasoning(entity.getReasoning());
        response.setEntryPrice(entity.getEntryPrice());
        response.setStopLoss(entity.getStopLoss());
        response.setTarget(entity.getTarget());
        response.setRiskRewardRatio(entity.getRiskReward());
        if (entity.getIndicators() != null) {
            response.setIndicators(java.util.List.of(entity.getIndicators().split(",")));
        }
        response.setGeneratedAt(entity.getGeneratedAt());
        return response;
    }

    /**
     * Convert list of SignalService.Signal to SignalResponse DTOs
     */
    private List<SignalResponse> convertSignalsToResponses(List<com.swingtrade.api.SignalService.Signal> signals) {
        if (signals == null) {
            return List.of();
        }
        List<SignalResponse> responses = List.copyOf(signals).stream()
                .map(this::convertSignalToResponse)
                .filter(r -> r != null)
                .toList();
        return responses;
    }

    /**
     * Convert SignalService.TechnicalAnalysis to TechnicalAnalysisResponse DTO
     */
    private TechnicalAnalysisResponse convertTechnicalAnalysis(com.swingtrade.api.SignalService.TechnicalAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        TechnicalAnalysisResponse response = new TechnicalAnalysisResponse();
        response.setSymbol(analysis.getSymbol());
        response.setAnalysisDate(analysis.getDate());
        response.setIndicators(analysis.getIndicators());
        response.setSignal("TECHNICAL");
        return response;
    }

    /**
     * Convert SignalService.SentimentAnalysis to SentimentAnalysisResponse DTO
     */
    private SentimentAnalysisResponse convertSentimentAnalysis(com.swingtrade.api.SignalService.SentimentAnalysis sentiment) {
        if (sentiment == null) {
            return null;
        }
        SentimentAnalysisResponse response = new SentimentAnalysisResponse();
        response.setSymbol(sentiment.getSymbol());
        response.setAnalyzedAt(sentiment.getDate());
        response.setScore(SentimentAnalysisResponse.SentimentScore.valueOf(sentiment.getScore()));
        response.setSummary(sentiment.getSummary());
        return response;
    }

    /**
     * Convert SignalService.CombinedSignal to CombinedSignalResponse DTO
     */
    private CombinedSignalResponse convertCombinedSignal(com.swingtrade.api.SignalService.CombinedSignal combined) {
        if (combined == null) {
            return null;
        }
        CombinedSignalResponse response = new CombinedSignalResponse();
        response.setSymbol(combined.getSymbol());
        response.setAnalysisDate(combined.getDate());
        response.setSignalType(SignalResponse.SignalType.valueOf(combined.getFinalSignal().toString()));
        response.setTechnicalAnalysis(convertTechnicalAnalysis(null)); // Placeholder
        response.setSentimentAnalysis(convertSentimentAnalysis(combined.getSentiment()));
        return response;
    }
}
