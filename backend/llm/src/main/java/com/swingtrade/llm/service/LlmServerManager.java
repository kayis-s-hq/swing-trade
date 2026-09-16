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

    /**
     * Marks the start of an in-flight LLM request so the idle monitor won't stop
     * the server underneath it.
     *
     * <p>This can't be inferred from the server's own health endpoint: llama.cpp
     * serves multiple slots, so it answers {@code /health} perfectly happily while
     * one slot is mid-generation. Without explicit tracking, any request running
     * longer than {@code llamacpp.idle-timeout} had the server stopped out from
     * under it and failed with "Request failed".
     *
     * <p>Must be paired with {@link #endRequest()} in a finally block.
     */
    default void beginRequest() {
    }

    /**
     * Marks the end of an in-flight LLM request and refreshes the idle clock.
     */
    default void endRequest() {
    }
}
