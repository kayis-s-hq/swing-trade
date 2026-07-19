package com.swingtrade.api;

import com.swingtrade.api.dto.SignalQueryResult;
import com.swingtrade.api.dto.SignalQueryResult.Signal;
import com.swingtrade.api.service.SignalService;
import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.api.service.SignalEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
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

    @BeforeEach
    void setUp() {
        // Set up test data using SignalEntity
        List<SignalEntity> testEntities = new ArrayList<>();
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
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        assertNotNull(signals, "Signals list should not be null");
        assertFalse(signals.isEmpty(), "Signals list should not be empty");
        assertEquals(5, signals.size(), "Should return all 5 signals from test data");
    }

    @Test
    void testGetLatestSignals_ReturnsCorrectSignalTypes() {
        // Arrange - the service returns BUY and SELL signals
        List<Signal> signals = signalService.getLatestSignals();

        // Act & Assert
        assertTrue(signals.stream().anyMatch(s -> "BUY".equals(s.type().name())), "Should contain at least one BUY signal");
        assertTrue(signals.stream().anyMatch(s -> "SELL".equals(s.type().name())), "Should contain at least one SELL signal");
    }

    @Test
    void testGetLatestSignals_ValidatesSignalProperties() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        for (Signal signal : signals) {
            assertNotNull(signal.symbol(), "Signal symbol should not be null");
            assertNotNull(signal.type(), "Signal type should not be null");
            assertNotNull(signal.date(), "Signal date should not be null");
            assertNotNull(signal.reasoning(), "Signal reason should not be null");
            assertTrue("BUY".equalsIgnoreCase(signal.type().name()) || "SELL".equalsIgnoreCase(signal.type().name()),
                    "Signal type should be BUY or SELL");
        }
    }

    @Test
    void testGetLatestSignals_AllSignalsHaveReasons() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        for (Signal signal : signals) {
            assertNotNull(signal.reasoning());
            assertFalse(signal.reasoning().trim().isEmpty(), "Signal reason should not be empty");
            assertTrue(signal.reasoning().length() > 5, "Signal reason should have meaningful content");
        }
    }

    @Test
    void testGetLatestSignals_TimestampsAreRecent() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        LocalDate today = LocalDate.now();
        for (Signal signal : signals) {
            assertNotNull(signal.date());
            // Timestamps should be today
            assertEquals(today, signal.date(), "Signal date should be today");
        }
    }

    @Test
    void testGetLatestSignals_ReturnsDistinctSymbols() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        List<String> symbols = signals.stream().map(Signal::symbol).toList();
        assertTrue(symbols.stream().distinct().count() == symbols.size(),
                "All signals should have distinct symbols");
    }

    @Test
    void testGetLatestSignals_SymbolFormat() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        for (Signal signal : signals) {
            assertNotNull(signal.symbol());
            assertTrue(signal.symbol().matches("[A-Z]+"),
                    "Symbol should consist of uppercase letters");
            assertTrue(signal.symbol().length() <= 5,
                    "Symbol should be reasonably short");
        }
    }

    @Test
    void testGetLatestSignals_ListIsMutable() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        assertDoesNotThrow(signals::clear,
                "Returned list should be mutable");
    }

    @Test
    void testGetLatestSignals_NoNullElements() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        assertFalse(signals.contains(null), "List should not contain null elements");
        assertEquals(signals.size(), signals.stream().filter(Objects::nonNull).count(),
                "All elements should be non-null");
    }
}
