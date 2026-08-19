package com.swingtrade.llm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PdfExtractionPromptLoaderTest.TestConfig.class)
@TestPropertySource(properties = {
    "spring.main.web-application-type=none"
})
class PdfExtractionPromptLoaderTest {

    @Autowired
    private PdfExtractionPromptLoader loader;

    @Test
    @DisplayName("PDF extraction prompt loads from classpath")
    void promptLoads() {
        String prompt = loader.getPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("Extract financial data");
        assertThat(prompt).contains("earnings document");
        assertThat(prompt).contains("\"revenue\"");
        assertThat(prompt).contains("\"netProfit\"");
        assertThat(prompt).contains("INR values");
    }

    @Test
    @DisplayName("PDF extraction prompt matches old hardcoded content")
    void promptMatchesHardcoded() {
        String prompt = loader.getPrompt();
        assertThat(prompt).contains("Extract financial data from this earnings document");
        assertThat(prompt).contains("\"eps\": 42.50");
        assertThat(prompt).contains("\"ebitda\": 95000000000");
        assertThat(prompt).contains("No other text");
    }

    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public PdfExtractionPromptLoader pdfExtractionPromptLoader() {
            return new PdfExtractionPromptLoader(
                new ClassPathResource("prompts/pdf-extraction.md"));
        }
    }
}