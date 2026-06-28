package com.swingtrade.llm.config;

import com.swingtrade.llm.LlmClient;
import com.swingtrade.llm.impl.LangChain4jLlmClient;
import com.swingtrade.llm.service.SentimentAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration class for LLM module.
 * Sets up the LLM client with configurable base URL for vLLM endpoint.
 * Enables scheduled tasks for weekly sector digest.
 */
@Configuration
@EnableScheduling
public class LlmConfig {

    private static final Logger logger = LoggerFactory.getLogger(LlmConfig.class);

    @Value("${llm.vllm.base-url:http://localhost:8000}")
    private String baseUrl;

    @Autowired(required = false)
    private SentimentAnalysisService sentimentAnalysisService;

    /**
     * Creates and configures the LLM client bean.
     *
     * @return Configured LLM client instance
     */
    @Bean
    public LlmClient llmClient() {
        return new LangChain4jLlmClient(baseUrl);
    }

    /**
     * Scheduled job to generate and send weekly sector digest every Sunday at 17:00 IST.
     * Cron: 0 0 11 * * SUN (11:00 UTC = 17:00 IST)
     */
    @Scheduled(cron = "0 0 11 * * SUN", zone = "Asia/Kolkata")
    public void sendWeeklySectorDigest() {
        if (sentimentAnalysisService == null) {
            logger.warn("SentimentAnalysisService not available for weekly digest generation");
            return;
        }

        logger.info("Starting weekly sector digest generation");
        try {
            // Generate sector digest for the past week
            String digest = sentimentAnalysisService.generateSectorDigestForLastWeek();
            logger.info("Weekly sector digest generated successfully: {} characters", digest.length());
        } catch (Exception e) {
            logger.error("Error sending weekly sector digest", e);
            // Don't rethrow - scheduler should continue
        }
    }
}
