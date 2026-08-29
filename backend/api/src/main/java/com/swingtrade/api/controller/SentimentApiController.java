package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.api.scheduler.SentimentEvaluationJob;
import com.swingtrade.data.repository.PdfExtractionRepository;
import com.swingtrade.data.service.SentimentAccuracyService;
import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.llm.domain.EarningsData;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.PdfExtractionService;
import com.swingtrade.llm.service.SentimentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for sentiment analysis, news, PDF extraction, and accuracy tracking.
 */
@RestController
@RequestMapping("/api")
public class SentimentApiController {

    private final SentimentService sentimentService;
    private final NewsIngestionService newsService;
    private final PdfExtractionService pdfService;
    private final SentimentAccuracyService accuracyService;
    private final SentimentStore sentimentStore;
    private final PdfExtractionRepository pdfExtractionRepo;
    private final SentimentEvaluationJob evaluationJob;

    public SentimentApiController(SentimentService sentimentService,
                                  NewsIngestionService newsService,
                                  PdfExtractionService pdfService,
                                  SentimentAccuracyService accuracyService,
                                  SentimentStore sentimentStore,
                                  PdfExtractionRepository pdfExtractionRepo,
                                  SentimentEvaluationJob evaluationJob) {
        this.sentimentService = sentimentService;
        this.newsService = newsService;
        this.pdfService = pdfService;
        this.accuracyService = accuracyService;
        this.sentimentStore = sentimentStore;
        this.pdfExtractionRepo = pdfExtractionRepo;
        this.evaluationJob = evaluationJob;
    }

