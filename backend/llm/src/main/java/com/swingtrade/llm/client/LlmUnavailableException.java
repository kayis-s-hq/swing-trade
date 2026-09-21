package com.swingtrade.llm.client;

/**
 * Thrown when an LLM backend cannot produce a usable answer, for example when it
 * returns no text even after a bounded retry. Callers fall back to a degraded
 * (non-LLM) path and must record that the fallback was used.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
