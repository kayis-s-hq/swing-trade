package com.swingtrade.llm.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Spring AI-based implementation of LlmClient.
 * Uses ChatClient with OpenAiChatModel for any /v1-compatible endpoint.
 *
 * Handles the vLLM + Claude reasoning quirk: when content is null/empty,
 * extracts JSON from the reasoning field using brace-counting.
 *
 * Supports Chain of Thought (CoT) via system prompt injection.
 */
@Component
public class SpringAiLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(SpringAiLlmClient.class);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    private final ChatClient chatClient;
    private final boolean enableCoT;

    public SpringAiLlmClient(ChatClient chatClient,
                             @org.springframework.beans.factory.annotation.Value("${llm.cot.enabled:false}") boolean enableCoT) {
        this.chatClient = chatClient;
        this.enableCoT = enableCoT;
    }

    @Override
    public Mono<String> generateChatCompletion(List<Map<String, String>> messages,
                                                int maxTokens,
                                                double temperature) {
        String systemPrompt = extractSystemPrompt(messages);
        String userPrompt = extractUserPrompt(messages);

        String effectiveSystemPrompt = systemPrompt;
        if (enableCoT) {
            effectiveSystemPrompt = addCoTInstructions(systemPrompt);
        }

        logger.debug("Generating chat completion (CoT: {}, maxTokens: {}, temperature: {})",
                enableCoT, maxTokens, temperature);

        var promptBuilder = chatClient.prompt()
                .system(effectiveSystemPrompt)
                .user(userPrompt);

        // Get the full ChatResponse to handle vLLM reasoning field quirk
        var response = promptBuilder
                .call()
                .chatResponse();

        String result = response.getResult().getOutput().getText();

        // vLLM reasoning field fix: if content is empty, extract JSON from reasoning metadata
        if (result == null || result.isBlank()) {
            Object reasoningObj = response.getResult().getOutput().getMetadata().get("reasoningContent");
            String reasoning = (reasoningObj != null) ? reasoningObj.toString() : null;
            if (reasoning != null && !reasoning.isBlank()) {
                result = extractJsonFromReasoning(reasoning);
                if (result != null) {
                    logger.debug("Extracted JSON from reasoning field ({} chars)", result.length());
                }
            }
        }

        logger.debug("Chat completion complete, received {} chars",
                result != null ? result.length() : 0);

        return Mono.just(result != null ? result : "");
    }

    /**
     * Extracts the system prompt from the messages list.
     */
    private String extractSystemPrompt(List<Map<String, String>> messages) {
        return messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts the user prompt from the messages list.
     */
    private String extractUserPrompt(List<Map<String, String>> messages) {
        return messages.stream()
                .filter(m -> "user".equals(m.get("role")))
                .map(m -> m.get("content"))
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse("");
    }

    /**
     * Adds Chain of Thought instructions to the system prompt.
     * Instructs the model to think step-by-step before providing its final answer.
     */
    private String addCoTInstructions(String systemPrompt) {
        String cotInstruction = """

                PROCESS:
                1. Think step-by-step about the analysis
                2. Consider multiple perspectives
                3. Identify key factors and their implications
                4. Synthesize your reasoning into a clear conclusion
                5. Place your final JSON response after your reasoning""";
        return systemPrompt + cotInstruction;
    }

    /**
     * Extracts JSON from reasoning text that wraps it.
     * Strips reasoning tags and markdown code blocks, then uses brace-counting
     * to find the matching closing brace of the JSON object.
     *
     * This handles the vLLM + Claude quirk where the model puts everything
     * in the reasoning field while content is None.
     */
    String extractJsonFromReasoning(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String cleaned = text;
        cleaned = cleaned.replaceAll("(?s)<thinking>.*?</thinking>\\s*", "");
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>\\s*", "");
        cleaned = cleaned.replaceAll("(?s)<reasoning>.*?</reasoning>\\s*", "");
        cleaned = cleaned.replaceAll("(?m)^```(?:json)?$\\s*", "");

        int open = findFirstBrace(cleaned, '{', 0);
        if (open < 0) {
            return null;
        }

        int balance = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = open; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (ch == '{') {
                    balance++;
                } else if (ch == '}') {
                    balance--;
                    if (balance == 0) {
                        String candidate = cleaned.substring(open, i + 1);
                        if (isValidJson(candidate)) {
                            return candidate;
                        }
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private int findFirstBrace(String text, char c, int from) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = from; i < text.length(); i++) {
            if (escaped) {
                escaped = false;
                continue;
            }
            char ch = text.charAt(i);
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (!inString && ch == c) {
                return i;
            }
        }
        return -1;
    }

    private boolean isValidJson(String text) {
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}