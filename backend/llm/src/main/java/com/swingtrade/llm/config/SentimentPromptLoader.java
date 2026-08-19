package com.swingtrade.llm.config;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SentimentPromptLoader {

    private final String systemPrompt;
    private final String userPrompt;

    public SentimentPromptLoader(
            @Value("classpath:prompts/sentiment-system.md") Resource systemPromptResource,
            @Value("classpath:prompts/sentiment-user.md") Resource userPromptResource) {
        try {
            this.systemPrompt = new String(systemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            this.userPrompt = new String(userPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt resources", e);
        }
    }

    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
}