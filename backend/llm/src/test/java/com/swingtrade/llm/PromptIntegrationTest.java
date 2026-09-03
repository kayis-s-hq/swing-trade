package com.swingtrade.llm;

import com.swingtrade.llm.config.SentimentPromptLoader;
import com.swingtrade.llm.config.SynthesisPromptLoader;
import com.swingtrade.llm.config.PdfExtractionPromptLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PromptIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
    "spring.main.web-application-type=none"
})
class PromptIntegrationTest {

    @Autowired
    private SentimentPromptLoader sentimentLoader;

    @Autowired
    private SynthesisPromptLoader synthesisLoader;

    @Autowired
    private PdfExtractionPromptLoader pdfLoader;

    @Test
    @DisplayName("All prompt loaders wired correctly")
    void allLoadersWired() {
        assertThat(sentimentLoader).isNotNull();
        assertThat(synthesisLoader).isNotNull();
        assertThat(pdfLoader).isNotNull();
    }

    @Test
    @DisplayName("All prompts are non-empty and well-formed")
    void allPromptsNonEmpty() {
        assertThat(sentimentLoader.getSystemPrompt()).isNotBlank();
        assertThat(sentimentLoader.getUserPrompt()).isNotBlank();
        assertThat(synthesisLoader.getSystemPrompt()).isNotBlank();
        assertThat(synthesisLoader.getUserPromptTemplate()).isNotBlank();
        assertThat(pdfLoader.getPrompt()).isNotBlank();
    }

    @Test
    @DisplayName("Sentiment user prompt uses {symbol} and {newsContent} placeholders")
    void sentimentPlaceholders() {
        String user = sentimentLoader.getUserPrompt();
        assertThat(user).contains("{symbol}");
        assertThat(user).contains("{newsContent}");

        String formatted = user.replace("{symbol}", "RELIANCE").replace("{newsContent}", "Reliance profits up 20%");
        assertThat(formatted).contains("RELIANCE");
        assertThat(formatted).contains("Reliance profits up 20%");
        assertThat(formatted).doesNotContain("{symbol}");
        assertThat(formatted).doesNotContain("{newsContent}");
    }

    @Test
    @DisplayName("Synthesis user prompt uses all expected placeholders")
    void synthesisPlaceholders() {
        String template = synthesisLoader.getUserPromptTemplate();

        String formatted = String.format(template,
            "RELIANCE", "2025-01-15",
            75, 10, "Strong outlook", "IPO catalyst", "SEBI probe",
            "BULLISH", 80, 0.85, "RSI: 65, MACD: positive",
            70, "BULLISH", "P/E: 25, ROE: 18%",
            5, 60.0, 1.5, 10.0, 25.0, 5.0,
            72, "BUY", 0.80, "Strong composite"
        );

        assertThat(formatted).contains("RELIANCE");
        assertThat(formatted).contains("BULLISH");
        assertThat(formatted).contains("2025-01-15");
        assertThat(formatted).doesNotContain("%s");
        assertThat(formatted).doesNotContain("%d");
    }

    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public SentimentPromptLoader sentimentPromptLoader() {
            return new SentimentPromptLoader(
                new ClassPathResource("prompts/sentiment-system.md"),
                new ClassPathResource("prompts/sentiment-user.md"));
        }

        @org.springframework.context.annotation.Bean
        public SynthesisPromptLoader synthesisPromptLoader() {
            return new SynthesisPromptLoader(
                new ClassPathResource("prompts/synthesis-system.md"),
                new ClassPathResource("prompts/synthesis-user.md"));
        }

        @org.springframework.context.annotation.Bean
        public PdfExtractionPromptLoader pdfExtractionPromptLoader() {
            return new PdfExtractionPromptLoader(
                new ClassPathResource("prompts/pdf-extraction.md"));
        }
    }
}