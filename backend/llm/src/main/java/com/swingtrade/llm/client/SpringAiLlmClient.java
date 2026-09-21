package com.swingtrade.llm.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
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

    private static final List<String> REASONING_METADATA_KEYS =
            List.of("reasoningContent", "reasoning", "reasoning_content");

    private final ChatClient chatClient;
    private final boolean enableCoT;
    private final String reasoningEffort;

    public SpringAiLlmClient(ChatClient chatClient, boolean enableCoT) {
        this(chatClient, enableCoT, null);
    }

    /**
     * @param reasoningEffort optional {@code reasoning_effort} sent to the backend
     *                        (for example {@code none} to stop Ollama thinking models
     *                        spending the whole token budget on reasoning); blank to omit
     */
    @org.springframework.beans.factory.annotation.Autowired
    public SpringAiLlmClient(ChatClient chatClient,
                             @org.springframework.beans.factory.annotation.Value("${llm.cot.enabled:false}") boolean enableCoT,
                             @org.springframework.beans.factory.annotation.Value("${llm.reasoning-effort:}") String reasoningEffort) {
        this.chatClient = chatClient;
        this.enableCoT = enableCoT;
        this.reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank()
                ? null : reasoningEffort.trim();
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

        // 0 chars after reasoning recovery is a failure, not an answer: one bounded
        // retry with double the token budget (a thinking model that ran out of tokens
        // mid-reasoning is the usual cause), then LlmUnavailableException so callers
        // take their explicit degraded path instead of parsing "".
        String result = attempt(effectiveSystemPrompt, userPrompt, maxTokens, temperature);
        if (result.isBlank()) {
            int retryTokens = maxTokens >= Integer.MAX_VALUE / 2 ? maxTokens : maxTokens * 2;
            logger.warn("LLM returned no usable text (maxTokens {}); retrying once with maxTokens {}",
                    maxTokens, retryTokens);
            result = attempt(effectiveSystemPrompt, userPrompt, retryTokens, temperature);
        }
        if (result.isBlank()) {
            return Mono.error(new LlmUnavailableException(
                    "LLM returned an empty response after one retry"));
        }
        return Mono.just(result);
    }

    private String attempt(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        // maxTokens/temperature must be set per-call via .options(), not just logged:
        // without this, no max_tokens is sent at all and llama-server falls back to
        // its own (effectively unbounded) default, so generation only stops at EOS
        // or by running the KV cache into the context ceiling — indistinguishable
        // from a genuine "prompt too long" 400 once the prompt leaves little headroom.
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .maxTokens(maxTokens)
                .temperature(temperature);
        if (reasoningEffort != null) {
            options.reasoningEffort(reasoningEffort);
        }

        // Get the full ChatResponse to handle the reasoning-field quirk
        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(options)
                .call()
                .chatResponse();

        String result = response.getResult().getOutput().getText();

        // Thinking models (vLLM, Ollama qwen3.5) can leave content empty and put text in
        // a reasoning field; recover a JSON object from it when present.
        if (result == null || result.isBlank()) {
            String reasoning = readReasoning(response.getResult().getOutput().getMetadata());
            if (reasoning != null && !reasoning.isBlank()) {
                result = extractJsonFromReasoning(reasoning);
                if (result != null) {
                    logger.debug("Extracted JSON from reasoning field ({} chars)", result.length());
                }
            }
        }

        logger.debug("Chat completion complete, received {} chars",
                result != null ? result.length() : 0);
        return result != null ? result : "";
    }

    private static String readReasoning(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        for (String key : REASONING_METADATA_KEYS) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
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
            new tools.jackson.databind.ObjectMapper().readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
