package com.swingtrade.api;

import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.strategy.SignalEngine;
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

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for SignalService
 * Tests signal management functionality including retrieval and filtering
 */
@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @Mock
    private SignalRepository signalRepository;

    @Mock
    private SignalEngine signalEngine;

    @InjectMocks
    private SignalService signalService;

    private List<SignalEntity> testEntities;

    @BeforeEach
    void setUp() {
        // Set up test data using SignalEntity
        testEntities = new ArrayList<>();
        testEntities.add(createSignalEntity("AAPL", "BUY", 0.85, LocalDate.now(), "Breakout above resistance"));
        testEntities.add(createSignalEntity("TSLA", "SELL", 0.72, LocalDate.now(), "Support level broken"));
        testEntities.add(createSignalEntity("MSFT", "BUY", 0.78, LocalDate.now(), "Moving average crossover"));
        testEntities.add(createSignalEntity("GOOGL", "BUY", 0.65, LocalDate.now(), "RSI oversold"));
        testEntities.add(createSignalEntity("AMZN", "SELL", 0.68, LocalDate.now(), "Resistance rejection"));

        // Mock repository to return test data as Page for findAll with pageable
        Page<SignalEntity> testPage = new PageImpl<>(testEntities);
        when(signalRepository.findAll(any(Pageable.class))).thenReturn(testPage);
    }

    private SignalEntity createSignalEntity(String symbol, String type, double confidence, LocalDate date, String reasoning) {
        var entity = new SignalEntity();
        entity.setSymbol(symbol);
        entity.setSignalType(type);
        entity.setConfidenceScore(BigDecimal.valueOf(confidence));
        entity.setDate(date);
        entity.setReasoning(reasoning);
        entity.setEntryPrice(BigDecimal.valueOf(100));
        entity.setStopLoss(BigDecimal.valueOf(95));
        entity.setTarget(BigDecimal.valueOf(110));
        entity.setRiskReward(BigDecimal.valueOf(2.0));
        entity.setIndicators("RSI=35");
        return entity;
    }

    @Test
    void testGetLatestSignals_ReturnsNonEmptyList() {
        // Act
        List<SignalService.Signal> signals = signalService.getLatestSignals();

        // Assert
        assertNotNull(signals, "Signals list should not be null");
        assertFalse(signals.isEmpty(), "Signals list should not be empty");
        assertEquals(5, signals.size(), "Should return all 5 signals from test data");
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
