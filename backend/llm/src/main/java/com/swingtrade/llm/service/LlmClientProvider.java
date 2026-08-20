package com.swingtrade.llm.service;

import com.swingtrade.llm.client.LlmClient;
import com.swingtrade.llm.client.SpringAiLlmClient;
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

    private final LlmBackendSelector selector;
    private final OpenAiChatModel localModel;
    private final OpenAiChatModel piSshModel;
    private final OpenAiChatModel openAiModel;

    public LlmClientProvider(LlmBackendSelector selector,
                             @Qualifier("localChatModel") OpenAiChatModel localModel,
                             @Qualifier("piSshChatModel") OpenAiChatModel piSshModel,
                             @Qualifier("openAiChatModel") OpenAiChatModel openAiModel) {
        this.selector = selector;
        this.localModel = localModel;
        this.piSshModel = piSshModel;
        this.openAiModel = openAiModel;
    }

    /**
     * Returns an LlmClient backed by the OpenAiChatModel
     * selected by the backend selector.
     */
    public LlmClient getClient() {
        OpenAiChatModel model = switch (selector.resolve()) {
            case LOCAL -> localModel;
            case PI_SSH -> piSshModel;
            case OPENAI -> openAiModel;
        };
        ChatClient chatClient = ChatClient.create(model);
        return new SpringAiLlmClient(chatClient, false);
    }
}