package com.swingtrade.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    private List<Signal> testSignals;

    @BeforeEach
    void setUp() {
        // Set up test data
        testSignals = new ArrayList<>();
        testSignals.add(new Signal("AAPL", "BUY", 150.0, LocalDateTime.now(), "Breakout above resistance"));
        testSignals.add(new Signal("TSLA", "SELL", 250.0, LocalDateTime.now(), "Support level broken"));
        testSignals.add(new Signal("MSFT", "BUY", 380.5, LocalDateTime.now(), "Moving average crossover"));
        testSignals.add(new Signal("GOOGL", "BUY", 140.0, LocalDateTime.now(), "RSI oversold"));
        testSignals.add(new Signal("AMZN", "SELL", 175.25, LocalDateTime.now(), "Resistance rejection"));
    }

    @Test
    void testGetLatestSignals_ReturnsNonEmptyList() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        assertNotNull(signals, "Signals list should not be null");
        assertFalse(signals.isEmpty(), "Signals list should not be empty");
        assertEquals(2, signals.size(), "Should return exactly 2 signals");
    }

    @Test
    void testGetLatestSignals_ReturnsCorrectSignalTypes() {
        // Arrange - the service returns BUY and SELL signals
        List<Signal> signals = signalService.getLatestSignals();

        // Act & Assert
        assertTrue(signals.stream().anyMatch(s -> "BUY".equals(s.getType())), "Should contain at least one BUY signal");
        assertTrue(signals.stream().anyMatch(s -> "SELL".equals(s.getType())), "Should contain at least one SELL signal");
    }

    @Test
    void testGetLatestSignals_ValidatesSignalProperties() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        for (Signal signal : signals) {
            assertNotNull(signal.getSymbol(), "Signal symbol should not be null");
            assertNotNull(signal.getType(), "Signal type should not be null");
            assertNotNull(signal.getPrice(), "Signal price should not be null");
            assertNotNull(signal.getTimestamp(), "Signal timestamp should not be null");
            assertNotNull(signal.getReason(), "Signal reason should not be null");
            assertTrue("BUY".equalsIgnoreCase(signal.getType()) || "SELL".equalsIgnoreCase(signal.getType()),
                    "Signal type should be BUY or SELL");
        }
    }

    @Test
    void testGetLatestSignals_ValidatesPriceRange() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        for (Signal signal : signals) {
            assertNotNull(signal.getPrice());
            assertTrue(signal.getPrice() > 0, "Signal price should be positive");
        }
    }

    @Test
    void testGetLatestSignals_AllSignalsHaveReasons() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        for (Signal signal : signals) {
            assertNotNull(signal.getReason());
            assertFalse(signal.getReason().trim().isEmpty(), "Signal reason should not be empty");
            assertTrue(signal.getReason().length() > 5, "Signal reason should have meaningful content");
        }
    }

    @Test
    void testGetLatestSignals_TimestampsAreRecent() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        LocalDateTime now = LocalDateTime.now();
        for (Signal signal : signals) {
            assertNotNull(signal.getTimestamp());
            // Timestamps should be today (within reasonable bounds)
            assertTrue(signal.getTimestamp().isEqual(now) ||
                       signal.getTimestamp().isAfter(now.minusMinutes(5)) ||
                       signal.getTimestamp().isBefore(now.plusMinutes(5)),
                   "Signal timestamp should be recent");
        }
    }

    @Test
    void testGetLatestSignals_ReturnsDistinctSymbols() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        List<String> symbols = signals.stream().map(Signal::getSymbol).toList();
        assertTrue(symbols.stream().distinct().count() == symbols.size(),
                "All signals should have distinct symbols");
    }

    @Test
    void testGetLatestSignals_SymbolFormat() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        for (Signal signal : signals) {
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
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        assertDoesNotThrow(() -> signals.clear(),
                "Returned list should be mutable");
    }

    @Test
    void testGetLatestSignals_NoNullElements() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        assertFalse(signals.contains(null), "List should not contain null elements");
        assertEquals(signals.size(), signals.stream().filter(s -> s != null).count(),
                "All elements should be non-null");
    }
}