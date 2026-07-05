package com.swingtrade.api.controller;

import com.swingtrade.api.dto.*;
import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.repository.SentimentResultRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    @Autowired
    private SentimentResultRepository sentimentResultRepository;

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
     * Generate a price-action signal for a symbol (Phase 2 strategy engine).
     *
     * @param symbol Stock symbol
     * @return Generated price-action signal, or 404 if there isn't enough candle history
     */
    @PostMapping("/price-action/{symbol}/generate")
    public ResponseEntity<SignalResponse> generatePriceActionSignal(@PathVariable String symbol) {
        logger.info("Generating price-action signal for symbol: {}", symbol);

        return signalService.generatePriceActionSignal(symbol)
                .map(entity -> ResponseEntity.ok(entityToResponse(entity)))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
        Optional<SentimentResultEntity> opt = sentimentResultRepository.findLatestBySymbol(symbol);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SentimentResultEntity entity = opt.get();
        SentimentAnalysisResponse response = new SentimentAnalysisResponse();
        response.setSymbol(entity.getSymbol());
        response.setAnalyzedAt(entity.getAnalyzedAt());
        response.setScore(SentimentAnalysisResponse.SentimentScore.valueOf(entity.getSentimentScore()));
        response.setSummary(entity.getSummary());
        return ResponseEntity.ok(response);
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
        CombinedSignalResponse combined = new CombinedSignalResponse();
        combined.setSymbol(symbol);
        combined.setAnalysisDate(LocalDate.now());

        Optional<SentimentResultEntity> sentimentOpt = sentimentResultRepository.findLatestBySymbol(symbol);
        if (sentimentOpt.isPresent()) {
            SentimentResultEntity entity = sentimentOpt.get();
            SentimentAnalysisResponse sa = new SentimentAnalysisResponse();
            sa.setSymbol(entity.getSymbol());
            sa.setAnalyzedAt(entity.getAnalyzedAt());
            sa.setScore(SentimentAnalysisResponse.SentimentScore.valueOf(entity.getSentimentScore()));
            sa.setSummary(entity.getSummary());
            combined.setSentimentAnalysis(sa);
        }

        return ResponseEntity.ok(combined);
    }

    /**
     * Convert SignalService.Signal to SignalResponse DTO
     */
    private SignalResponse convertSignalToResponse(com.swingtrade.api.dto.SignalQueryResult.Signal signal) {
        if (signal == null) {
            return null;
        }
        SignalResponse response = new SignalResponse();
        response.setSymbol(signal.symbol());
        response.setSignalType(SignalResponse.SignalType.valueOf(signal.type().toString()));
        response.setConfidence(signal.confidence() != null ? BigDecimal.valueOf(signal.confidence()) : null);
        response.setGeneratedAt(signal.date());
        response.setReasoning(signal.reasoning());
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
        response.setStrategy(entity.getStrategy());
        return response;
    }

    /**
     * Convert list of SignalService.Signal to SignalResponse DTOs
     */
    private List<SignalResponse> convertSignalsToResponses(List<com.swingtrade.api.dto.SignalQueryResult.Signal> signals) {
        if (signals == null) {
            return List.of();
        }
        return List.copyOf(signals).stream()
                .map(this::convertSignalToResponse)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Convert SignalService.TechnicalAnalysis to TechnicalAnalysisResponse DTO
     */
    private TechnicalAnalysisResponse convertTechnicalAnalysis(com.swingtrade.api.dto.SignalQueryResult.TechnicalAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        TechnicalAnalysisResponse response = new TechnicalAnalysisResponse();
        response.setSymbol(analysis.symbol());
        response.setAnalysisDate(analysis.date());
        response.setIndicators(analysis.indicators());
        response.setSignal("TECHNICAL");
        return response;
    }

    /**
     * Convert SignalService.SentimentAnalysis to SentimentAnalysisResponse DTO
     */
    private SentimentAnalysisResponse convertSentimentAnalysis(com.swingtrade.api.dto.SignalQueryResult.SentimentAnalysis sentiment) {
        if (sentiment == null) {
            return null;
        }
        SentimentAnalysisResponse response = new SentimentAnalysisResponse();
        response.setSymbol(sentiment.symbol());
        response.setAnalyzedAt(sentiment.date());
        response.setScore(SentimentAnalysisResponse.SentimentScore.valueOf(sentiment.score()));
        response.setSummary(sentiment.summary());
        return response;
    }

    /**
     * Convert SignalService.CombinedSignal to CombinedSignalResponse DTO
     */
    private CombinedSignalResponse convertCombinedSignal(com.swingtrade.api.dto.SignalQueryResult.CombinedSignal combined) {
        if (combined == null) {
            return null;
        }
        CombinedSignalResponse response = new CombinedSignalResponse();
        response.setSymbol(combined.symbol());
        response.setAnalysisDate(combined.date());
        response.setSignalType(SignalResponse.SignalType.valueOf(combined.finalSignal().toString()));
        response.setTechnicalAnalysis(convertTechnicalAnalysis(null)); // Placeholder
        response.setSentimentAnalysis(convertSentimentAnalysis(combined.sentiment()));
        return response;
    }
}
