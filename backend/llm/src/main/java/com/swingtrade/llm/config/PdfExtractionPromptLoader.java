package com.swingtrade.llm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PdfExtractionPromptLoader {

    private final String prompt;

    public PdfExtractionPromptLoader(@Value("classpath:prompts/pdf-extraction.md") Resource pdfExtractionResource) {
        try {
            this.prompt = new String(pdfExtractionResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load pdf-extraction prompt", e);
        }
    }

    public String getPrompt() { return prompt; }
}