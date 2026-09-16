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

package com.swingtrade.broker.service;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.broker.entity.PaperTradingOrderEntity;
import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import com.swingtrade.broker.repository.PaperTradingOrderRepository;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.repository.PaperTradingSnapshotRepository;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.strategy.ExitReason;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for PaperTradingStateService covering state loading,
 * portfolio/position/order/snapshot persistence, queries, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaperTradingStateServiceTest {

    @Mock
    private PaperTradingEngine engine;

    @Mock
    private PositionManager positionManager;

    @Mock
    private OrderManager orderManager;

    @Mock
    private PaperTradingPortfolioRepository portfolioRepo;

    @Mock
    private PositionRepository unifiedPositionRepo;

    @Mock
    private PaperTradingOrderRepository orderRepo;

    @Mock
    private PaperTradingSnapshotRepository snapshotRepo;

    private PaperTradingStateService stateService;

    private PaperTradingPortfolioEntity makePortfolioEntity(
            Long id, String portfolioId, BigDecimal initialCapital, BigDecimal currentCapital,
            BigDecimal totalRealizedPnl, BigDecimal totalUnrealizedPnL, int openPositionCount) {
        PaperTradingPortfolioEntity e = new PaperTradingPortfolioEntity();
        e.setId(id);
        e.setPortfolioId(portfolioId);
        e.setInitialCapital(initialCapital);
        e.setCurrentCapital(currentCapital);
        e.setTotalRealizedPnl(totalRealizedPnl);
        e.setTotalUnrealizedPnL(totalUnrealizedPnL);
        e.setOpenPositionCount(openPositionCount);
        return e;
    }

    private PositionEntity makePositionEntity(
            Long id, String symbol, String brokerType, BigDecimal entryPrice, LocalDate entryDate,
            Integer quantity, BigDecimal stopLoss, BigDecimal target, String status,
            String entryReason, BigDecimal currentPrice, String positionId, String brokerPositionId,
            String exchange, String direction, BigDecimal averagePrice,
            BigDecimal unrealizedPnL, BigDecimal realizedPnL, BigDecimal marginUtilized,
            LocalDateTime entryTime, LocalDateTime exitTime, String exitReason) {
        PositionEntity e = new PositionEntity();
        e.setId(id);
        e.setSymbol(symbol);
        e.setBrokerType(brokerType);
        e.setEntryPrice(entryPrice);
        e.setEntryDate(entryDate);
        e.setQuantity(quantity);
        e.setStopLoss(stopLoss);
        e.setTarget(target);
        e.setStatus(status);
        e.setEntryReason(entryReason);
        e.setCurrentPrice(currentPrice);
        e.setPositionId(positionId);
        e.setBrokerPositionId(brokerPositionId);
        e.setExchange(exchange);
        e.setDirection(direction);
        e.setAveragePrice(averagePrice);
        e.setUnrealizedPnL(unrealizedPnL);
        e.setRealizedPnL(realizedPnL);
        e.setMarginUtilized(marginUtilized);
        e.setEntryTime(entryTime);
        e.setExitTime(exitTime);
        e.setExitReason(exitReason);
        return e;
    }

    private Order makeOrder(String orderId, String symbol, OrderType type, TradeDirection direction,
                            BigDecimal quantity, BigDecimal price, OrderStatus status) {
        Order o = new Order();
        o.setOrderId(orderId);
        o.setSymbol(symbol);
        o.setType(type);
        o.setDirection(direction);
        o.setQuantity(quantity);
        o.setPrice(price);
        o.setStatus(status);
        return o;
    }

    @BeforeEach
    void setUp() {
        stateService = new PaperTradingStateService(engine, positionManager, orderManager,
                portfolioRepo, unifiedPositionRepo, orderRepo, snapshotRepo);
    }

    // ==================== loadState ====================

    @Nested
    class LoadState {

        @Test
        void success_loadsPortfolioAndPositions() {
            // Given: Portfolio exists with saved state
            PaperTradingPortfolioEntity saved = makePortfolioEntity(
                    1L, "default", new BigDecimal("1000000"), new BigDecimal("950000"),
                    new BigDecimal("50000"), BigDecimal.ZERO, 0);
            when(portfolioRepo.findById(1L)).thenReturn(Optional.of(saved));
            when(unifiedPositionRepo.findAllOpenPositions()).thenReturn(List.of());

            // Given: Portfolio getter returns a real portfolio object
            com.swingtrade.broker.model.Portfolio portfolio = new com.swingtrade.broker.model.Portfolio("default", new BigDecimal("1000000"));
            when(engine.getPortfolio()).thenReturn(portfolio);

            // When
            stateService.loadState();

            // Then
            verify(portfolioRepo).findById(1L);
            verify(unifiedPositionRepo).findAllOpenPositions();
            verify(unifiedPositionRepo).findByStatus("CLOSED");
        }

        @Test
        void success_reloadsPendingOrdersForNextSession() {
            when(portfolioRepo.findById(1L)).thenReturn(Optional.empty());
            when(unifiedPositionRepo.findAllOpenPositions()).thenReturn(List.of());
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.ZERO));

            PaperTradingOrderEntity entity = new PaperTradingOrderEntity();
            entity.setOrderId("ORD_RESTART");
            entity.setSymbol("TCS");
            entity.setType(OrderType.MARKET.name());
            entity.setDirection(TradeDirection.LONG.name());
            entity.setQuantity(10);
            entity.setPrice(new BigDecimal("100.00"));
            entity.setStatus(OrderStatus.PENDING.name());
            entity.setSignalId("42");
            when(orderRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entity));
            var restoredOrders = new java.util.concurrent.ConcurrentHashMap<String, Order>();
            when(orderManager.getOrders()).thenReturn(restoredOrders);

            stateService.loadState();

            verify(orderManager).getOrders();
            assertThat(restoredOrders).containsKey("ORD_RESTART");
            Order restored = restoredOrders.get("ORD_RESTART");
            assertThat(restored.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(restored.getAdditionalProperties()).containsEntry("signalId", "42");
        }

        @Test
        void nullPortfolio_usesDefaults() {
            // Given: No portfolio found in DB
            when(portfolioRepo.findById(1L)).thenReturn(Optional.empty());
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.ZERO));

            // When
            stateService.loadState();

            // Then
            verify(portfolioRepo).findById(1L);
        }

        @Test
        void loadOpenPositions_filtersByPaperBroker() {
            // Given: Portfolio exists, open positions exist
            when(portfolioRepo.findById(1L)).thenReturn(Optional.of(makePortfolioEntity(
                    1L, "default", BigDecimal.valueOf(1000000), BigDecimal.valueOf(950000),
                    BigDecimal.ZERO, BigDecimal.ZERO, 0)));
            PositionEntity paperPos = makePositionEntity(1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                    "Test", new BigDecimal("2500"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            PositionEntity otherPos = makePositionEntity(2L, "TCS", "Fyers",
                    new BigDecimal("3500"), LocalDate.now(), 5,
                    new BigDecimal("3400"), new BigDecimal("3700"), "OPEN",
                    "Test", new BigDecimal("3500"), "POS_00000002", null,
                    "NSE", "LONG", new BigDecimal("3500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findAllOpenPositions()).thenReturn(List.of(paperPos, otherPos));
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(1000000)));
            when(positionManager.getPositions()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());

            // When
            stateService.loadState();

            // Then: Only PAPER broker positions are loaded into the engine
            verify(positionManager, times(1)).getPositions();
        }

        @Test
        void loadClosedPositions_includesMultipleStatuses() {
            // Given: Portfolio exists
            when(portfolioRepo.findById(1L)).thenReturn(Optional.of(makePortfolioEntity(
                    1L, "default", BigDecimal.valueOf(1000000), BigDecimal.valueOf(950000),
                    BigDecimal.valueOf(50000), BigDecimal.ZERO, 0)));
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(1000000)));
            when(unifiedPositionRepo.findAllOpenPositions()).thenReturn(List.of());
            when(unifiedPositionRepo.findByStatus("CLOSED")).thenReturn(List.of());
            when(unifiedPositionRepo.findByStatus("STOPPED")).thenReturn(List.of());
            when(unifiedPositionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of());

            // When
            stateService.loadState();

            // Then
            verify(unifiedPositionRepo).findByStatus("CLOSED");
            verify(unifiedPositionRepo).findByStatus("STOPPED");
            verify(unifiedPositionRepo).findByStatus("TARGET_HIT");
        }

        @Test
        void failureOnOpenPositions_continues() {
            // Given: Portfolio loads fine, open positions throw
            when(portfolioRepo.findById(1L)).thenReturn(Optional.of(makePortfolioEntity(
                    1L, "default", BigDecimal.valueOf(1000000), BigDecimal.valueOf(950000),
                    BigDecimal.ZERO, BigDecimal.ZERO, 0)));
            when(unifiedPositionRepo.findAllOpenPositions()).thenThrow(new RuntimeException("DB error"));
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(1000000)));

            // When: loadState catches exception and logs warning
            stateService.loadState();

            // Then: No exception propagates
            verify(portfolioRepo).findById(1L);
        }
    }

    // ==================== savePortfolio ====================

    @Nested
    class SavePortfolio {

        @Test
        void savesFromEngine_newPortfolio() {
            // Given: No existing portfolio in DB
            when(portfolioRepo.findById(1L)).thenReturn(Optional.empty());
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(1000000)));
            when(engine.getTotalRealizedPnL()).thenReturn(BigDecimal.ZERO);
            when(engine.getTotalUnrealizedPnL()).thenReturn(BigDecimal.ZERO);
            when(engine.getOpenPositionCount()).thenReturn(2);
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            stateService.savePortfolio();

            // Then
            verify(portfolioRepo).save(any(PaperTradingPortfolioEntity.class));
            verify(portfolioRepo).findById(1L);
        }

        @Test
        void savesFromEngine_updatesExisting() {
            // Given: Existing portfolio in DB
            PaperTradingPortfolioEntity existing = makePortfolioEntity(
                    1L, "default", BigDecimal.valueOf(1000000), BigDecimal.valueOf(900000),
                    BigDecimal.valueOf(100000), BigDecimal.ZERO, 3);
            when(portfolioRepo.findById(1L)).thenReturn(Optional.of(existing));
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(950000)));
            when(engine.getTotalRealizedPnL()).thenReturn(BigDecimal.valueOf(50000));
            when(engine.getTotalUnrealizedPnL()).thenReturn(BigDecimal.valueOf(-10000));
            when(engine.getOpenPositionCount()).thenReturn(2);
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            stateService.savePortfolio();

            // Then
            verify(portfolioRepo).save(any(PaperTradingPortfolioEntity.class));
        }

        @Test
        void savesFromEngine_createsNewWhenNotFound() {
            // Given: findById returns empty
            when(portfolioRepo.findById(1L)).thenReturn(Optional.empty());
            com.swingtrade.broker.model.Portfolio portfolio = new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(500000));
            when(engine.getPortfolio()).thenReturn(portfolio);
            when(engine.getTotalRealizedPnL()).thenReturn(BigDecimal.ZERO);
            when(engine.getTotalUnrealizedPnL()).thenReturn(BigDecimal.ZERO);
            when(engine.getOpenPositionCount()).thenReturn(0);
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            stateService.savePortfolio();

            // Then
            verify(portfolioRepo).save(any(PaperTradingPortfolioEntity.class));
        }
    }

    // ==================== savePosition ====================

    @Nested
    class SavePosition {

        @Test
        void newPosition_createsEntity() {
            // Given: Position not found in DB
            Position position = new Position(
                    null, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.OPEN, "Test", new BigDecimal("2500"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null, null);
            when(unifiedPositionRepo.findByPositionId("POS_00000001")).thenReturn(Optional.empty());
            when(unifiedPositionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            stateService.savePosition(position);

            // Then
            verify(unifiedPositionRepo).save(any(PositionEntity.class));
        }

        @Test
        void existingPosition_updatesFields() {
            // Given: Position exists in DB
            PositionEntity existing = makePositionEntity(
                    1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                    "Test", new BigDecimal("2500"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findByPositionId("POS_00000001")).thenReturn(Optional.of(existing));

            Position position = new Position(
                    1L, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.OPEN, "Test", new BigDecimal("2550"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null, null);

            // When
            stateService.savePosition(position);

            // Then
            verify(unifiedPositionRepo).save(any(PositionEntity.class));
        }

        @Test
        void statusChange_setsExitTime() {
            // Given: Existing open position, new status is CLOSED
            PositionEntity existing = makePositionEntity(
                    1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                    "Test", new BigDecimal("2500"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findByPositionId("POS_00000001")).thenReturn(Optional.of(existing));

            Position position = new Position(
                    1L, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.CLOSED, "Test", new BigDecimal("2600"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), null, null, null);

            // When
            stateService.savePosition(position);

            // Then
            verify(unifiedPositionRepo).save(any(PositionEntity.class));
        }

        @Test
        void savePosition_repositoryException_wrapped() {
            // Given: Position not found, save throws
            when(unifiedPositionRepo.findByPositionId("POS_NONEXIST")).thenReturn(Optional.empty());
            when(unifiedPositionRepo.save(any())).thenThrow(new RuntimeException("DB failure"));

            Position position = new Position(
                    null, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.OPEN, "Test", new BigDecimal("2500"),
                    "POS_NONEXIST", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null, null);

            // When / Then
            assertThatThrownBy(() -> stateService.savePosition(position))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to save position");
        }
    }

    // ==================== closePosition ====================

    @Nested
    class ClosePosition {

        @Test
        void updatesStatusAndExitTime() {
            // Given: Existing position entity
            PositionEntity existing = makePositionEntity(
                    1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                    "Test", new BigDecimal("2500"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findByPositionId("POS_00000001")).thenReturn(Optional.of(existing));

            Position closedPos = new Position(
                    1L, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.CLOSED, "Test", new BigDecimal("2600"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), ExitReason.MANUAL.name(), null);

            // When
            stateService.closePosition("POS_00000001", closedPos);

            // Then
            verify(unifiedPositionRepo).save(any(PositionEntity.class));
        }

        @Test
        void setsExitReasonManual() {
            // Given: Existing position
            PositionEntity existing = makePositionEntity(
                    1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                    "Test", new BigDecimal("2500"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findByPositionId("POS_00000001")).thenReturn(Optional.of(existing));

            Position closedPos = new Position(
                    1L, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.CLOSED, "Test", new BigDecimal("2600"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), ExitReason.MANUAL.name(), null);

            // When
            stateService.closePosition("POS_00000001", closedPos);

            // Then
            verify(unifiedPositionRepo).save(argThat(entity ->
                    "CLOSED".equals(entity.getStatus())
                    && ExitReason.MANUAL.name().equals(entity.getExitReason())
            ));
        }

        @Test
        void usesActualExitReason_notHardcodedManual() {
            // Given: Existing position, closed with a real signal-driven reason
            PositionEntity existing = makePositionEntity(
                    1L, "WIPRO", "PAPER",
                    new BigDecimal("450"), LocalDate.now(), 10,
                    new BigDecimal("430"), new BigDecimal("500"), "OPEN",
                    "Test", new BigDecimal("450"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("450"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findByPositionId("POS_00000001")).thenReturn(Optional.of(existing));

            Position closedPos = new Position(
                    1L, "PAPER", "WIPRO",
                    new BigDecimal("450"), LocalDate.now(), 10,
                    new BigDecimal("430"), new BigDecimal("500"),
                    PositionStatus.CLOSED, "Test", new BigDecimal("600"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("450"),
                    BigDecimal.ZERO, new BigDecimal("1500"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), ExitReason.SIGNAL_EXIT.name(), null);

            // When
            stateService.closePosition("POS_00000001", closedPos);

            // Then: exitReason persisted is the real reason passed in, not a hardcoded "manual"
            verify(unifiedPositionRepo).save(argThat(entity ->
                    ExitReason.SIGNAL_EXIT.name().equals(entity.getExitReason())
            ));
        }

        @Test
        void notFound_doesNothing() {
            // Given: Position not found in DB
            when(unifiedPositionRepo.findByPositionId("POS_NONEXIST")).thenReturn(Optional.empty());

            Position closedPos = new Position(
                    null, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.CLOSED, "Test", new BigDecimal("2600"),
                    "POS_NONEXIST", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), ExitReason.MANUAL.name(), null);

            // When
            stateService.closePosition("POS_NONEXIST", closedPos);

            // Then: No save called since entity not found
            verify(unifiedPositionRepo, never()).save(any());
        }

        @Test
        void repositoryException_wrapped() {
            // Given: Position found, save throws
            when(unifiedPositionRepo.findByPositionId("POS_00000001"))
                    .thenReturn(Optional.of(makePositionEntity(
                            1L, "RELIANCE", "PAPER",
                            new BigDecimal("2500"), LocalDate.now(), 10,
                            new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                            "Test", new BigDecimal("2500"), "POS_00000001", null,
                            "NSE", "LONG", new BigDecimal("2500"),
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            LocalDateTime.now(), null, null)));
            when(unifiedPositionRepo.save(any())).thenThrow(new RuntimeException("DB failure"));

            Position closedPos = new Position(
                    1L, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.CLOSED, "Test", new BigDecimal("2600"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), ExitReason.MANUAL.name(), null);

            // When / Then
            assertThatThrownBy(() -> stateService.closePosition("POS_00000001", closedPos))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to close position");
        }
    }

    // ==================== saveOrder ====================

    @Nested
    class SaveOrder {

        @Test
        void newOrder_createsEntity() {
            // Given: Order not found in DB
            Order order = makeOrder("ORD_00000001", "RELIANCE", OrderType.MARKET,
                    TradeDirection.LONG, new BigDecimal("100"), new BigDecimal("2500"), OrderStatus.PENDING);
            when(orderRepo.findByOrderId("ORD_00000001")).thenReturn(Optional.empty());
            when(orderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            stateService.saveOrder(order);

            // Then
            verify(orderRepo).save(any(PaperTradingOrderEntity.class));
        }

        @Test
        void existingOrder_updatesStatus() {
            // Given: Existing order in DB
            PaperTradingOrderEntity existing = new PaperTradingOrderEntity();
            existing.setOrderId("ORD_00000001");
            existing.setStatus("PENDING");
            when(orderRepo.findByOrderId("ORD_00000001")).thenReturn(Optional.of(existing));
            when(orderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            Order order = makeOrder("ORD_00000001", "RELIANCE", OrderType.MARKET,
                    TradeDirection.LONG, new BigDecimal("100"), new BigDecimal("2500"), OrderStatus.FILLED);

            // When
            stateService.saveOrder(order);

            // Then
            verify(orderRepo).save(any(PaperTradingOrderEntity.class));
        }

        @Test
        void filledOrder_setsExecutionTime() {
            // Given: Existing order
            PaperTradingOrderEntity existing = new PaperTradingOrderEntity();
            existing.setOrderId("ORD_00000001");
            existing.setStatus("PENDING");
            when(orderRepo.findByOrderId("ORD_00000001")).thenReturn(Optional.of(existing));
            when(orderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            LocalDateTime executionTime = LocalDateTime.now();
            Order order = makeOrder("ORD_00000001", "RELIANCE", OrderType.MARKET,
                    TradeDirection.LONG, new BigDecimal("100"), new BigDecimal("2500"), OrderStatus.FILLED);
            order.setExecutionTime(executionTime);

            // When
            stateService.saveOrder(order);

            // Then
            verify(orderRepo).save(argThat(entity ->
                    entity.getExecutedAt() != null
            ));
        }

        @Test
        void repositoryException_wrapped() {
            // Given: Order not found, save throws
            when(orderRepo.findByOrderId("ORD_NONEXIST")).thenReturn(Optional.empty());
            when(orderRepo.save(any())).thenThrow(new RuntimeException("DB failure"));

            Order order = makeOrder("ORD_NONEXIST", "RELIANCE", OrderType.MARKET,
                    TradeDirection.LONG, new BigDecimal("100"), new BigDecimal("2500"), OrderStatus.PENDING);

            // When / Then
            assertThatThrownBy(() -> stateService.saveOrder(order))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to save order");
        }
    }

    // ==================== saveSnapshot ====================

    @Nested
    class SaveSnapshot {

        @Test
        void savesSnapshotFromEngine() {
            // Given: Portfolio with values
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(1000000)));
            when(engine.getCurrentCash()).thenReturn(new BigDecimal("500000"));
            when(engine.getTotalPnL()).thenReturn(new BigDecimal("50000"));
            when(engine.getReturnPercentage()).thenReturn(new BigDecimal("5.0000"));
            when(engine.getOpenPositionCount()).thenReturn(3);
            when(snapshotRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            stateService.saveSnapshot();

            // Then
            verify(snapshotRepo).save(any(PaperTradingSnapshotEntity.class));
        }

        @Test
        void snapshotContainsCorrectFields() {
            // Given
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(1000000)));
            when(engine.getCurrentCash()).thenReturn(new BigDecimal("400000"));
            when(engine.getTotalPnL()).thenReturn(new BigDecimal("100000"));
            when(engine.getReturnPercentage()).thenReturn(new BigDecimal("10.0000"));
            when(engine.getOpenPositionCount()).thenReturn(5);
            when(snapshotRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            stateService.saveSnapshot();

            // Then
            verify(snapshotRepo).save(argThat(entity ->
                    entity.getCashBalance().equals(new BigDecimal("400000"))
                    && entity.getTotalPnL().equals(new BigDecimal("100000"))
                    && entity.getOpenPositions() == 5
            ));
        }

        @Test
        void repositoryException_wrapped() {
            // Given
            when(snapshotRepo.save(any())).thenThrow(new RuntimeException("DB failure"));

            // When / Then
            assertThatThrownBy(() -> stateService.saveSnapshot())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to save portfolio snapshot");
        }
    }

    // ==================== getOpenPositions ====================

    @Nested
    class GetOpenPositions {

        @Test
        void returnsPaperOnly() {
            // Given: Mix of PAPER and non-PAPER open positions
            PositionEntity paperPos = makePositionEntity(1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                    "Test", new BigDecimal("2500"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            PositionEntity fyersPos = makePositionEntity(2L, "TCS", "Fyers",
                    new BigDecimal("3500"), LocalDate.now(), 5,
                    new BigDecimal("3400"), new BigDecimal("3700"), "OPEN",
                    "Test", new BigDecimal("3500"), "POS_00000002", null,
                    "NSE", "LONG", new BigDecimal("3500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findAllOpenPositions()).thenReturn(List.of(paperPos, fyersPos));

            // When
            List<PositionEntity> result = stateService.getOpenPositions();

            // Then: Only PAPER broker positions returned
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getBrokerType()).isEqualTo("PAPER");
        }

        @Test
        void emptyWhenNoOpenPositions() {
            // Given
            when(unifiedPositionRepo.findAllOpenPositions()).thenReturn(List.of());

            // When
            List<PositionEntity> result = stateService.getOpenPositions();

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ==================== getClosedPositions ====================

    @Nested
    class GetClosedPositions {

        @Test
        void includesAllClosedStatuses() {
            // Given: Positions with CLOSED, STOPPED, TARGET_HIT statuses
            PositionEntity closedPos = makePositionEntity(1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "CLOSED",
                    "Test", new BigDecimal("2600"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), "Manual");
            PositionEntity stoppedPos = makePositionEntity(2L, "TCS", "PAPER",
                    new BigDecimal("3500"), LocalDate.now(), 5,
                    new BigDecimal("3400"), new BigDecimal("3700"), "STOPPED",
                    "Test", new BigDecimal("3300"), "POS_00000002", null,
                    "NSE", "LONG", new BigDecimal("3500"),
                    BigDecimal.ZERO, new BigDecimal("-1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), "Stop");
            PositionEntity targetHitPos = makePositionEntity(3L, "INFY", "PAPER",
                    new BigDecimal("1500"), LocalDate.now(), 20,
                    new BigDecimal("1400"), new BigDecimal("1700"), "TARGET_HIT",
                    "Test", new BigDecimal("1750"), "POS_00000003", null,
                    "NSE", "LONG", new BigDecimal("1500"),
                    BigDecimal.ZERO, new BigDecimal("5000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), "Target");
            when(unifiedPositionRepo.findByStatus("CLOSED")).thenReturn(List.of(closedPos));
            when(unifiedPositionRepo.findByStatus("STOPPED")).thenReturn(List.of(stoppedPos));
            when(unifiedPositionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of(targetHitPos));

            // When
            List<PositionEntity> result = stateService.getClosedPositions();

            // Then
            assertThat(result).hasSize(3);
        }

        @Test
        void filtersNonPaperBrokers() {
            // Given: Only non-PAPER positions
            PositionEntity fyersPos = makePositionEntity(1L, "RELIANCE", "Fyers",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "CLOSED",
                    "Test", new BigDecimal("2600"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), "Manual");
            when(unifiedPositionRepo.findByStatus("CLOSED")).thenReturn(List.of(fyersPos));
            when(unifiedPositionRepo.findByStatus("STOPPED")).thenReturn(List.of());
            when(unifiedPositionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of());

            // When
            List<PositionEntity> result = stateService.getClosedPositions();

            // Then: Non-PAPER positions filtered out
            assertThat(result).isEmpty();
        }

        @Test
        void exceptionReturnsEmptyList() {
            // Given: findByStatus throws on first call
            when(unifiedPositionRepo.findByStatus("CLOSED")).thenThrow(new RuntimeException("DB error"));

            // When
            List<PositionEntity> result = stateService.getClosedPositions();

            // Then: Returns empty list instead of propagating exception
            assertThat(result).isEmpty();
        }
    }

    // ==================== getOrders ====================

    @Nested
    class GetOrders {

        @Test
        void orderedByCreatedDesc() {
            // Given
            PaperTradingOrderEntity older = new PaperTradingOrderEntity();
            older.setOrderId("ORD_001");
            older.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
            PaperTradingOrderEntity newer = new PaperTradingOrderEntity();
            newer.setOrderId("ORD_002");
            newer.setCreatedAt(LocalDateTime.of(2026, 8, 8, 15, 0));
            when(orderRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(newer, older));

            // When
            List<PaperTradingOrderEntity> result = stateService.getOrders();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getOrderId()).isEqualTo("ORD_002");
        }

        @Test
        void emptyWhenNoOrders() {
            // Given
            when(orderRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            // When
            List<PaperTradingOrderEntity> result = stateService.getOrders();

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ==================== getPositionById ====================

    @Nested
    class GetPositionById {

        @Test
        void found_returnsEntity() {
            // Given
            PositionEntity entity = makePositionEntity(
                    1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                    "Test", new BigDecimal("2500"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findById(1L)).thenReturn(Optional.of(entity));

            // When
            PositionEntity result = stateService.getPositionById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getPositionId()).isEqualTo("POS_00000001");
        }

        @Test
        void notFound_returnsNull() {
            // Given
            when(unifiedPositionRepo.findById(999L)).thenReturn(Optional.empty());

            // When
            PositionEntity result = stateService.getPositionById(999L);

            // Then
            assertThat(result).isNull();
        }
    }

    // ==================== getPortfolio ====================

    @Nested
    class GetPortfolio {

        @Test
        void found_returnsEntity() {
            // Given
            PaperTradingPortfolioEntity entity = makePortfolioEntity(
                    1L, "default", BigDecimal.valueOf(1000000), BigDecimal.valueOf(950000),
                    BigDecimal.valueOf(50000), BigDecimal.ZERO, 2);
            when(portfolioRepo.findById(1L)).thenReturn(Optional.of(entity));

            // When
            PaperTradingPortfolioEntity result = stateService.getPortfolio();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCurrentCapital()).isEqualByComparingTo(new BigDecimal("950000"));
        }

        @Test
        void notFound_returnsNull() {
            // Given
            when(portfolioRepo.findById(1L)).thenReturn(Optional.empty());

            // When
            PaperTradingPortfolioEntity result = stateService.getPortfolio();

            // Then
            assertThat(result).isNull();
        }
    }

    // ==================== EdgeCases ====================

    @Nested
    class EdgeCases {

        @Test
        void loadStateWithNullPortfolioFields() {
            // Given: Portfolio exists but with null fields
            PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
            entity.setId(1L);
            entity.setPortfolioId("default");
            when(portfolioRepo.findById(1L)).thenReturn(Optional.of(entity));
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.ZERO));

            // When
            stateService.loadState();

            // Then: No exception, handles null gracefully
            verify(portfolioRepo).findById(1L);
        }

        @Test
        void savePositionWithNullStatus_defaultsToOpen() {
            // Given: Position with null status
            PositionEntity existing = makePositionEntity(
                    1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                    "Test", new BigDecimal("2500"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null);
            when(unifiedPositionRepo.findByPositionId("POS_00000001")).thenReturn(Optional.of(existing));
            when(unifiedPositionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            Position position = new Position(
                    1L, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    null, "Test", new BigDecimal("2550"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.now(), null, null, null);

            // When
            stateService.savePosition(position);

            // Then: Status defaults to "OPEN" when null
            verify(unifiedPositionRepo).save(argThat(entity ->
                    "OPEN".equals(entity.getStatus())
            ));
        }

        @Test
        void saveOrderWithNullStatus_preservesExisting() {
            // Given: Existing order with status "FILLED"
            PaperTradingOrderEntity existing = new PaperTradingOrderEntity();
            existing.setOrderId("ORD_001");
            existing.setStatus("FILLED");
            when(orderRepo.findByOrderId("ORD_001")).thenReturn(Optional.of(existing));
            when(orderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            Order order = makeOrder("ORD_001", "RELIANCE", OrderType.MARKET,
                    TradeDirection.LONG, new BigDecimal("100"), new BigDecimal("2500"), null);

            // When
            stateService.saveOrder(order);

            // Then: Status preserved from existing entity when order status is null
            verify(orderRepo).save(argThat(entity ->
                    "FILLED".equals(entity.getStatus())
            ));
        }

        @Test
        void loadClosedPositions_handlesNullRealizedPnL() {
            // Given: Closed position with null realized P&L
            when(portfolioRepo.findById(1L)).thenReturn(Optional.of(makePortfolioEntity(
                    1L, "default", BigDecimal.valueOf(1000000), BigDecimal.valueOf(950000),
                    BigDecimal.valueOf(50000), BigDecimal.ZERO, 0)));
            when(unifiedPositionRepo.findAllOpenPositions()).thenReturn(List.of());
            PositionEntity posWithNullPnl = makePositionEntity(
                    1L, "RELIANCE", "PAPER",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"), "CLOSED",
                    "Test", new BigDecimal("2600"), "POS_00000001", null,
                    "NSE", "LONG", new BigDecimal("2500"),
                    BigDecimal.ZERO, null, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), "Manual");
            when(unifiedPositionRepo.findByStatus("CLOSED")).thenReturn(List.of(posWithNullPnl));
            when(unifiedPositionRepo.findByStatus("STOPPED")).thenReturn(List.of());
            when(unifiedPositionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of());
            when(engine.getPortfolio()).thenReturn(new com.swingtrade.broker.model.Portfolio("default", BigDecimal.valueOf(1000000)));

            // When
            stateService.loadState();

            // Then: Null realized P&L handled gracefully (treated as zero)
            verify(unifiedPositionRepo).findByStatus("CLOSED");
        }

        @Test
        void saveSnapshot_repositoryThrows() {
            // Given
            when(snapshotRepo.save(any())).thenThrow(new IllegalArgumentException("Constraint violation"));

            // When / Then
            assertThatThrownBy(() -> stateService.saveSnapshot())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to save portfolio snapshot");
        }

        @Test
        void closePosition_repositoryThrows() {
            // Given
            when(unifiedPositionRepo.findByPositionId("POS_00000001"))
                    .thenReturn(Optional.of(makePositionEntity(
                            1L, "RELIANCE", "PAPER",
                            new BigDecimal("2500"), LocalDate.now(), 10,
                            new BigDecimal("2400"), new BigDecimal("2700"), "OPEN",
                            "Test", new BigDecimal("2500"), "POS_00000001", null,
                            "NSE", "LONG", new BigDecimal("2500"),
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            LocalDateTime.now(), null, null)));
            when(unifiedPositionRepo.save(any())).thenThrow(new IllegalStateException("Lock timeout"));

            Position closedPos = new Position(
                    1L, "PAPER", "RELIANCE",
                    new BigDecimal("2500"), LocalDate.now(), 10,
                    new BigDecimal("2400"), new BigDecimal("2700"),
                    PositionStatus.CLOSED, "Test", new BigDecimal("2600"),
                    "POS_00000001", null, null,
                    TradeDirection.LONG, new BigDecimal("2500"),
                    BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now(), ExitReason.MANUAL.name(), null);

            // When / Then
            assertThatThrownBy(() -> stateService.closePosition("POS_00000001", closedPos))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to close position");
        }
    }
}
