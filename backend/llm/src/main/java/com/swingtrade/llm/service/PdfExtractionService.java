package com.swingtrade.llm.service;

import com.swingtrade.data.entity.PdfExtractionEntity;
import com.swingtrade.data.repository.PdfExtractionRepository;
import com.swingtrade.llm.config.PdfExtractionPromptLoader;
import com.swingtrade.llm.domain.EarningsData;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Extracts earnings data from PDF documents via LLM endpoint.
 */
@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    private final WebClient webClient;
    private final String pdfBaseUrl;
    private final String pdfModel;
    private final PdfExtractionRepository pdfRepo;
    private final PdfExtractionPromptLoader promptLoader;

    public PdfExtractionService(
            @Value("${llm.pdf.base-url:}") String pdfBaseUrl,
            @Value("${llm.pdf.model:gemma-4-E2B}") String pdfModel,
            PdfExtractionRepository pdfRepo,
            Builder webClientBuilder,
            PdfExtractionPromptLoader promptLoader) {
        this.pdfBaseUrl = pdfBaseUrl;
        this.pdfModel = pdfModel;
        this.pdfRepo = pdfRepo;
        this.webClient = webClientBuilder
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.promptLoader = promptLoader;
    }

    /**
     * Extracts earnings data from a PDF at the given URL.
     */
    public EarningsData extractEarningsPdf(String symbol, String pdfUrl) {
        if (!isAvailable()) {
            log.warn("PDF extraction endpoint not configured — skipping {}", symbol);
            return null;
        }

        try {
            byte[] pdfBytes = webClient.get()
                .uri(pdfUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(30));

            if (pdfBytes == null || pdfBytes.length == 0) {
                log.warn("Empty PDF response for {}", symbol);
                return null;
            }

            String base64 = Base64.getEncoder().encodeToString(pdfBytes);

            String prompt = promptLoader.getPrompt();

            String llmResponse = webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(Map.of(
                    "model", pdfModel,
                    "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", 1024,
                    "temperature", 0.0,
                    "response_format", Map.of("type", "json_object")
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));

            if (llmResponse == null || llmResponse.isBlank()) {
                return null;
            }

            EarningsData earnings = parseLlmResponse(llmResponse);
            if (earnings == null) return null;

            // Store extraction
            PdfExtractionEntity entity = new PdfExtractionEntity();
            entity.setSymbol(symbol);
            entity.setDocumentType("earnings");
            entity.setExtractedJson(llmResponse);
            entity.setSourceUrl(pdfUrl);
            entity.setExtractionDate(java.time.LocalDate.now());
            entity.setModelUsed(pdfModel);
            entity.setCreatedAt(java.time.LocalDateTime.now());
            pdfRepo.save(entity);

            return earnings;

        } catch (Exception e) {
            log.error("PDF extraction failed for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public EarningsData extractLatestEarnings(String symbol) {
        return pdfRepo.findLatestBySymbol(symbol)
            .map(e -> EarningsData.fromJson(e.getExtractedJson()))
            .orElse(null);
    }

    public boolean isAvailable() {
        return pdfBaseUrl != null && !pdfBaseUrl.isBlank();
    }

    private EarningsData parseLlmResponse(String response) {
        try {
            // Extract JSON from possible markdown code blocks
            String json = response;
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            tools.jackson.databind.ObjectMapper mapper =
                new tools.jackson.databind.ObjectMapper();
            tools.jackson.databind.JsonNode root = mapper.readTree(json);

            return new com.swingtrade.llm.domain.EarningsData(
                root.has("symbol") ? root.get("symbol").asText() : null,
                root.has("quarter") ? root.get("quarter").asText() : null,
                toBigDecimal(root, "revenue"),
                toBigDecimal(root, "netProfit"),
                toBigDecimal(root, "eps"),
                toBigDecimal(root, "ebitda"),
                root.has("guidance") ? root.get("guidance").asText() : null,
                java.time.LocalDate.now()
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM PDF response: {}", e.getMessage());
            return null;
        }
    }

    private java.math.BigDecimal toBigDecimal(tools.jackson.databind.JsonNode root, String field) {
        if (!root.has(field)) return null;
        JsonNode node = root.get(field);
        if (node.isBigDecimal()) return node.decimalValue();
        if (node.isDouble()) return java.math.BigDecimal.valueOf(node.doubleValue());
        if (node.isLong()) return java.math.BigDecimal.valueOf(node.longValue());
        if (node.isInt()) return java.math.BigDecimal.valueOf(node.intValue());
        if (node.isTextual()) return new java.math.BigDecimal(node.asText());
        return null;
    }
}