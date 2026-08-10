package com.swingtrade.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration class for LLM module.
 */
@Configuration
public class LlmConfig {

    @Bean
    public ExecutorService newsExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "news-fetcher");
            t.setDaemon(true);
            return t;
        });
    }
}