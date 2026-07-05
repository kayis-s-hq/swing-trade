package com.swingtrade.llm.config;

import com.swingtrade.llm.LlmClient;
import com.swingtrade.llm.impl.LangChain4jLlmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

/**
 * Configuration class for LLM module.
 * Sets up the LLM client with configurable base URL for vLLM endpoint.
 * Scheduled tasks moved to WeeklySectorDigestScheduler.
 */
@Configuration
public class LlmConfig {

    @Value("${llm.vllm.base-url:http://localhost:8000}")
    private String baseUrl;

    /**
     * Creates and configures the LLM client bean.
     */
    @Bean
    public LlmClient llmClient() {
        return new LangChain4jLlmClient(baseUrl);
    }
}
