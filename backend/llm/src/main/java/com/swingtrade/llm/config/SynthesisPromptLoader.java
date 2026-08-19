package com.swingtrade.llm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SynthesisPromptLoader {

    private final String systemPrompt;
    private final String userPromptTemplate;

    public SynthesisPromptLoader(
            @Value("classpath:prompts/synthesis-system.md") Resource systemPromptResource,
            @Value("classpath:prompts/synthesis-user.md") Resource userPromptResource) {
        try {
            this.systemPrompt = new String(systemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            this.userPromptTemplate = new String(userPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load prompt files", e);
        }
    }

    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPromptTemplate() { return userPromptTemplate; }
}