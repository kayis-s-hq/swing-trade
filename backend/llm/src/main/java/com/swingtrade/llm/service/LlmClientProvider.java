package com.swingtrade.llm.service;

import com.swingtrade.llm.client.GpuHubLlmClient;
import com.swingtrade.llm.client.LlamaCppClient;
import com.swingtrade.llm.client.LlmClient;
import org.springframework.stereotype.Component;

/**
 * Provider that returns the correct LlmClient based on the selected backend.
 */
@Component
public class LlmClientProvider {

    private final LlmBackendSelector selector;
    private final LlamaCppClient llamaCppClient;
    private final GpuHubLlmClient gpuHubLlmClient;

    public LlmClientProvider(LlmBackendSelector selector,
                             LlamaCppClient llamaCppClient,
                             GpuHubLlmClient gpuHubLlmClient) {
        this.selector = selector;
        this.llamaCppClient = llamaCppClient;
        this.gpuHubLlmClient = gpuHubLlmClient;
    }

    /**
     * Returns the LlmClient for the currently selected backend.
     */
    public LlmClient getClient() {
        var backend = selector.resolve();
        return switch (backend) {
            case LOCAL, PI_SSH -> llamaCppClient;
            case GPUHUB -> gpuHubLlmClient;
        };
    }
}