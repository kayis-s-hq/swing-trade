package com.swingtrade.llm.config;

import com.swingtrade.llm.PromptLoaderTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PromptLoaderTestConfig.class)
class SentimentPromptLoaderTest {

    @Autowired
    private SentimentPromptLoader loader;

    @Test
    @DisplayName("System prompt loads from classpath")
    void systemPromptLoads() throws IOException {
        String prompt = loader.getSystemPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("financial analyst");
        assertThat(prompt).contains("Indian equity markets");
        assertThat(prompt).contains("{symbol}");
    }

    @Test
    @DisplayName("User prompt loads from classpath")
    void userPromptLoads() throws IOException {
        String prompt = loader.getUserPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("{symbol}");
        assertThat(prompt).contains("{newsContent}");
        assertThat(prompt).contains("swing trade entry");
    }

    @Test
    @DisplayName("System prompt matches old hardcoded content")
    void systemPromptMatchesHardcoded() throws IOException {
        String loaded = loader.getSystemPrompt();
        assertThat(loaded).contains("financial analyst specialising in Indian equity markets");
        assertThat(loaded).contains("earnings momentum");
        assertThat(loaded).contains("FII/DII activity");
        assertThat(loaded).contains("CRITICAL: Respond with ONLY a JSON object");
        assertThat(loaded).contains("\"score\": \"POSITIVE|NEUTRAL|NEGATIVE\"");
        assertThat(loaded).contains("\"confidence\": 0.0-1.0");
        assertThat(loaded).contains("\"red_flags\"");
        assertThat(loaded).contains("\"catalysts\"");
    }

    @Test
    @DisplayName("User prompt contains required placeholders")
    void userPromptPlaceholders() throws IOException {
        String prompt = loader.getUserPrompt();
        assertThat(prompt).contains("{symbol}");
        assertThat(prompt).contains("{newsContent}");
        assertThat(prompt).contains("swing trade entry");
        assertThat(prompt).contains("1-4 week");
    }
}