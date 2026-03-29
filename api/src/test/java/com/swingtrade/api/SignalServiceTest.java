package com.swingtrade.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SignalService
 * Tests signal management functionality including retrieval and filtering
 */
@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @InjectMocks
    private SignalService signalService;

    private List<SignalService.Signal> testSignals;

    @BeforeEach
    void setUp() {
        // Set up test data using SignalService.Signal
        testSignals = new ArrayList<>();
        testSignals.add(new SignalService.Signal("AAPL", "BUY", 0.85, LocalDate.now(), "Breakout above resistance"));
        testSignals.add(new SignalService.Signal("TSLA", "SELL", 0.72, LocalDate.now(), "Support level broken"));
        testSignals.add(new SignalService.Signal("MSFT", "BUY", 0.78, LocalDate.now(), "Moving average crossover"));
        testSignals.add(new SignalService.Signal("GOOGL", "BUY", 0.65, LocalDate.now(), "RSI oversold"));
        testSignals.add(new SignalService.Signal("AMZN", "SELL", 0.68, LocalDate.now(), "Resistance rejection"));
    }

    @Test
    void testGetLatestSignals_ReturnsNonEmptyList() {
        // Act
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        assertNotNull(signals, "Signals list should not be null");
        assertFalse(signals.isEmpty(), "Signals list should not be empty");
        assertEquals(2, signals.size(), "Should return exactly 2 signals");
    }

    @Test
    void testGetLatestSignals_ReturnsCorrectSignalTypes() {
        // Arrange - the service returns BUY and SELL signals
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Act & Assert
        assertTrue(signals.stream().anyMatch(s -> "BUY".equals(s.getType().name())), "Should contain at least one BUY signal");
        assertTrue(signals.stream().anyMatch(s -> "SELL".equals(s.getType().name())), "Should contain at least one SELL signal");
    }

    @Test
    void testGetLatestSignals_ValidatesSignalProperties() {
        // Act
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        for (SignalService.Signal signal : signals) {
            assertNotNull(signal.getSymbol(), "Signal symbol should not be null");
            assertNotNull(signal.getType(), "Signal type should not be null");
            assertNotNull(signal.getDate(), "Signal date should not be null");
            assertNotNull(signal.getReasoning(), "Signal reason should not be null");
            assertTrue("BUY".equalsIgnoreCase(signal.getType().name()) || "SELL".equalsIgnoreCase(signal.getType().name()),
                    "Signal type should be BUY or SELL");
        }
    }

    @Test
    void testGetLatestSignals_AllSignalsHaveReasons() {
        // Act
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        for (SignalService.Signal signal : signals) {
            assertNotNull(signal.getReasoning());
            assertFalse(signal.getReasoning().trim().isEmpty(), "Signal reason should not be empty");
            assertTrue(signal.getReasoning().length() > 5, "Signal reason should have meaningful content");
        }
    }

    @Test
    void testGetLatestSignals_TimestampsAreRecent() {
        // Act
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        LocalDate today = LocalDate.now();
        for (SignalService.Signal signal : signals) {
            assertNotNull(signal.getDate());
            // Timestamps should be today
            assertEquals(today, signal.getDate(), "Signal date should be today");
        }
    }

    @Test
    void testGetLatestSignals_ReturnsDistinctSymbols() {
        // Act
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        List<String> symbols = signals.stream().map(SignalService.Signal::getSymbol).toList();
        assertTrue(symbols.stream().distinct().count() == symbols.size(),
                "All signals should have distinct symbols");
    }

    @Test
    void testGetLatestSignals_SymbolFormat() {
        // Act
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        for (SignalService.Signal signal : signals) {
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
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        assertDoesNotThrow(() -> signals.clear(),
                "Returned list should be mutable");
    }

    @Test
    void testGetLatestSignals_NoNullElements() {
        // Act
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        assertFalse(signals.contains(null), "List should not contain null elements");
        assertEquals(signals.size(), signals.stream().filter(s -> s != null).count(),
                "All elements should be non-null");
    }
}
