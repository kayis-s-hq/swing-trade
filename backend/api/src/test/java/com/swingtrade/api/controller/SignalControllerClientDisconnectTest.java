package com.swingtrade.api.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@code SignalController.isClientDisconnect}, the predicate used
 * by {@code /api/signals/generate-all/stream} to decide whether to abandon the
 * rest of the batch.
 *
 * <p>Regression coverage for a bug where the predicate matched on
 * {@code instanceof IOException} anywhere in a cause chain, which misclassified
 * a routine business failure (e.g. a data-provider IOException) as "the client
 * went away" - abandoning every remaining symbol in the batch and never sending
 * a terminal COMPLETE event. Package-private visibility lets this test call it
 * directly; this predicate previously had no test coverage at all.</p>
 */
class SignalControllerClientDisconnectTest {

    @Test
    void plainIOException_withNoDisconnectMessage_isNotADisconnect() {
        // A data-provider IOException (e.g. Yahoo/Fyers network hiccup) must be
        // treated as a business failure for that symbol, not a client disconnect.
        assertThat(SignalController.isClientDisconnect(new IOException("Yahoo API returned 503"))).isFalse();
    }

    @Test
    void wrappedIOException_withNoDisconnectMessage_isNotADisconnect() {
        RuntimeException wrapped = new RuntimeException("signal generation failed",
                new IOException("connection timed out talking to LLM"));
        assertThat(SignalController.isClientDisconnect(wrapped)).isFalse();
    }

    @Test
    void brokenPipeMessage_isADisconnect() {
        assertThat(SignalController.isClientDisconnect(new IOException("Broken pipe"))).isTrue();
    }

    @Test
    void connectionResetMessage_isADisconnect() {
        assertThat(SignalController.isClientDisconnect(new IOException("Connection reset by peer"))).isTrue();
    }

    @Test
    void responseNotUsableMessage_isADisconnect() {
        assertThat(SignalController.isClientDisconnect(new IllegalStateException("Response not usable"))).isTrue();
    }

    @Test
    void disconnectMessageDeepInCauseChain_isStillDetected() {
        RuntimeException outer = new RuntimeException("send failed",
                new RuntimeException("io failure", new IOException("Broken pipe")));
        assertThat(SignalController.isClientDisconnect(outer)).isTrue();
    }

    @Test
    void nullMessage_isNotADisconnect() {
        assertThat(SignalController.isClientDisconnect(new IOException())).isFalse();
    }
}
