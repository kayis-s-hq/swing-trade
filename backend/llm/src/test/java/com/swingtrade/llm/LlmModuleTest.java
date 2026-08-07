package com.swingtrade.llm;

import com.swingtrade.domain.Signal;
import com.swingtrade.llm.impl.LangChain4jLlmClient;
import com.swingtrade.llm.impl.NewsIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for LLM module functionality.
 * Tests the core components of the LLM integration for swing trading.
 */
public class LlmModuleTest {

    private LangChain4jLlmClient llmClient;
    private NewsIngestionService newsService;

    @BeforeEach
    void setUp() {
        llmClient = new LangChain4jLlmClient("http://localhost:8000");
        newsService = new NewsIngestionService();
    }

    @Test
    void testSentimentOutputCreation() {
        SentimentOutput result = new SentimentOutput(
            SentimentType.POSITIVE,
            "Test reasoning",
            0.85
        );

        assertEquals(SentimentType.POSITIVE, result.getSentiment());
        assertEquals("Test reasoning", result.getReasoning());
        assertEquals(0.85, result.getConfidence());
    }

    @Test
    void testDomainSignalCreation() {
        Signal signal = new Signal(
            null,
            "AAPL",
            LocalDate.now(),
            Signal.SignalType.BUY,
            BigDecimal.valueOf(0.9),
            "Positive market sentiment",
            null, null, null, null, null, LocalDate.now(), null, null
        );

        assertEquals("AAPL", signal.symbol());
        assertEquals(Signal.SignalType.BUY, signal.type());
        assertEquals("Positive market sentiment", signal.reasoning());
        assertEquals(BigDecimal.valueOf(0.9), signal.confidence());
        assertTrue(signal.isBuySignal());
    }

    @Test
    @org.junit.jupiter.api.Disabled("Legacy test - use NewsIngestionServiceTest instead")
    void testNewsIngestion() {
        List<String> news = newsService.fetchNews();
        assertNotNull(news);
        assertTrue(news.size() > 0);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Legacy test - requires live vLLM server, use SentimentAnalyzerTest instead")
    void testSentimentAnalysis() {
        // Test basic sentiment analysis functionality
        String sampleNews = "Company reports strong earnings growth and positive market outlook.";
        SentimentOutput result = llmClient.analyzeSentiment(sampleNews);

        assertNotNull(result);
        assertNotNull(result.getSentiment());
        assertNotNull(result.getReasoning());
        assertNotNull(result.getConfidence());
    }
}
