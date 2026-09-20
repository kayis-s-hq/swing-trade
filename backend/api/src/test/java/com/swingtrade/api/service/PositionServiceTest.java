/*
 * Copyright 2026 Swing Trade
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swingtrade.api.service;

import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.TradeRequest;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.PositionSummary;
import com.swingtrade.domain.Trade;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.Stock;
import com.swingtrade.domain.service.OrderService;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.PositionStore;
import com.swingtrade.domain.store.StockStore;
import com.swingtrade.domain.store.TradeStore;
import com.swingtrade.strategy.ExitReason;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PositionService covering the SELL/exit position-close path,
 * specifically:
 * <ul>
 *   <li>closePosition() sources the exit price from the latest OHLCV candle
 *       rather than trusting a possibly-stale {@code currentPrice} field</li>
 *   <li>closePosition()/createPosition() persist a {@link Trade} audit record
 *       for every position open/close</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PositionServiceTest {

    @Mock
    private PositionStore positionStore;

    @Mock
    private StockStore stockStore;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TradingService tradingService;

    @Mock
    private OrderService orderService;

    @Mock
    private CandleStore candleStore;

    @Mock
    private TradeStore tradeStore;

    @Mock
    private EntityManager entityManager;

    private PositionService positionService;

    @BeforeEach
    void setUp() {
        positionService = new PositionService(positionStore, stockStore, positionRepository,
                tradingService, orderService, candleStore, tradeStore, entityManager);
    }

    @Test
    void filtersPositionsByNormalizedStockSector() {
        Position technology = makeDomainPosition(1L, "TCS", new BigDecimal("100"),
                new BigDecimal("105"), "POS_00000001");
        Position energy = makeDomainPosition(2L, "RELIANCE", new BigDecimal("100"),
                new BigDecimal("105"), "POS_00000002");
        when(positionStore.findAll()).thenReturn(java.util.List.of(technology, energy));
        when(stockStore.findBySymbol("TCS")).thenReturn(Optional.of(stock("TCS", Stock.Sector.IT)));
        when(stockStore.findBySymbol("RELIANCE")).thenReturn(Optional.of(stock("RELIANCE", Stock.Sector.ENERGY)));

        List<PositionResponse> result = positionService.getPositionsBySector("Technology");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("TCS");
    }

    private Stock stock(String symbol, Stock.Sector sector) {
        return new Stock(symbol, Stock.Exchange.NSE, symbol, sector,
                null, null, null, null, null, LocalDate.now());
    }

    private Position makeDomainPosition(Long id, String symbol, BigDecimal entryPrice,
                                         BigDecimal currentPrice, String positionId) {
        return Position.of(
                id, "PAPER", symbol, entryPrice, LocalDate.now(), 10,
                new BigDecimal("430"), new BigDecimal("500"),
                PositionStatus.OPEN, "Test", currentPrice,
                positionId, null, null,
                TradeDirection.LONG, entryPrice,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDateTime.now(), null, null, null);
    }

    private PositionEntity makePositionEntity(Long id, String symbol, BigDecimal entryPrice,
                                               BigDecimal currentPrice, String positionId,
                                               String entryReason) {
        PositionEntity e = new PositionEntity();
        e.setId(id);
        e.setSymbol(symbol);
        e.setEntryPrice(entryPrice);
        e.setEntryDate(LocalDate.now());
        e.setQuantity(10);
        e.setStatus("OPEN");
        e.setEntryReason(entryReason);
        e.setCurrentPrice(currentPrice);
        e.setPositionId(positionId);
        return e;
    }

    // ==================== closePosition ====================

    @Nested
    class ClosePosition {

        @Test
        void usesLatestCandleClose_notStaleCurrentPrice() {
            // Given: a held WIPRO position whose DB currentPrice is stale (still == entryPrice),
            // but the real market has since moved: the latest OHLCV candle closed at 585.
            Position domainPos = makeDomainPosition(10L, "WIPRO", new BigDecimal("450"),
                    new BigDecimal("450"), "POS_00000010");
            when(positionStore.findBySymbol("WIPRO")).thenReturn(Optional.of(domainPos));

            PositionEntity entity = makePositionEntity(10L, "WIPRO", new BigDecimal("450"),
                    new BigDecimal("450"), "POS_00000010", "Test");
            when(positionRepository.findById(10L)).thenReturn(Optional.of(entity));
            // PaperTradingStateService.closePosition() persists the close in its own
            // REQUIRES_NEW transaction on a *different* persistence context, so this
            // method's EntityManager.refresh(entity) re-syncs the managed `entity`
            // from the DB instead of handing back its now-stale in-memory snapshot -
            // simulate that DB-side update taking effect on refresh().
            org.mockito.Mockito.doAnswer(invocation -> {
                entity.setStatus("CLOSED");
                entity.setCurrentPrice(new BigDecimal("585"));
                return null;
            }).when(entityManager).refresh(entity);

            OhlcvCandle latestCandle = OhlcvCandle.of("WIPRO", LocalDate.now(),
                    new BigDecimal("580"), new BigDecimal("590"),
                    new BigDecimal("575"), new BigDecimal("585"), 100000L);
            when(candleStore.findLatestBySymbol("WIPRO")).thenReturn(Optional.of(latestCandle));

            when(tradingService.findOpenPositionBySymbol("WIPRO")).thenReturn(domainPos);
            when(tradeStore.findOpenByPositionId(10L)).thenReturn(Optional.empty());

            // When
            PositionResponse response = positionService.closePosition("WIPRO", ExitReason.SIGNAL_EXIT.name());

            // Then: the engine close uses the fresh candle close (585), not the stale
            // currentPrice field (450), and the response reflects the re-fetched entity
            verify(tradingService).closePosition(eq(10L), eq(new BigDecimal("585")), eq(ExitReason.SIGNAL_EXIT.name()));
            verify(positionRepository, times(1)).findById(10L);
            verify(entityManager).refresh(entity);
            verify(positionRepository, never()).save(any());
            assertThat(response).isNotNull();
        }

        @Test
        void fallsBackToCurrentPrice_whenNoCandleAvailable() {
            // Given: no candle in the store — fall back to the existing behavior
            Position domainPos = makeDomainPosition(11L, "TCS", new BigDecimal("3500"),
                    new BigDecimal("3550"), "POS_00000011");
            when(positionStore.findBySymbol("TCS")).thenReturn(Optional.of(domainPos));

            PositionEntity entity = makePositionEntity(11L, "TCS", new BigDecimal("3500"),
                    new BigDecimal("3550"), "POS_00000011", "Test");
            when(positionRepository.findById(11L)).thenReturn(Optional.of(entity));
            when(candleStore.findLatestBySymbol("TCS")).thenReturn(Optional.empty());
            when(tradingService.findOpenPositionBySymbol("TCS")).thenReturn(domainPos);
            when(tradeStore.findOpenByPositionId(11L)).thenReturn(Optional.empty());

            // When
            positionService.closePosition("TCS", ExitReason.MANUAL.name());

            // Then: falls back to entity.getCurrentPrice() when calling the engine close,
            // and re-fetches rather than saving its own copy (see usesLatestCandleClose_
            // notStaleCurrentPrice for why)
            verify(tradingService).closePosition(eq(11L), eq(new BigDecimal("3550")), eq(ExitReason.MANUAL.name()));
            verify(positionRepository, times(1)).findById(11L);
            verify(entityManager).refresh(entity);
            verify(positionRepository, never()).save(any());
        }

        @Test
        void persistsClosedTradeRecord_whenOpenTradeExists() {
            // Given
            Position domainPos = makeDomainPosition(10L, "WIPRO", new BigDecimal("450"),
                    new BigDecimal("450"), "POS_00000010");
            when(positionStore.findBySymbol("WIPRO")).thenReturn(Optional.of(domainPos));

            PositionEntity entity = makePositionEntity(10L, "WIPRO", new BigDecimal("450"),
                    new BigDecimal("450"), "POS_00000010", "Test");
            when(positionRepository.findById(10L)).thenReturn(Optional.of(entity));
            when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OhlcvCandle latestCandle = OhlcvCandle.of("WIPRO", LocalDate.now(),
                    new BigDecimal("580"), new BigDecimal("590"),
                    new BigDecimal("575"), new BigDecimal("585"), 100000L);
            when(candleStore.findLatestBySymbol("WIPRO")).thenReturn(Optional.of(latestCandle));
            when(tradingService.findOpenPositionBySymbol("WIPRO")).thenReturn(domainPos);

            Trade openTrade = Trade.open(10L, "WIPRO", LocalDate.now().minusDays(3),
                    new BigDecimal("450"), 10, "Test", BigDecimal.ZERO, TradeDirection.LONG);
            when(tradeStore.findOpenByPositionId(10L)).thenReturn(Optional.of(openTrade));
            when(tradeStore.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            positionService.closePosition("WIPRO", ExitReason.SIGNAL_EXIT.name());

            // Then: a Trade audit record is closed out with the real exit price/reason
            verify(tradeStore).save(argThat(t ->
                    t.tradeStatus() != Trade.TradeStatus.OPEN
                    && t.exitPrice() != null
                    && t.exitPrice().compareTo(new BigDecimal("585")) == 0
                    && ExitReason.SIGNAL_EXIT.name().equals(t.exitReason())
                    && t.positionId().equals(10L)));
        }

        @Test
        void engineMissingFallback_stillPersistsRealizedPnLAndExitReason() {
            // Given: no engine-side position (e.g. persisted without engine linkage) -
            // tradingService.closePosition() is never called, so nothing else computes
            // realizedPnL/exitReason/exitTime. The DB-only fallback must compute them
            // itself, or this close silently persists with realizedPnL=0.00 and no
            // exit reason (the exact bad state seen in production).
            Position domainPos = makeDomainPosition(12L, "INFY", new BigDecimal("1500"),
                    new BigDecimal("1500"), "POS_00000012");
            when(positionStore.findBySymbol("INFY")).thenReturn(Optional.of(domainPos));

            PositionEntity entity = makePositionEntity(12L, "INFY", new BigDecimal("1500"),
                    new BigDecimal("1500"), "POS_00000012", "Test");
            entity.setDirection("LONG");
            when(positionRepository.findById(12L)).thenReturn(Optional.of(entity));
            when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OhlcvCandle latestCandle = OhlcvCandle.of("INFY", LocalDate.now(),
                    new BigDecimal("1520"), new BigDecimal("1540"),
                    new BigDecimal("1510"), new BigDecimal("1530"), 50000L);
            when(candleStore.findLatestBySymbol("INFY")).thenReturn(Optional.of(latestCandle));

            when(tradingService.findOpenPositionBySymbol("INFY")).thenReturn(null); // no engine position
            when(tradeStore.findOpenByPositionId(12L)).thenReturn(Optional.empty());

            // When
            positionService.closePosition("INFY", ExitReason.MANUAL.name());

            // Then: engine.closePosition() was never called...
            verify(tradingService, never()).closePosition(any(), any(), any());
            // ...but the DB entity still carries a real P&L and exit reason:
            // (1530 - 1500) * 10 = 300
            verify(positionRepository).save(argThat(e ->
                    e.getRealizedPnL() != null
                    && e.getRealizedPnL().compareTo(new BigDecimal("300")) == 0
                    && ExitReason.MANUAL.name().equals(e.getExitReason())
                    && e.getExitTime() != null));
        }

        @Test
        void positionNotFound_returnsNullWithoutTouchingTradeStore() {
            // Given
            when(positionStore.findBySymbol("NOSUCH")).thenReturn(Optional.empty());

            // When
            PositionResponse response = positionService.closePosition("NOSUCH", ExitReason.MANUAL.name());

            // Then
            assertThat(response).isNull();
            verify(tradeStore, never()).save(any());
        }
    }

    // ==================== createPosition ====================

    @Nested
    class CreatePosition {

        @Test
        void persistsOpenTradeRecord() {
            // Given
            TradeRequest request = new TradeRequest("WIPRO", 10, TradeDirection.LONG, OrderType.MARKET);
            request.setPrice(new BigDecimal("450"));
            request.setEntryReason("Test entry");

            when(stockStore.existsBySymbol("WIPRO")).thenReturn(true);

            Position enginePos = makeDomainPosition(99L, "WIPRO", new BigDecimal("450"),
                    new BigDecimal("450"), "POS_00000010");
            // First call is the duplicate-position guard (no open position yet);
            // subsequent calls occur after the order fills.
            when(tradingService.findOpenPositionBySymbol("WIPRO"))
                    .thenReturn(null, enginePos, enginePos);
            when(tradingService.calculateEntryCommission(10)).thenReturn(new BigDecimal("0.50"));

            Order pendingOrder = new Order();
            pendingOrder.setOrderId("ORD1");
            pendingOrder.setSymbol("WIPRO");
            pendingOrder.setStatus(OrderStatus.PENDING);
            when(orderService.createBuyOrder(eq("WIPRO"), eq(10), any(BigDecimal.class))).thenReturn(pendingOrder);

            Order filledOrder = new Order();
            filledOrder.setOrderId("ORD1");
            filledOrder.setSymbol("WIPRO");
            filledOrder.setStatus(OrderStatus.FILLED);
            when(tradingService.executePendingOrder(eq("ORD1"), any(BigDecimal.class))).thenReturn(filledOrder);

            PositionEntity entity = makePositionEntity(99L, "WIPRO", new BigDecimal("450"),
                    new BigDecimal("450"), "POS_00000010", "Test entry");
            when(positionRepository.findByPositionId("POS_00000010")).thenReturn(Optional.of(entity));
            when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(tradeStore.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            positionService.createPosition(request);

            // Then: an open Trade audit record is created alongside the position
            verify(tradeStore).save(argThat(t ->
                    t.tradeStatus() == Trade.TradeStatus.OPEN
                    && t.positionId().equals(99L)
                    && "WIPRO".equals(t.symbol())
                    && t.entryPrice().compareTo(new BigDecimal("450")) == 0
                    && t.quantity() == 10
                    && t.direction() == TradeDirection.LONG
                    // Regression: entry commission used to be hardcoded to ZERO instead
                    // of coming from the engine's own rate.
                    && t.fees().compareTo(new BigDecimal("0.50")) == 0));
        }
    }

    // ==================== getPositionStats ====================

    @Nested
    class GetPositionStats {

        private Position closedPosition(PositionStatus status, BigDecimal realizedPnL) {
            return Position.of(
                    1L, "PAPER", "TEST", new BigDecimal("100"), LocalDate.now(), 10,
                    new BigDecimal("90"), new BigDecimal("120"),
                    status, "Test", new BigDecimal("100"),
                    "POS_X", null, null,
                    TradeDirection.LONG, new BigDecimal("100"),
                    BigDecimal.ZERO, realizedPnL, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), "reason", null);
        }

        @Test
        void countsStoppedOutAndTargetHit_bySameStatusItAlreadyQueries() {
            // Regression: getPositionStats() used to hardcode setStoppedOut(0) /
            // setTargetHit(0) despite already fetching positions by exactly these
            // statuses a few lines above - the counts must reflect what was queried.
            when(positionStore.findOpenSummaries()).thenReturn(java.util.List.of());
            when(positionStore.findByStatus(PositionStatus.CLOSED)).thenReturn(java.util.List.of(
                    closedPosition(PositionStatus.CLOSED, new BigDecimal("50"))));
            when(positionStore.findByStatus(PositionStatus.STOPPED)).thenReturn(java.util.List.of(
                    closedPosition(PositionStatus.STOPPED, new BigDecimal("-30")),
                    closedPosition(PositionStatus.STOPPED, new BigDecimal("-10"))));
            when(positionStore.findByStatus(PositionStatus.TARGET_HIT)).thenReturn(java.util.List.of(
                    closedPosition(PositionStatus.TARGET_HIT, new BigDecimal("80"))));
            when(tradingService.getTotalUnrealizedPnL()).thenReturn(BigDecimal.ZERO);
            when(tradingService.getTotalRealizedPnL()).thenReturn(BigDecimal.ZERO);

            var stats = positionService.getPositionStats();

            assertThat(stats.getStoppedOut()).isEqualTo(2);
            assertThat(stats.getTargetHit()).isEqualTo(1);
            assertThat(stats.getClosedPositions()).isEqualTo(4);
        }
    }

    // ==================== risk summary / sector allocation (summary projection) ====================

    @Nested
    class SummaryBackedAggregations {

        private PositionSummary summary(String symbol, String entry, String current, String stop, int qty) {
            return new PositionSummary(1L, symbol, PositionStatus.OPEN, TradeDirection.LONG,
                    new BigDecimal(entry), qty, current == null ? null : new BigDecimal(current),
                    BigDecimal.ZERO, new BigDecimal(stop), new BigDecimal("500"), "PAPER",
                    LocalDate.of(2026, 1, 15), "Test");
        }

        @Test
        void riskSummaryAggregatesFromSummariesWithoutLoadingFullPositions() {
            when(positionStore.findOpenSummaries()).thenReturn(List.of(
                    summary("TCS", "100", "110", "90", 10),
                    summary("INFY", "200", "190", "180", 5)));
            when(stockStore.findBySymbol("TCS")).thenReturn(Optional.of(stock("TCS", Stock.Sector.IT)));
            when(stockStore.findBySymbol("INFY")).thenReturn(Optional.of(stock("INFY", Stock.Sector.IT)));
            when(tradingService.getCurrentCash()).thenReturn(new BigDecimal("1000"));

            var risk = positionService.getRiskSummary();

            assertThat(risk.getTotalExposure()).isEqualByComparingTo("2050");
            assertThat(risk.getNumberOfPositions()).isEqualTo(2);
            assertThat(risk.getStopLossExposure()).isEqualByComparingTo("200");
            assertThat(risk.getSectorExposure()).containsEntry("IT", 2);
            verify(positionStore, never()).findAllOpen();
        }

        @Test
        void sectorAllocationSkipsSummariesWithoutPrice() {
            when(positionStore.findOpenSummaries()).thenReturn(List.of(
                    summary("TCS", "100", "100", "90", 10),
                    summary("NOPRICE", "100", null, "90", 10)));
            when(stockStore.findBySymbol("TCS")).thenReturn(Optional.of(stock("TCS", Stock.Sector.IT)));

            var allocation = positionService.getSectorAllocation();

            assertThat(allocation.getTotalExposure()).isEqualByComparingTo("1000");
            assertThat(allocation.getNumberOfSectors()).isEqualTo(1);
            assertThat(allocation.getAllocation()).containsEntry("IT", 100.0);
            verify(positionStore, never()).findAllOpen();
        }
    }
}
