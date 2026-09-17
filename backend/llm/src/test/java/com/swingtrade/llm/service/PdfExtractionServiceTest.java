package com.swingtrade.llm.service;

import com.swingtrade.data.entity.PdfExtractionEntity;
import com.swingtrade.data.repository.PdfExtractionRepository;
import com.swingtrade.llm.config.PdfExtractionPromptLoader;
import com.swingtrade.llm.domain.EarningsData;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PdfExtractionServiceTest {

    @Test
    void unavailableEndpoint_skipsExtractionWithoutTouchingCollaborators() {
        PdfExtractionRepository repository = mock(PdfExtractionRepository.class);
        PdfExtractionPromptLoader promptLoader = mock(PdfExtractionPromptLoader.class);
        PdfExtractionService service = new PdfExtractionService(" ", "test-model", repository,
                WebClient.builder(), promptLoader);

        assertThat(service.isAvailable()).isFalse();
        assertThat(service.extractEarningsPdf("TCS", "https://example.test/report.pdf")).isNull();
        verifyNoInteractions(repository, promptLoader);
    }

    @Test
    void latestExtraction_convertsPersistedJsonToEarningsData() {
        PdfExtractionRepository repository = mock(PdfExtractionRepository.class);
        PdfExtractionEntity entity = new PdfExtractionEntity();
        entity.setExtractedJson("""
                {"symbol":"TCS","quarter":"Q2 FY27","revenue":"100.50","netProfit":20.25,"eps":4.05,"guidance":"stable"}
                """);
        when(repository.findLatestBySymbol("TCS")).thenReturn(Optional.of(entity));
        PdfExtractionService service = new PdfExtractionService("http://llm.test", "test-model",
                repository, WebClient.builder(), mock(PdfExtractionPromptLoader.class));

        EarningsData result = service.extractLatestEarnings("TCS");

        assertThat(result.symbol()).isEqualTo("TCS");
        assertThat(result.quarter()).isEqualTo("Q2 FY27");
        assertThat(result.revenue()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(result.netProfit()).isEqualByComparingTo("20.25");
        assertThat(result.guidance()).isEqualTo("stable");
    }

    @Test
    void latestExtraction_returnsNullWhenNoPersistedExtractionExists() {
        PdfExtractionRepository repository = mock(PdfExtractionRepository.class);
        when(repository.findLatestBySymbol("INFY")).thenReturn(Optional.empty());
        PdfExtractionService service = new PdfExtractionService("http://llm.test", "test-model",
                repository, WebClient.builder(), mock(PdfExtractionPromptLoader.class));

        assertThat(service.extractLatestEarnings("INFY")).isNull();
    }
}
