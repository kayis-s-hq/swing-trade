package com.swingtrade.llm;

import com.swingtrade.domain.Signal;
import com.swingtrade.llm.client.LlamaCppClient;
import com.swingtrade.llm.service.SentimentOutput;
import com.swingtrade.llm.service.SentimentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for LLM module functionality.
 */
@ExtendWith(MockitoExtension.class)
public class LlmModuleTest {

    @Mock
    private LlamaCppClient llamaCppClient;

    @Test
    void testSentimentOutputCreation() {
        SentimentOutput result = new SentimentOutput(
            SentimentType.POSITIVE,
            "Test reasoning",
            0.85
        );

        assertEquals(SentimentType.POSITIVE, result.getSentiment());
        assertEquals("Test reasoning", result.getReasoning());
        assertEquals(0.85, result.getConfidence());
    }

    @Test
    void testDomainSignalCreation() {
        Signal signal = new Signal(
            null,
            "AAPL",
            LocalDate.now(),
            Signal.SignalType.BUY,
            BigDecimal.valueOf(0.9),
            "Positive market sentiment",
            null, null, null, null, null, LocalDate.now(), null, null
        );

        assertEquals("AAPL", signal.symbol());
        assertEquals(Signal.SignalType.BUY, signal.type());
        assertEquals("Positive market sentiment", signal.reasoning());
        assertEquals(BigDecimal.valueOf(0.9), signal.confidence());
        assertTrue(signal.isBuySignal());
    }
}