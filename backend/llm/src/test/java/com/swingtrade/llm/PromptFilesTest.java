package com.swingtrade.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

class PromptFilesTest {

    private static final Set<String> REQUIRED_PROMPTS = Set.of(
        "sentiment-system.md",
        "sentiment-user.md",
        "sentiment-user-earnings.md",
        "synthesis-system.md",
        "synthesis-user.md",
        "pdf-extraction.md",
        "multi-article-sentiment.md",
        "combined-analysis.md",
        "trading-recommendation.md",
        "simple-classification.md"
    );

    @Test
    @DisplayName("All required prompt files exist on classpath")
    void allPromptFilesExist() throws IOException {
        Path promptsDir = Paths.get("src/main/resources/prompts");
        if (!Files.exists(promptsDir)) {
            throw new AssertionError("prompts/ directory does not exist");
        }

        Set<String> existing = Files.list(promptsDir)
            .map(p -> p.getFileName().toString())
            .collect(java.util.stream.Collectors.toSet());

        java.util.List<String> missing = REQUIRED_PROMPTS.stream()
            .filter(p -> !existing.contains(p))
            .toList();

        if (!missing.isEmpty()) {
            throw new AssertionError("Missing prompt files: " + missing);
        }
    }

    @Test
    @DisplayName("All prompt files are non-empty")
    void allPromptFilesNonEmpty() throws IOException {
        Path promptsDir = Paths.get("src/main/resources/prompts");

        for (Path file : Files.list(promptsDir).toList()) {
            if (Files.size(file) == 0) {
                throw new AssertionError("Empty prompt file: " + file.getFileName());
            }
        }
    }
}