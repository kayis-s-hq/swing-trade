package com.swingtrade.api;

import com.swingtrade.api.dto.SignalResponse;
import com.swingtrade.api.service.SignalEngine;
import com.swingtrade.api.service.SignalService;
import com.swingtrade.api.service.TechnicalAnalysisService;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.store.SignalStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive unit tests for SignalService
 * Tests signal management functionality including retrieval and filtering
 */
@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @Mock
    private SignalStore signalStore;

    @Mock
    private SentimentStore sentimentStore;

    @Mock
    private TechnicalAnalysisService technicalAnalysisService;

    @Mock
    private SignalEngine signalEngine;

    @Mock
    private CandleStore candleStore;

    @InjectMocks
    private SignalService signalService;

    private final List<Signal> testSignals = List.of(
            createSignal("AAPL", "BUY", 0.85, LocalDate.now(), "Breakout above resistance"),
            createSignal("TSLA", "SELL", 0.72, LocalDate.now(), "Support level broken"),
            createSignal("MSFT", "BUY", 0.78, LocalDate.now(), "Moving average crossover"),
            createSignal("GOOGL", "BUY", 0.65, LocalDate.now(), "RSI oversold"),
            createSignal("AMZN", "SELL", 0.68, LocalDate.now(), "Resistance rejection")
    );

    private Signal createSignal(String symbol, String type, double confidence, LocalDate date, String reasoning) {
        return Signal.create(symbol, date, Signal.SignalType.valueOf(type),
                BigDecimal.valueOf(confidence), reasoning);
    }

    @BeforeEach
    void setUp() {
        lenient().when(signalStore.findAll()).thenReturn(new ArrayList<>(testSignals));
    }

    @Test
    void testGetLatestSignals_ReturnsNonEmptyList() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        assertNotNull(signals, "Signals list should not be null");
        assertFalse(signals.isEmpty(), "Signals list should not be empty");
        assertEquals(5, signals.size(), "Should return all 5 signals from test data");
    }

    @Test
    void testGetLatestSignals_ReturnsCorrectSignalTypes() {
        // Arrange - the service returns BUY and SELL signals
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Act & Assert
        assertTrue(signals.stream().anyMatch(s -> "BUY".equals(s.getSignalType().name())), "Should contain at least one BUY signal");
        assertTrue(signals.stream().anyMatch(s -> "SELL".equals(s.getSignalType().name())), "Should contain at least one SELL signal");
    }

    @Test
    void testGetLatestSignals_ValidatesSignalProperties() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        for (SignalResponse signal : signals) {
            assertNotNull(signal.getSymbol(), "Signal symbol should not be null");
            assertNotNull(signal.getSignalType(), "Signal type should not be null");
            assertNotNull(signal.getDate(), "Signal date should not be null");
            assertNotNull(signal.getReasoning(), "Signal reason should not be null");
            assertTrue("BUY".equalsIgnoreCase(signal.getSignalType().name()) || "SELL".equalsIgnoreCase(signal.getSignalType().name()),
                    "Signal type should be BUY or SELL");
        }
    }

    @Test
    void testGetLatestSignals_AllSignalsHaveReasons() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        for (SignalResponse signal : signals) {
            assertNotNull(signal.getReasoning());
            assertFalse(signal.getReasoning().trim().isEmpty(), "Signal reason should not be empty");
            assertTrue(signal.getReasoning().length() > 5, "Signal reason should have meaningful content");
        }
    }

    @Test
    void testGetLatestSignals_TimestampsAreRecent() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        LocalDate today = LocalDate.now();
        for (SignalResponse signal : signals) {
            assertNotNull(signal.getDate());
            // Timestamps should be today
            assertEquals(today, signal.getDate(), "Signal date should be today");
        }
    }

    @Test
    void testGetLatestSignals_ReturnsDistinctSymbols() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        List<String> symbols = signals.stream().map(SignalResponse::getSymbol).toList();
        assertTrue(symbols.stream().distinct().count() == symbols.size(),
                "All signals should have distinct symbols");
    }

    @Test
    void testGetLatestSignals_SymbolFormat() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        for (SignalResponse signal : signals) {
            assertNotNull(signal.getSymbol());
            assertTrue(signal.getSymbol().matches("[A-Z]+"),
                    "Symbol should consist of uppercase letters");
            assertTrue(signal.getSymbol().length() <= 5,
                    "Symbol should be reasonably short");
        }
    }

    @Test
    void testGetLatestSignals_ListIsMutable() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        assertDoesNotThrow(signals::clear,
                "Returned list should be mutable");
    }

    @Test
    void testGetLatestSignals_NoNullElements() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        assertFalse(signals.contains(null), "List should not contain null elements");
        assertEquals(signals.size(), signals.stream().filter(Objects::nonNull).count(),
                "All elements should be non-null");
    }
}
