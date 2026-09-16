package com.swingtrade.llm.service;

import com.swingtrade.llm.client.LlmClient;
import com.swingtrade.llm.client.LlamaCppClient;
import com.swingtrade.llm.client.SpringAiLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Provider that routes LLM requests to the correct backend.
 * Creates a SpringAiLlmClient backed by the OpenAiChatModel
 * selected by LlmBackendSelector at runtime.
 */
@Component
public class LlmClientProvider {

    private static final Logger logger = LoggerFactory.getLogger(LlmClientProvider.class);

    private final LlmBackendSelector selector;
    private final LlamaCppClient llamaCppClient;
    private final OpenAiChatModel localModel;
    private final OpenAiChatModel piSshModel;
    private final OpenAiChatModel openAiModel;
    private final OpenAiChatModel ollamaModel;

    public LlmClientProvider(LlmBackendSelector selector,
                             LlamaCppClient llamaCppClient,
                             @Qualifier("localChatModel") OpenAiChatModel localModel,
                             @Qualifier("piSshChatModel") OpenAiChatModel piSshModel,
                             @Qualifier("openAiChatModel") OpenAiChatModel openAiModel,
                             @Qualifier("ollamaChatModel") OpenAiChatModel ollamaModel) {
        this.selector = selector;
        this.llamaCppClient = llamaCppClient;
        this.localModel = localModel;
        this.piSshModel = piSshModel;
        this.openAiModel = openAiModel;
        this.ollamaModel = ollamaModel;
    }

    /**
     * Returns an LlmClient backed by the OpenAiChatModel
     * selected by the backend selector.
     */
    public LlmClient getClient() {
        if (selector.resolve() == LlmBackendSelector.Backend.PI_SSH) {
            logger.info("Using native llama.cpp HTTP client for PI_SSH backend");
            return llamaCppClient;
        }
        OpenAiChatModel model = switch (selector.resolve()) {
            case LOCAL -> localModel;
            case PI_SSH -> piSshModel;
            case OPENAI -> openAiModel;
            case OLLAMA -> ollamaModel;
        };
        if (model.getOptions() != null) {
            logger.info("Selected LLM backend {} with model {} at {}", selector.resolve(),
                    model.getOptions().getModel(), model.getOptions().getBaseUrl());
        }
        ChatClient chatClient = ChatClient.create(model);
        return new SpringAiLlmClient(chatClient, false);
    }

    public LlmBackendSelector.Backend getBackend() {
        return selector.resolve();
    }
}
