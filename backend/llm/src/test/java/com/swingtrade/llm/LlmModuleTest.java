package com.swingtrade.llm;

import com.swingtrade.llm.impl.LangChain4jLlmClient;
import com.swingtrade.llm.impl.NewsIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void testSentimentAnalysisResultCreation() {
        SentimentAnalysisResult result = new SentimentAnalysisResult(
            SentimentType.POSITIVE, 
            "Test reasoning", 
            0.85
        );
        
        assertEquals(SentimentType.POSITIVE, result.getSentiment());
        assertEquals("Test reasoning", result.getReasoning());
        assertEquals(0.85, result.getConfidence());
    }
    
    @Test
    void testTechnicalSignalCreation() {
        TechnicalSignal signal = new TechnicalSignal(
            "AAPL",
            "BUY",
            "Positive market sentiment",
            java.time.LocalDateTime.now(),
            0.9
        );
        
        assertEquals("AAPL", signal.getSymbol());
        assertEquals("BUY", signal.getSignalType());
        assertEquals("Positive market sentiment", signal.getReason());
        assertNotNull(signal.getTimestamp());
        assertEquals(0.9, signal.getStrength());
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
        SentimentAnalysisResult result = llmClient.analyzeSentiment(sampleNews);

        assertNotNull(result);
        assertNotNull(result.getSentiment());
        assertNotNull(result.getReasoning());
        assertNotNull(result.getConfidence());
    }
}