    @GetMapping("/sentiment/{symbol}/latest")
    public ResponseEntity<ApiResponse<SentimentResult>> getLatestSentiment(
            @PathVariable String symbol) {
        return sentimentStore.findLatestBySymbol(symbol.toUpperCase())
            .map(e -> ResponseEntity.ok(ApiResponse.ok(e)))
            .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(SentimentResult.create(
                symbol.toUpperCase(), java.time.LocalDate.now(),
                SentimentResult.SentimentScore.NEUTRAL, "No data", "", 0.0,
                List.of(), List.of()))));
    }

    @GetMapping("/sentiment/{symbol}/history")
    public ResponseEntity<ApiResponse<List<SentimentResult>>> getSentimentHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Store returns all results; client pagination handled in memory
        List<SentimentResult> results = sentimentStore.findBySymbol(symbol.toUpperCase());
        int start = page * size;
        if (start >= results.size()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        int end = Math.min(start + size, results.size());
        results = results.subList(start, end);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    // --- Accuracy metrics endpoints ---

    @GetMapping("/sentiment/accuracy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccuracyStats() {
        var stats = accuracyService.getAccuracyStats();
        Map<String, Object> data = Map.of(
            "total", stats.total(),
            "correct", stats.correct(),
            "accuracy_pct", stats.accuracyPct(),
            "by_sentiment", stats.bySentiment(),
            "by_symbol", stats.bySymbol()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/sentiment/accuracy/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccuracySummary() {
        var stats = accuracyService.getAccuracyStats();
        long directionalTotal = accuracyService.getDirectionalCount();
        long directionalCorrect = accuracyService.getDirectionalCorrectCount();
        double directionalAccuracy = directionalTotal > 0 ?
            Math.round((double) directionalCorrect / directionalTotal * 10000.0) / 10000.0 : 0.0;

        Map<String, Object> summary = Map.of(
            "total", stats.total(),
            "correct", stats.correct(),
            "accuracy_pct", stats.accuracyPct(),
            "directional_accuracy", directionalAccuracy,
            "avg_confidence", accuracyService.getAvgConfidence(),
            "by_sentiment", stats.bySentiment(),
            "by_symbol", stats.bySymbol()
        );
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/sentiment/accuracy/by-window")
    public ResponseEntity<ApiResponse<List<SentimentAccuracyService.AccuracyByWindow>>> getAccuracyByWindow() {
        return ResponseEntity.ok(ApiResponse.ok(accuracyService.getAccuracyByWindow()));
    }

    @GetMapping("/sentiment/accuracy/by-regime")
    public ResponseEntity<ApiResponse<List<SentimentAccuracyService.AccuracyByRegime>>> getAccuracyByRegime() {
        return ResponseEntity.ok(ApiResponse.ok(accuracyService.getAccuracyByRegime()));
    }

    @GetMapping("/sentiment/accuracy/by-symbol")
    public ResponseEntity<ApiResponse<List<SentimentAccuracyService.AccuracyBySymbol>>> getAccuracyBySymbol() {
        return ResponseEntity.ok(ApiResponse.ok(accuracyService.getAccuracyBySymbol()));
    }

    @GetMapping("/sentiment/accuracy/calibration")
    public ResponseEntity<ApiResponse<List<SentimentAccuracyService.CalibrationData>>> getCalibration() {
        return ResponseEntity.ok(ApiResponse.ok(accuracyService.getCalibrationData()));
    }

    @GetMapping("/sentiment/accuracy/rolling-ic")
    public ResponseEntity<ApiResponse<List<SentimentAccuracyService.RollingICResult>>> getRollingIC(
            @RequestParam(defaultValue = "30") int windowDays) {
        return ResponseEntity.ok(ApiResponse.ok(accuracyService.getRollingIC(windowDays)));
    }

    @GetMapping("/sentiment/accuracy/signal-volume")
    public ResponseEntity<ApiResponse<SentimentAccuracyService.SignalVolumeStats>> getSignalVolume() {
        return ResponseEntity.ok(ApiResponse.ok(accuracyService.getSignalVolumeStats()));
    }

    @GetMapping("/sentiment/accuracy/ece")
    public ResponseEntity<ApiResponse<SentimentAccuracyService.ECEStats>> getECE() {
        return ResponseEntity.ok(ApiResponse.ok(accuracyService.getECEStats()));
    }

    // --- Job status & manual trigger ---

    @PostMapping("/sentiment/evaluate/trigger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerEvaluation() {
        evaluationJob.triggerEvaluation();
        Map<String, Object> data = Map.of(
            "message", "Evaluation triggered",
            "lastRun", evaluationJob.getLastRun(),
            "lastStatus", evaluationJob.getLastStatus(),
            "lastCount", evaluationJob.getLastCount()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/sentiment/evaluate/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJobStatus() {
        LocalDateTime lastRun = evaluationJob.getLastRun();
        String lastStatus = evaluationJob.getLastStatus();
        Integer lastCount = evaluationJob.getLastCount();
        Map<String, Object> data = Map.of(
            "lastRun", lastRun != null ? lastRun.toString() : null,
            "lastStatus", lastStatus != null ? lastStatus : "never run",
            "lastCount", lastCount != null ? lastCount : 0
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // --- Legacy endpoints ---

    @GetMapping("/news/{symbol}/latest")
    public ResponseEntity<ApiResponse<List<NewsArticle>>> getLatestNews(
            @PathVariable String symbol) {
        List<NewsArticle> news = newsService.fetchStockNews(symbol);
        return ResponseEntity.ok(ApiResponse.ok(news));
    }

    @GetMapping("/pdf/{symbol}/latest")
    public ResponseEntity<ApiResponse<EarningsData>> getLatestEarnings(
            @PathVariable String symbol) {
        EarningsData data = pdfExtractionRepo.findLatestBySymbol(symbol)
            .map(e -> EarningsData.fromJson(e.getExtractedJson()))
            .orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/sentiment/{symbol}/analyse")
    public ResponseEntity<ApiResponse<SentimentResult>> triggerAnalysis(
            @PathVariable String symbol) {
        SentimentResult result = sentimentService.analyzeStockSentiment(symbol, LocalDate.now());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/pdf/{symbol}/extract")
    public ResponseEntity<ApiResponse<EarningsData>> triggerPdfExtraction(
            @PathVariable String symbol,
            @RequestParam String url) {
        EarningsData data = pdfService.extractEarningsPdf(symbol, url);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
