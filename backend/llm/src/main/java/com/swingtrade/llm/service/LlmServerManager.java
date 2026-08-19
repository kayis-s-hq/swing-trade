package com.swingtrade.llm.service;

/**
 * Unified interface for LLM server lifecycle management.
 * Implemented by LlamaCppServerManager (local) and PiLlamaServerManager (remote SSH).
 */
public interface LlmServerManager {

    /**
     * Ensures the LLM server is running. Blocks until ready.
     * If already running, returns immediately.
     *
     * @throws IllegalStateException if the server fails to start
     */
    void ensureRunning();

    /**
     * Stops the LLM server process.
     */
    void stop();

    /**
     * Checks if the server process is running.
     */
    boolean isRunning();

    /**
     * Restarts the server with updated configuration.
     */
    void restart();
}