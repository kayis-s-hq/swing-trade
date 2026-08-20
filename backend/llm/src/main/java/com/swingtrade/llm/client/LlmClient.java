package com.swingtrade.llm.client;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Unified interface for LLM chat completion clients.
 * Single implementation: SpringAiLlmClient (handles all backends via Spring AI OpenAiChatModel).
 */
public interface LlmClient {

    /**
     * Generates a chat completion from the LLM.
     *
     * @param messages  list of message objects with "role" and "content" keys
     * @param maxTokens maximum tokens in the response
     * @param temperature sampling temperature
     * @return response content as a Mono
     */
    Mono<String> generateChatCompletion(List<Map<String, String>> messages,
                                        int maxTokens,
                                        double temperature);
}