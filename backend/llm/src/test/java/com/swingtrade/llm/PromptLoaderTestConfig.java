package com.swingtrade.llm;

import com.swingtrade.llm.config.SentimentPromptLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

@Configuration
public class PromptLoaderTestConfig {

    @Bean
    public SentimentPromptLoader sentimentPromptLoader() throws Exception {
        Resource systemResource = new ClassPathResource("prompts/sentiment-system.md");
        Resource userResource = new ClassPathResource("prompts/sentiment-user.md");
        return new SentimentPromptLoader(systemResource, userResource);
    }
}