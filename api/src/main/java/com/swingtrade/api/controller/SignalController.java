package com.swingtrade.api.controller;

import com.swingtrade.api.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * Get the latest trading signals for today.
     *
     * @return List of latest signals
     */
    @GetMapping("/latest")
    public ResponseEntity<List<SignalResponse>> getLatestSignals() {
        logger.debug("Fetching latest signals");

        try {
            List<SignalResponse> signals = signalService.getLatestSignals();
            return ResponseEntity.ok(signals);

        } catch (Exception e) {
            logger.error("Error fetching signals: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(List.of(buildSignalErrorResponse("Internal Server Error", "Failed to fetch signals")));
        }
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

        try {
            List<SignalResponse> signals = signalService.getSignalsBySymbol(symbol);
            return ResponseEntity.ok(signals);

        } catch (Exception e) {
            logger.error("Error fetching signals: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(List.of(buildSignalErrorResponse("Internal Server Error", "Failed to fetch signals")));
        }
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

        try {
            List<SignalResponse> signals = signalService.getSignalsByDateRange(startDate, endDate);
            return ResponseEntity.ok(signals);

        } catch (Exception e) {
            logger.error("Error fetching signals: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(List.of(buildSignalErrorResponse("Internal Server Error", "Failed to fetch signals")));
        }
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

        try {
            List<SignalResponse> signals = signalService.getSignalsByType(type);
            return ResponseEntity.ok(signals);

        } catch (Exception e) {
            logger.error("Error fetching signals: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(List.of(buildSignalErrorResponse("Internal Server Error", "Failed to fetch signals")));
        }
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

        try {
            if (minConfidence < 0.0 || minConfidence > 1.0) {
                return ResponseEntity.badRequest()
                        .body(List.of(buildSignalErrorResponse("Bad Request", "Confidence must be between 0.0 and 1.0")));
            }

            List<SignalResponse> signals = signalService.getHighConfidenceSignals(minConfidence);
            return ResponseEntity.ok(signals);

        } catch (Exception e) {
            logger.error("Error fetching signals: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(List.of(buildSignalErrorResponse("Internal Server Error", "Failed to fetch signals")));
        }
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

        try {
            if (!request.isValid()) {
                return ResponseEntity.badRequest()
                        .body(buildSignalErrorResponse("Bad Request", "Invalid symbol format"));
            }

            SignalResponse signal = signalService.generateSignal(request.getSymbol());
            return ResponseEntity.status(HttpStatus.CREATED).body(signal);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildSignalErrorResponse("Bad Request", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error generating signal: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildSignalErrorResponse("Internal Server Error", "Failed to generate signal"));
        }
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

        try {
            ScanResponse scanResult = scanService.triggerScan(request);

            if (scanResult.getSignalsFound() == 0) {
                scanResult.setStatus(ScanResponse.ScanStatus.NO_SIGNALS);
            }

            return ResponseEntity.ok(scanResult);

        } catch (Exception e) {
            logger.error("Error triggering scan: {}", e.getMessage(), e);
            ScanResponse errorResponse = new ScanResponse();
            errorResponse.setStatus(ScanResponse.ScanStatus.FAILED);
            errorResponse.setMessage("Scan failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get scan history.
     *
     * @return List of scan results
     */
    @GetMapping("/scan/history")
    public ResponseEntity<List<ScanResponse>> getScanHistory() {
        logger.debug("Fetching scan history");

        try {
            List<ScanResponse> history = scanService.getScanHistory();
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            logger.error("Error fetching scan history: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(List.of(buildScanErrorResponse("Internal Server Error", "Failed to fetch history")));
        }
    }

    /**
     * Get technical analysis for a symbol.
     *
     * @param request Symbol request
     * @return Technical analysis result
     */
    @GetMapping("/analysis/{symbol}")
    public ResponseEntity<TechnicalAnalysisResponse> getTechnicalAnalysis(@PathVariable String symbol) {
        logger.debug("Fetching technical analysis for symbol: {}", symbol);

        try {
            TechnicalAnalysisResponse analysis = signalService.getTechnicalAnalysis(symbol);

            if (analysis == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            logger.error("Error fetching technical analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildTechnicalAnalysisErrorResponse("Internal Server Error", "Failed to fetch analysis"));
        }
    }

    /**
     * Get sentiment analysis for a symbol.
     *
     * @param request Symbol request
     * @return Sentiment analysis result
     */
    @GetMapping("/sentiment/{symbol}")
    public ResponseEntity<SentimentAnalysisResponse> getSentimentAnalysis(@PathVariable String symbol) {
        logger.debug("Fetching sentiment analysis for symbol: {}", symbol);

        try {
            SentimentAnalysisResponse sentiment = signalService.getSentimentAnalysis(symbol);

            if (sentiment == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(sentiment);

        } catch (Exception e) {
            logger.error("Error fetching sentiment analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildSentimentErrorResponse("Internal Server Error", "Failed to fetch sentiment"));
        }
    }

    /**
     * Get combined signal with LLM sentiment.
     *
     * @param request Symbol request
     * @return Combined signal analysis
     */
    @GetMapping("/combined/{symbol}")
    public ResponseEntity<CombinedSignalResponse> getCombinedSignal(@PathVariable String symbol) {
        logger.debug("Fetching combined signal for symbol: {}", symbol);

        try {
            CombinedSignalResponse combined = signalService.getCombinedSignal(symbol);

            if (combined == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(combined);

        } catch (Exception e) {
            logger.error("Error fetching combined signal: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildCombinedSignalErrorResponse("Internal Server Error", "Failed to fetch combined signal"));
        }
    }

    /**
     * Build a SignalResponse with error info.
     */
    private SignalResponse buildSignalErrorResponse(String error, String message) {
        SignalResponse response = new SignalResponse();
        response.setReasoning(error + ": " + message);
        return response;
    }

    /**
     * Build a ScanResponse with error info.
     */
    private ScanResponse buildScanErrorResponse(String error, String message) {
        ScanResponse response = new ScanResponse();
        response.setStatus(ScanResponse.ScanStatus.FAILED);
        response.setMessage(error + ": " + message);
        return response;
    }

    /**
     * Build a TechnicalAnalysisResponse with error info.
     */
    private TechnicalAnalysisResponse buildTechnicalAnalysisErrorResponse(String error, String message) {
        TechnicalAnalysisResponse response = new TechnicalAnalysisResponse();
        response.setAnalysisDate(LocalDate.now());
        response.setSymbol("ERROR");
        response.setReasoning(error + ": " + message);
        return response;
    }

    /**
     * Build a SentimentAnalysisResponse with error info.
     */
    private SentimentAnalysisResponse buildSentimentErrorResponse(String error, String message) {
        SentimentAnalysisResponse response = new SentimentAnalysisResponse();
        response.setAnalyzedAt(LocalDate.now());
        response.setSymbol("ERROR");
        response.setReasoning(error + ": " + message);
        return response;
    }

    /**
     * Build a CombinedSignalResponse with error info.
     */
    private CombinedSignalResponse buildCombinedSignalErrorResponse(String error, String message) {
        CombinedSignalResponse response = new CombinedSignalResponse();
        response.setCombinedScore(0.0);
        response.setSymbol("ERROR");
        response.setReasoning(error + ": " + message);
        return response;
    }

    /**
     * Scan request DTO.
     */
    public static class ScanRequest {
        private List<String> symbols;
        private Boolean includeSentiment;
        private Double minConfidence;

        public ScanRequest() {
            this.includeSentiment = true;
            this.minConfidence = 0.5;
        }

        // Getters and Setters
        public List<String> getSymbols() {
            return symbols;
        }

        public void setSymbols(List<String> symbols) {
            this.symbols = symbols;
        }

        public Boolean getIncludeSentiment() {
            return includeSentiment;
        }

        public void setIncludeSentiment(Boolean includeSentiment) {
            this.includeSentiment = includeSentiment;
        }

        public Double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(Double minConfidence) {
            this.minConfidence = minConfidence;
        }
    }

    /**
     * Technical analysis response DTO.
     */
    public static class TechnicalAnalysisResponse {
        private String symbol;
        private LocalDate analysisDate;
        private Double rsi;
        private Double macd;
        private Double emaFast;
        private Double emaSlow;
        private Double atr;
        private DoublebollingerBandUpper;
        private Double bollingerBandLower;
        private Double volume;
        private Double avgVolume;
        private String trend;
        private String signal;
        private String reasoning;
        private java.util.List<String> indicators;

        // Getters and Setters
        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public LocalDate getAnalysisDate() {
            return analysisDate;
        }

        public void setAnalysisDate(LocalDate analysisDate) {
            this.analysisDate = analysisDate;
        }

        public Double getRsi() {
            return rsi;
        }

        public void setRsi(Double rsi) {
            this.rsi = rsi;
        }

        public Double getMacd() {
            return macd;
        }

        public void setMacd(Double macd) {
            this.macd = macd;
        }

        public Double getEmaFast() {
            return emaFast;
        }

        public void setEmaFast(Double emaFast) {
            this.emaFast = emaFast;
        }

        public Double getEmaSlow() {
            return emaSlow;
        }

        public void setEmaSlow(Double emaSlow) {
            this.emaSlow = emaSlow;
        }

        public Double getAtr() {
            return atr;
        }

        public void setAtr(Double atr) {
            this.atr = atr;
        }

        public Double getBollingerBandUpper() {
            return bollingerBandUpper;
        }

        public void setBollingerBandUpper(Double bollingerBandUpper) {
            this.bollingerBandUpper = bollingerBandUpper;
        }

        public Double getBollingerBandLower() {
            return bollingerBandLower;
        }

        public void setBollingerBandLower(Double bollingerBandLower) {
            this.bollingerBandLower = bollingerBandLower;
        }

        public Double getVolume() {
            return volume;
        }

        public void setVolume(Double volume) {
            this.volume = volume;
        }

        public Double getAvgVolume() {
            return avgVolume;
        }

        public void setAvgVolume(Double avgVolume) {
            this.avgVolume = avgVolume;
        }

        public String getTrend() {
            return trend;
        }

        public void setTrend(String trend) {
            this.trend = trend;
        }

        public String getSignal() {
            return signal;
        }

        public void setSignal(String signal) {
            this.signal = signal;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }

        public java.util.List<String> getIndicators() {
            return indicators;
        }

        public void setIndicators(java.util.List<String> indicators) {
            this.indicators = indicators;
        }
    }

    /**
     * Sentiment analysis response DTO.
     */
    public static class SentimentAnalysisResponse {
        private String symbol;
        private LocalDate analyzedAt;
        private SentimentScore score;
        private Double confidence;
        private String summary;
        private String reasoning;
        private java.util.List<SentimentSource> sources;

        // Getters and Setters
        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public LocalDate getAnalyzedAt() {
            return analyzedAt;
        }

        public void setAnalyzedAt(LocalDate analyzedAt) {
            this.analyzedAt = analyzedAt;
        }

        public SentimentScore getScore() {
            return score;
        }

        public void setScore(SentimentScore score) {
            this.score = score;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }

        public java.util.List<SentimentSource> getSources() {
            return sources;
        }

        public void setSources(java.util.List<SentimentSource> sources) {
            this.sources = sources;
        }

        public enum SentimentScore {
            POSITIVE("Positive"),
            NEUTRAL("Neutral"),
            NEGATIVE("Negative");

            private final String description;

            SentimentScore(String description) {
                this.description = description;
            }

            public String getDescription() {
                return description;
            }
        }

        public static class SentimentSource {
            private String name;
            private Double weight;
            private String sentiment;

            public SentimentSource() {
            }

            public SentimentSource(String name, Double weight, String sentiment) {
                this.name = name;
                this.weight = weight;
                this.sentiment = sentiment;
            }

            // Getters and Setters
            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public Double getWeight() {
                return weight;
            }

            public void setWeight(Double weight) {
                this.weight = weight;
            }

            public String getSentiment() {
                return sentiment;
            }

            public void setSentiment(String sentiment) {
                this.sentiment = sentiment;
            }
        }
    }

    /**
     * Combined signal response DTO.
     */
    public static class CombinedSignalResponse {
        private String symbol;
        private LocalDate analysisDate;
        private Double combinedScore;
        private SignalResponse.SignalType signalType;
        private Double technicalScore;
        private Double sentimentScore;
        private Double volumeScore;
        private String reasoning;
        private TechnicalAnalysisResponse technicalAnalysis;
        private SentimentAnalysisResponse sentimentAnalysis;

        // Getters and Setters
        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public LocalDate getAnalysisDate() {
            return analysisDate;
        }

        public void setAnalysisDate(LocalDate analysisDate) {
            this.analysisDate = analysisDate;
        }

        public Double getCombinedScore() {
            return combinedScore;
        }

        public void setCombinedScore(Double combinedScore) {
            this.combinedScore = combinedScore;
        }

        public SignalResponse.SignalType getSignalType() {
            return signalType;
        }

        public void setSignalType(SignalResponse.SignalType signalType) {
            this.signalType = signalType;
        }

        public Double getTechnicalScore() {
            return technicalScore;
        }

        public void setTechnicalScore(Double technicalScore) {
            this.technicalScore = technicalScore;
        }

        public Double getSentimentScore() {
            return sentimentScore;
        }

        public void setSentimentScore(Double sentimentScore) {
            this.sentimentScore = sentimentScore;
        }

        public Double getVolumeScore() {
            return volumeScore;
        }

        public void setVolumeScore(Double volumeScore) {
            this.volumeScore = volumeScore;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }

        public TechnicalAnalysisResponse getTechnicalAnalysis() {
            return technicalAnalysis;
        }

        public void setTechnicalAnalysis(TechnicalAnalysisResponse technicalAnalysis) {
            this.technicalAnalysis = technicalAnalysis;
        }

        public SentimentAnalysisResponse getSentimentAnalysis() {
            return sentimentAnalysis;
        }

        public void setSentimentAnalysis(SentimentAnalysisResponse sentimentAnalysis) {
            this.sentimentAnalysis = sentimentAnalysis;
        }
    }
}
