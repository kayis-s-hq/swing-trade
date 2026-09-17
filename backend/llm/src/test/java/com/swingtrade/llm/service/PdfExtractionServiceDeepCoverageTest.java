package com.swingtrade.llm.service;

import com.swingtrade.data.entity.PdfExtractionEntity;
import com.swingtrade.data.repository.PdfExtractionRepository;
import com.swingtrade.llm.config.PdfExtractionPromptLoader;
import com.swingtrade.llm.domain.EarningsData;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfExtractionServiceDeepCoverageTest {

    private MockWebServer server;
    private PdfExtractionRepository repository;
    private PdfExtractionPromptLoader promptLoader;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        repository = mock(PdfExtractionRepository.class);
        promptLoader = mock(PdfExtractionPromptLoader.class);
        when(promptLoader.getPrompt()).thenReturn("Extract earnings as JSON");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void availableEndpoint_isReportedAsAvailable() {
        PdfExtractionService service = service();

        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void emptyPdfResponse_skipsLlmAndPersistence() {
        server.enqueue(new MockResponse().setResponseCode(200));
        PdfExtractionService service = service();

        assertThat(service.extractEarningsPdf("TCS", pdfUrl())).isNull();
        assertThat(server.getRequestCount()).isEqualTo(1);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidPdfResponse_returnsNullWithoutPersistence() {
        server.enqueue(new MockResponse().setResponseCode(500));
        PdfExtractionService service = service();

        assertThat(service.extractEarningsPdf("INFY", pdfUrl())).isNull();
        assertThat(server.getRequestCount()).isEqualTo(1);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void successfulWebClientExtraction_persistsResponseAndReturnsEarnings() throws Exception {
        String response = "{\"symbol\":\"TCS\",\"quarter\":\"Q2 FY27\","
                + "\"revenue\":100.50,\"netProfit\":20.25,\"eps\":4.05,"
                + "\"ebitda\":30,\"guidance\":\"stable\"}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody("%PDF-test"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(response));
        PdfExtractionService service = service();

        EarningsData result = service.extractEarningsPdf("TCS", pdfUrl());

        assertThat(result.symbol()).isEqualTo("TCS");
        assertThat(result.quarter()).isEqualTo("Q2 FY27");
        assertThat(result.revenue()).isEqualByComparingTo("100.50");
        assertThat(result.netProfit()).isEqualByComparingTo("20.25");
        assertThat(result.eps()).isEqualByComparingTo("4.05");
        assertThat(result.ebitda()).isEqualByComparingTo("30");
        assertThat(result.guidance()).isEqualTo("stable");

        RecordedRequest pdfRequest = server.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest llmRequest = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(pdfRequest.getMethod()).isEqualTo("GET");
        assertThat(pdfRequest.getPath()).isEqualTo("/report.pdf");
        assertThat(llmRequest.getMethod()).isEqualTo("POST");
        assertThat(llmRequest.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(llmRequest.getBody().readUtf8()).contains("Extract earnings as JSON");

        ArgumentCaptor<PdfExtractionEntity> captor = ArgumentCaptor.forClass(PdfExtractionEntity.class);
        verify(repository).save(captor.capture());
        PdfExtractionEntity saved = captor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("TCS");
        assertThat(saved.getDocumentType()).isEqualTo("earnings");
        assertThat(saved.getExtractedJson()).isEqualTo(response);
        assertThat(saved.getSourceUrl()).isEqualTo(pdfUrl());
        assertThat(saved.getModelUsed()).isEqualTo("test-model");
        assertThat(saved.getExtractionDate()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void malformedLlmJson_returnsNullWithoutPersistence() {
        server.enqueue(new MockResponse().setBody("%PDF-test"));
        server.enqueue(new MockResponse().setBody("not JSON"));
        PdfExtractionService service = service();

        assertThat(service.extractEarningsPdf("TCS", pdfUrl())).isNull();
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void markdownJson_isParsedAndMissingNumericFieldsRemainNull() {
        server.enqueue(new MockResponse().setBody("%PDF-test"));
        server.enqueue(new MockResponse().setBody("""
                Here is the extraction:
                ```json
                {"symbol":"INFY","quarter":"Q3 FY27","revenue":"200.00","guidance":"steady"}
                ```
                """));
        PdfExtractionService service = service();

        EarningsData result = service.extractEarningsPdf("INFY", pdfUrl());

        assertThat(result.symbol()).isEqualTo("INFY");
        assertThat(result.revenue()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.netProfit()).isNull();
        assertThat(result.eps()).isNull();
        assertThat(result.ebitda()).isNull();
        verify(repository).save(org.mockito.ArgumentMatchers.any(PdfExtractionEntity.class));
    }

    @Test
    void latestExtraction_returnsPersistedDataOrNullWhenAbsent() {
        PdfExtractionEntity entity = new PdfExtractionEntity();
        entity.setExtractedJson("""
                {"symbol":"RELIANCE","quarter":"Q4 FY26","revenue":300,"netProfit":50}
                """);
        when(repository.findLatestBySymbol("RELIANCE")).thenReturn(Optional.of(entity));
        when(repository.findLatestBySymbol("WIPRO")).thenReturn(Optional.empty());
        PdfExtractionService service = service();

        EarningsData latest = service.extractLatestEarnings("RELIANCE");

        assertThat(latest.symbol()).isEqualTo("RELIANCE");
        assertThat(latest.netProfit()).isEqualByComparingTo("50");
        assertThat(service.extractLatestEarnings("WIPRO")).isNull();
    }

    private PdfExtractionService service() {
        return new PdfExtractionService(server.url("/").toString(), "test-model", repository,
                WebClient.builder().baseUrl(server.url("/").toString()), promptLoader);
    }

    private String pdfUrl() {
        return server.url("/report.pdf").toString();
    }
}
