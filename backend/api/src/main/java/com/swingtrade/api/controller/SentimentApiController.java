package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.repository.NewsItemRepository;
import com.swingtrade.data.repository.PdfExtractionRepository;
import com.swingtrade.data.repository.SentimentResultRepository;
import com.swingtrade.data.service.SentimentAccuracyService;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.llm.domain.EarningsData;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.PdfExtractionService;
import com.swingtrade.llm.service.SentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for sentiment analysis, news, PDF extraction, and accuracy tracking.
 */
@RestController
@RequestMapping("/api")
public class SentimentApiController {

    private static final Logger log = LoggerFactory.getLogger(SentimentApiController.class);

    private final SentimentService sentimentService;
    private final NewsIngestionService newsService;
    private final PdfExtractionService pdfService;
    private final SentimentAccuracyService accuracyService;
    private final SentimentResultRepository sentimentRepo;
    private final PdfExtractionRepository pdfExtractionRepo;

    public SentimentApiController(SentimentService sentimentService,
                                  NewsIngestionService newsService,
                                  PdfExtractionService pdfService,
                                  SentimentAccuracyService accuracyService,
                                  SentimentResultRepository sentimentRepo,
                                  NewsItemRepository newsItemRepo,
                                  PdfExtractionRepository pdfExtractionRepo) {
        this.sentimentService = sentimentService;
        this.newsService = newsService;
        this.pdfService = pdfService;
        this.accuracyService = accuracyService;
        this.sentimentRepo = sentimentRepo;
        this.pdfExtractionRepo = pdfExtractionRepo;
    }

    @GetMapping("/sentiment/{symbol}/latest")
    public ResponseEntity<ApiResponse<SentimentResult>> getLatestSentiment(
            @PathVariable String symbol) {
        return sentimentRepo.findLatestBySymbol(symbol)
            .map(e -> ResponseEntity.ok(ApiResponse.ok(e.toDomain())))
            .orElse(ResponseEntity.ok(ApiResponse.ok(SentimentResult.create(
                symbol, java.time.LocalDate.now(),
                SentimentResult.SentimentScore.NEUTRAL, "No data", "", 0.0,
                List.of(), List.of()))));
    }

    @GetMapping("/sentiment/{symbol}/history")
    public ResponseEntity<ApiResponse<List<SentimentResult>>> getSentimentHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("date").descending());
        List<SentimentResult> results = sentimentRepo.findAllBySymbol(symbol, pageable)
            .stream().map(SentimentResultEntity::toDomain).toList();
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

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

    @GetMapping("/news/{symbol}/latest")
    public ResponseEntity<ApiResponse<List<NewsIngestionService.NewsArticle>>> getLatestNews(
            @PathVariable String symbol) {
        List<NewsIngestionService.NewsArticle> news = newsService.fetchAllNews(symbol);
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
        List<NewsIngestionService.NewsArticle> news = newsService.fetchAllNews(symbol);
        List<String> headlines = news.stream()
            .map(newsService::cleanNewsText)
            .toList();
        SentimentResult result = sentimentService.analyseSentiment(symbol, headlines, null);
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