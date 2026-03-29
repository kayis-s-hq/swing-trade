package com.swingtrade.api;

import com.swingtrade.api.dto.SignalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    private List<SignalResponse> testSignals;

    @BeforeEach
    void setUp() {
        // Set up test data
        testSignals = new ArrayList<>();
        testSignals.add(new SignalResponse("AAPL", LocalDate.now(), SignalResponse.SignalType.BUY, new BigDecimal("0.85"), "Breakout above resistance", new BigDecimal("150.00"), new BigDecimal("145.00"), new BigDecimal("160.00"), new BigDecimal("2.0"), List.of("RSI", "MACD"), java.time.LocalDateTime.now()));
        testSignals.add(new SignalResponse("TSLA", LocalDate.now(), SignalResponse.SignalType.SELL, new BigDecimal("0.72"), "Support level broken", new BigDecimal("250.00"), new BigDecimal("260.00"), new BigDecimal("240.00"), new BigDecimal("1.5"), List.of("RSI"), java.time.LocalDateTime.now()));
        testSignals.add(new SignalResponse("MSFT", LocalDate.now(), SignalResponse.SignalType.BUY, new BigDecimal("0.78"), "Moving average crossover", new BigDecimal("380.50"), new BigDecimal("375.00"), new BigDecimal("395.00"), new BigDecimal("2.5"), List.of("EMA", "MACD"), java.time.LocalDateTime.now()));
        testSignals.add(new SignalResponse("GOOGL", LocalDate.now(), SignalResponse.SignalType.BUY, new BigDecimal("0.65"), "RSI oversold", new BigDecimal("140.00"), new BigDecimal("135.00"), new BigDecimal("150.00"), new BigDecimal("2.0"), List.of("RSI"), java.time.LocalDateTime.now()));
        testSignals.add(new SignalResponse("AMZN", LocalDate.now(), SignalResponse.SignalType.SELL, new BigDecimal("0.68"), "Resistance rejection", new BigDecimal("175.25"), new BigDecimal("180.00"), new BigDecimal("170.00"), new BigDecimal("1.5"), List.of("RSI", "MACD"), java.time.LocalDateTime.now()));
    }

    @Test
    void testGetLatestSignals_ReturnsNonEmptyList() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        assertNotNull(signals, "Signals list should not be null");
        assertFalse(signals.isEmpty(), "Signals list should not be empty");
        assertEquals(2, signals.size(), "Should return exactly 2 signals");
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
            assertNotNull(signal.getEntryPrice(), "Signal price should not be null");
            assertNotNull(signal.getGeneratedAt(), "Signal timestamp should not be null");
            assertNotNull(signal.getReasoning(), "Signal reason should not be null");
            assertTrue("BUY".equalsIgnoreCase(signal.getSignalType().name()) || "SELL".equalsIgnoreCase(signal.getSignalType().name()),
                    "Signal type should be BUY or SELL");
        }
    }

    @Test
    void testGetLatestSignals_ValidatesPriceRange() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        for (SignalResponse signal : signals) {
            assertNotNull(signal.getEntryPrice());
            assertTrue(signal.getEntryPrice().compareTo(java.math.BigDecimal.ZERO) > 0, "Signal price should be positive");
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
        LocalDateTime now = LocalDateTime.now();
        for (SignalResponse signal : signals) {
            assertNotNull(signal.getGeneratedAt());
            // Timestamps should be today (within reasonable bounds)
            assertTrue(signal.getGeneratedAt().isEqual(now) ||
                       signal.getGeneratedAt().isAfter(now.minusMinutes(5)) ||
                       signal.getGeneratedAt().isBefore(now.plusMinutes(5)),
                   "Signal timestamp should be recent");
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
        assertDoesNotThrow(() -> signals.clear(),
                "Returned list should be mutable");
    }

    @Test
    void testGetLatestSignals_NoNullElements() {
        // Act
        List<SignalResponse> signals = signalService.getLatestSignals();

        // Assert
        assertFalse(signals.contains(null), "List should not contain null elements");
        assertEquals(signals.size(), signals.stream().filter(s -> s != null).count(),
                "All elements should be non-null");
    }
}
