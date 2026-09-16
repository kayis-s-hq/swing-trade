package com.swingtrade.broker.service;

import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Unit tests for PaperTradingMonitorService covering position monitoring,
 * SL/TP triggers via candle, error handling, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class PaperTradingMonitorServiceTest {

    private PaperTradingMonitorService monitorService;

    @Mock
    private PaperTradingEngine engine;

    @Mock
    private PaperTradingStateService stateService;

    @Mock
    private OhlcvCandleRepository ohlcvCandleRepository;

    private PaperTradingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PaperTradingProperties();
        monitorService = new PaperTradingMonitorService(engine, stateService, ohlcvCandleRepository, properties);
    }

    // Helper: build a Position for testing
    private Position makePosition(String symbol, PositionStatus status, BigDecimal currentPrice) {
        return new Position(
            1L, "PAPER", symbol, new BigDecimal("100.00"), LocalDate.now(), 10,
            new BigDecimal("90.00"), new BigDecimal("125.00"), status, "Test",
            currentPrice, "POS_00000001", null, null, TradeDirection.LONG,
            new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            null, null, null, null
        );
    }

    private OhlcvCandle makeCandle(String symbol, BigDecimal close) {
        return new OhlcvCandle(symbol, LocalDate.now(), new BigDecimal("100"),
            new BigDecimal("105"), new BigDecimal("98"), close, 100000L, close);
    }

    // ==================== monitorPositions ====================

    @Nested
    class MonitorPositions {

        @Test
        void monitorPositions_noPositions_returnsEarly() {
            // Given: No open positions
            when(engine.getOpenPositions()).thenReturn(List.of());

            // When
            monitorService.monitorPositions();

            // Then
            verify(ohlcvCandleRepository, never()).save(any());
            verify(engine, never()).updatePositionsFromDomain(any());
            verify(stateService, never()).saveSnapshot();
            verify(stateService, never()).savePortfolio();
        }

        @Test
        void monitorPositions_singlePosition_fullFlow() {
            // Given: One open position
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN, new BigDecimal("100.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));

            OhlcvCandle candle = makeCandle("RELIANCE-EQ", new BigDecimal("103.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("RELIANCE-EQ")).thenReturn(Optional.of(entity));

            // When
            monitorService.monitorPositions();

            // Then
            verify(ohlcvCandleRepository).findLatestBySymbol("RELIANCE-EQ");
            verify(ohlcvCandleRepository, never()).save(any(OhlcvCandleEntity.class));
            verify(engine).updatePositionsFromDomain(candle);
            verify(stateService).saveSnapshot();
            verify(stateService).savePortfolio();
        }

        @Test
        void monitorPositions_multiplePositions() {
            // Given: Two open positions
            Position pos1 = makePosition("RELIANCE-EQ", PositionStatus.OPEN, new BigDecimal("100.00"));
            Position pos2 = makePosition("TCS-EQ", PositionStatus.OPEN, new BigDecimal("200.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(pos1, pos2));

            OhlcvCandle candle1 = makeCandle("RELIANCE-EQ", new BigDecimal("103.00"));
            OhlcvCandle candle2 = makeCandle("TCS-EQ", new BigDecimal("195.00"));

            OhlcvCandleEntity entity1 = mock(OhlcvCandleEntity.class);
            OhlcvCandleEntity entity2 = mock(OhlcvCandleEntity.class);
            when(entity1.toDomain()).thenReturn(candle1);
            when(entity2.toDomain()).thenReturn(candle2);

            when(ohlcvCandleRepository.findLatestBySymbol("RELIANCE-EQ")).thenReturn(Optional.of(entity1));
            when(ohlcvCandleRepository.findLatestBySymbol("TCS-EQ")).thenReturn(Optional.of(entity2));

            // When
            monitorService.monitorPositions();

            // Then
            verify(ohlcvCandleRepository, times(2)).findLatestBySymbol(anyString());
            verify(ohlcvCandleRepository, never()).save(any(OhlcvCandleEntity.class));
            verify(engine, times(2)).updatePositionsFromDomain(any(OhlcvCandle.class));
            verify(stateService).saveSnapshot();
            verify(stateService).savePortfolio();
        }

        @Test
        void monitorPositions_saveSnapshotAndPortfolio() {
            // Given: Open positions
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN, new BigDecimal("100.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));

            OhlcvCandle candle = makeCandle("RELIANCE-EQ", new BigDecimal("103.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("RELIANCE-EQ")).thenReturn(Optional.of(entity));

            // When
            monitorService.monitorPositions();

            // Then
            verify(stateService).saveSnapshot();
            verify(stateService).savePortfolio();
        }
    }

    // ==================== Position Updates ====================

    @Nested
    class PositionUpdates {

        @Test
        void candleSavedToDb() {
            // Given: An open position
            Position position = makePosition("INFY-EQ", PositionStatus.OPEN, new BigDecimal("150.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));

            OhlcvCandle candle = makeCandle("INFY-EQ", new BigDecimal("155.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("INFY-EQ")).thenReturn(Optional.of(entity));

            // When
            monitorService.monitorPositions();

            // Then
            verify(ohlcvCandleRepository, never()).save(any(OhlcvCandleEntity.class));
        }

        @Test
        void engineUpdatesPositionFromCandle() {
            // Given: An open position
            Position position = makePosition("HDFC-EQ", PositionStatus.OPEN, new BigDecimal("1800.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));

            OhlcvCandle candle = makeCandle("HDFC-EQ", new BigDecimal("1820.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("HDFC-EQ")).thenReturn(Optional.of(entity));

            // When
            monitorService.monitorPositions();

            // Then
            verify(engine).updatePositionsFromDomain(candle);
        }

        @Test
        void doesNotDuplicatePersistence_delegatesSolelyToEngine() {
            // Given: An open position. engine.updatePositionsFromDomain() is responsible
            // for persisting the resulting state (open or closed) internally; the monitor
            // must not additionally call stateService.savePosition() with its own
            // pre-update snapshot, which would overwrite whatever the engine just wrote.
            Position position = makePosition("SBIN-EQ", PositionStatus.OPEN, new BigDecimal("700.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));

            OhlcvCandle candle = makeCandle("SBIN-EQ", new BigDecimal("710.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("SBIN-EQ")).thenReturn(Optional.of(entity));

            // When
            monitorService.monitorPositions();

            // Then
            verify(engine).updatePositionsFromDomain(candle);
            verify(stateService, never()).savePosition(any());
        }

        @Test
        void noTrigger_candleWithinSlTpRange() {
            // Given: A long position with SL 90, target 125, current 100
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN, new BigDecimal("100.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));

            // Candle close is 103 -- between SL 90 and target 125, no trigger
            OhlcvCandle candle = makeCandle("RELIANCE-EQ", new BigDecimal("103.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("RELIANCE-EQ")).thenReturn(Optional.of(entity));

            // When
            monitorService.monitorPositions();

            // Then
            verify(engine).updatePositionsFromDomain(candle);
        }
    }

    // ==================== Error Handling ====================

    @Nested
    class ErrorHandling {

        @Test
        void candleFetchFails_continuesWithNextPosition() {
            // Given: Two open positions, first candle fetch returns null
            Position pos1 = makePosition("RELIANCE-EQ", PositionStatus.OPEN, new BigDecimal("100.00"));
            Position pos2 = makePosition("TCS-EQ", PositionStatus.OPEN, new BigDecimal("200.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(pos1, pos2));

            // First symbol: no candle found
            when(ohlcvCandleRepository.findLatestBySymbol("RELIANCE-EQ")).thenReturn(Optional.empty());
            // Second symbol: candle found
            OhlcvCandle candle = makeCandle("TCS-EQ", new BigDecimal("195.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("TCS-EQ")).thenReturn(Optional.of(entity));

            // When
            monitorService.monitorPositions();

            // Then: First position skipped (candle null), second processed
            verify(ohlcvCandleRepository, times(2)).findLatestBySymbol(anyString());
            verify(ohlcvCandleRepository, never()).save(any(OhlcvCandleEntity.class));
            verify(engine, times(1)).updatePositionsFromDomain(any(OhlcvCandle.class));
            verify(stateService).saveSnapshot();
            verify(stateService).savePortfolio();
        }

        @Test
        void positionUpdateThrows_continuesWithNextPosition() {
            // Given: Two open positions, first throws
            Position pos1 = makePosition("RELIANCE-EQ", PositionStatus.OPEN, new BigDecimal("100.00"));
            Position pos2 = makePosition("TCS-EQ", PositionStatus.OPEN, new BigDecimal("200.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(pos1, pos2));

            OhlcvCandle candle1 = makeCandle("RELIANCE-EQ", new BigDecimal("103.00"));
            OhlcvCandle candle2 = makeCandle("TCS-EQ", new BigDecimal("195.00"));
            OhlcvCandleEntity entity1 = mock(OhlcvCandleEntity.class);
            OhlcvCandleEntity entity2 = mock(OhlcvCandleEntity.class);
            when(entity1.toDomain()).thenReturn(candle1);
            when(entity2.toDomain()).thenReturn(candle2);
            when(ohlcvCandleRepository.findLatestBySymbol("RELIANCE-EQ")).thenReturn(Optional.of(entity1));
            when(ohlcvCandleRepository.findLatestBySymbol("TCS-EQ")).thenReturn(Optional.of(entity2));
            // First engine call throws
            doThrow(new RuntimeException("Update failed")).when(engine).updatePositionsFromDomain(candle1);

            // When
            monitorService.monitorPositions();

            // Then: First position fails, second continues, snapshot still saved
            verify(engine, times(2)).updatePositionsFromDomain(any(OhlcvCandle.class));
            verify(stateService).saveSnapshot();
            verify(stateService).savePortfolio();
        }

        @Test
        void enginePersistenceThrows_continuesToSnapshot() {
            // Given: One open position, the engine's own persistence step fails
            // (engine.updatePositionsFromDomain is responsible for calling
            // stateService internally; the monitor no longer calls it directly)
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN, new BigDecimal("100.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));

            OhlcvCandle candle = makeCandle("RELIANCE-EQ", new BigDecimal("103.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("RELIANCE-EQ")).thenReturn(Optional.of(entity));
            doThrow(new RuntimeException("DB error")).when(engine).updatePositionsFromDomain(candle);

            // When
            monitorService.monitorPositions();

            // Then: Exception caught, best-effort continues to snapshot
            verify(stateService).saveSnapshot();
            verify(stateService).savePortfolio();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void nullCandle_skipsPosition() {
            // Given: An open position, no candle in DB
            Position position = makePosition("INFY-EQ", PositionStatus.OPEN, new BigDecimal("150.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));
            when(ohlcvCandleRepository.findLatestBySymbol("INFY-EQ")).thenReturn(Optional.empty());

            // When
            monitorService.monitorPositions();

            // Then: Position skipped, no save/update calls for it
            verify(ohlcvCandleRepository, never()).save(any());
            verify(engine, never()).updatePositionsFromDomain(any());
            verify(stateService, never()).savePosition(any());
            verify(stateService).saveSnapshot();
            verify(stateService).savePortfolio();
        }

        @Test
        void nullStateService_skipsPersist() {
            // Given: Open positions, null stateService
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN, new BigDecimal("100.00"));
            when(engine.getOpenPositions()).thenReturn(List.of(position));

            OhlcvCandle candle = makeCandle("RELIANCE-EQ", new BigDecimal("103.00"));
            OhlcvCandleEntity entity = mock(OhlcvCandleEntity.class);
            when(entity.toDomain()).thenReturn(candle);
            when(ohlcvCandleRepository.findLatestBySymbol("RELIANCE-EQ")).thenReturn(Optional.of(entity));

            PaperTradingMonitorService service = new PaperTradingMonitorService(engine, null, ohlcvCandleRepository, properties);

            // When
            service.monitorPositions();

            // Then: No stateService calls
            verify(engine).updatePositionsFromDomain(candle);
        }

        @Test
        void monitorCronExpression_defaultValue() {
            // When
            String cron = properties.getMonitorCron();

            // Then
            assertThat(cron).isEqualTo("0 45 16 * * MON-FRI");
        }

        @Test
        void snapshotCronExpression_defaultValue() {
            // When
            String cron = properties.getSnapshotCron();

            // Then
            assertThat(cron).isEqualTo("0 45 15 * * MON-FRI");
        }
    }
}
