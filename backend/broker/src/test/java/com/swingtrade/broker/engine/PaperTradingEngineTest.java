package com.swingtrade.broker.engine;

import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.broker.model.Portfolio;
import com.swingtrade.domain.*;
import com.swingtrade.broker.service.PaperTradingStateService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaperTradingEngine covering signal execution, position capacity,
 * position sizing, portfolio entry/exit, triggers, P&L, queries, order management,
 * clearAll, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaperTradingEngineTest {

    private PaperTradingEngine engine;

    @Mock
    private OrderManager orderManager;
    @Mock
    private PositionManager positionManager;
    @Mock
    private PaperTradingProperties properties;
    @Mock
    private PaperTradingStateService stateService;

    private PaperTradingProperties testProperties;

    @BeforeEach
    void setUp() {
        testProperties = new PaperTradingProperties();
        testProperties.setInitialBalance(BigDecimal.valueOf(750000));
        testProperties.setMaxConcurrentPositions(5);
        testProperties.setMaxCapitalPerPosition(new BigDecimal("500000"));

        engine = new PaperTradingEngine(orderManager, positionManager, testProperties,
                org.mockito.Mockito.mock(com.swingtrade.core.metrics.TradeMetrics.class));
        engine.setStateService(stateService);
    }

    // ==================== Signal Execution ====================

    @Nested
    class SignalExecution {

        @Test
        void executeSignal_buyCreatesOrderAndPosition() {
            // Given: A BUY signal
            Signal signal = Signal.create("RELIANCE-EQ", LocalDate.now(), Signal.SignalType.BUY,
                    new BigDecimal("0.8"), "Golden cross on daily");
            BigDecimal currentPrice = new BigDecimal("10.00");

            Order pendingOrder = new Order();
            pendingOrder.setOrderId("ORD_00000001");
            pendingOrder.setSymbol("RELIANCE-EQ");
            pendingOrder.setDirection(TradeDirection.LONG);
            pendingOrder.setQuantity(BigDecimal.valueOf(100));
            pendingOrder.setPrice(currentPrice);
            pendingOrder.setStatus(OrderStatus.PENDING);

            when(orderManager.createBuyOrder(eq("RELIANCE-EQ"), anyInt(), eq(currentPrice)))
                    .thenReturn(pendingOrder);

            Order filledOrder = new Order();
            filledOrder.setOrderId("ORD_00000001");
            filledOrder.setSymbol("RELIANCE-EQ");
            filledOrder.setDirection(TradeDirection.LONG);
            filledOrder.setQuantity(BigDecimal.valueOf(100));
            filledOrder.setPrice(currentPrice);
            filledOrder.setStatus(OrderStatus.FILLED);
            filledOrder.setAdditionalProperties(new java.util.HashMap<>());

            when(orderManager.executeOrder("ORD_00000001", currentPrice)).thenReturn(filledOrder);
            lenient().when(positionManager.createPosition(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        String posId = inv.getArgument(0);
                        return new Position(null, "PAPER", "RELIANCE-EQ", currentPrice, LocalDate.now(),
                                100, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                                "Golden cross on daily", currentPrice, posId, null, null,
                                TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                                java.time.LocalDateTime.now(), null, null, null);
                    });
            when(positionManager.hasReachedPositionLimit()).thenReturn(false);

            // When
            Order result = engine.executeSignal(signal, currentPrice);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo("ORD_00000001");
            assertThat(result.getStatus()).isEqualTo(OrderStatus.FILLED);
            verify(orderManager).createBuyOrder("RELIANCE-EQ", 100, currentPrice);
            verify(orderManager).executeOrder("ORD_00000001", currentPrice);
            verify(stateService).saveOrder(filledOrder);
            verify(stateService).savePosition(any(Position.class));
            verify(stateService).savePortfolio();
        }

        @Test
        void executeSignal_nullSignal_returnsNull() {
            // Given: A null signal
            BigDecimal currentPrice = new BigDecimal("2500.00");

            // When
            Order result = engine.executeSignal(null, currentPrice);

            // Then
            assertThat(result).isNull();
            verify(orderManager, never()).createBuyOrder(anyString(), anyInt(), any());
        }

        @Test
        void executeSignal_sellSignal_returnsNull() {
            // Given: A SELL signal
            Signal signal = Signal.create("RELIANCE-EQ", LocalDate.now(), Signal.SignalType.SELL,
                    new BigDecimal("0.6"), "Bearish breakout");
            BigDecimal currentPrice = new BigDecimal("2500.00");

            // When
            Order result = engine.executeSignal(signal, currentPrice);

            // Then
            assertThat(result).isNull();
            verify(orderManager, never()).createBuyOrder(anyString(), anyInt(), any());
        }

        @Test
        void executeSignal_holdSignal_returnsNull() {
            // Given: A HOLD signal
            Signal signal = Signal.create("RELIANCE-EQ", LocalDate.now(), Signal.SignalType.HOLD,
                    new BigDecimal("0.5"), "No clear direction");
            BigDecimal currentPrice = new BigDecimal("2500.00");

            // When
            Order result = engine.executeSignal(signal, currentPrice);

            // Then
            assertThat(result).isNull();
            verify(orderManager, never()).createBuyOrder(anyString(), anyInt(), any());
        }

        @Test
        void executeSignal_metadataAttached() {
            // Given: A BUY signal with confidence, SL, target
            Signal signal = Signal.create("RELIANCE-EQ", LocalDate.now(), Signal.SignalType.BUY,
                    new BigDecimal("0.85"), "Strong bullish momentum");
            signal = new Signal(signal.id(), signal.symbol(), signal.date(), signal.type(),
                    signal.confidence(), signal.reasoning(),
                    new BigDecimal("10.00"), new BigDecimal("8.00"),
                    new BigDecimal("15.00"), new BigDecimal("2.5"),
                    signal.indicators(), signal.generatedAt(), signal.sentimentScore(),
                    signal.sentimentReasoning());
            BigDecimal currentPrice = new BigDecimal("10.00");

            Order pendingOrder = new Order();
            pendingOrder.setOrderId("ORD_00000001");
            pendingOrder.setSymbol("RELIANCE-EQ");
            pendingOrder.setDirection(TradeDirection.LONG);
            pendingOrder.setQuantity(BigDecimal.valueOf(100));
            pendingOrder.setPrice(currentPrice);
            pendingOrder.setStatus(OrderStatus.PENDING);

            when(orderManager.createBuyOrder(eq("RELIANCE-EQ"), anyInt(), eq(currentPrice)))
                    .thenReturn(pendingOrder);

            Order filledOrder = new Order();
            filledOrder.setOrderId("ORD_00000001");
            filledOrder.setSymbol("RELIANCE-EQ");
            filledOrder.setDirection(TradeDirection.LONG);
            filledOrder.setQuantity(BigDecimal.valueOf(100));
            filledOrder.setPrice(currentPrice);
            filledOrder.setStatus(OrderStatus.FILLED);
            filledOrder.setAdditionalProperties(new java.util.HashMap<>());

            when(orderManager.executeOrder("ORD_00000001", currentPrice)).thenReturn(filledOrder);
            lenient().when(positionManager.createPosition(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        String posId = inv.getArgument(0);
                        return new Position(null, "PAPER", "RELIANCE-EQ", currentPrice, LocalDate.now(),
                                100, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                                "Strong bullish momentum", currentPrice, posId, null, null,
                                TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                                java.time.LocalDateTime.now(), null, null, null);
                    });
            when(positionManager.hasReachedPositionLimit()).thenReturn(false);

            // When
            Order result = engine.executeSignal(signal, currentPrice);

            // Then: metadata is attached to the pending order before execution
            assertThat(result).isNotNull();
            assertThat(pendingOrder.getAdditionalProperties()).containsEntry("signalId", "");
            assertThat(pendingOrder.getAdditionalProperties()).containsEntry("signalReason", "Strong bullish momentum");
            assertThat(pendingOrder.getAdditionalProperties()).containsEntry("confidence", "0.85");
            assertThat(pendingOrder.getAdditionalProperties()).containsEntry("stopLoss", "8.00");
            assertThat(pendingOrder.getAdditionalProperties()).containsEntry("target", "15.00");
            assertThat(pendingOrder.getAdditionalProperties()).containsEntry("riskReward", "2.5");
        }
    }

    // ==================== Position Capacity ====================

    @Nested
    class PositionCapacity {

        @Test
        void validatePositionCapacity_withinLimits() {
            // Given: Sufficient capital and capacity
            BigDecimal entryPrice = new BigDecimal("100.00");
            int quantity = 10;

            Portfolio portfolio = mock(Portfolio.class);
            when(portfolio.getCurrentCapital()).thenReturn(new BigDecimal("500000"));

            when(positionManager.hasReachedPositionLimit()).thenReturn(false);
            when(properties.getMaxCapitalPerPosition()).thenReturn(new BigDecimal("20"));

            // When
            boolean canOpen = engine.validatePositionCapacity(entryPrice, quantity);

            // Then
            assertThat(canOpen).isTrue();
        }

        @Test
        void validatePositionCapacity_atMaxPositions() {
            // Given: Position limit reached
            when(positionManager.hasReachedPositionLimit()).thenReturn(true);
            when(positionManager.getMaxPositions()).thenReturn(5);

            // When
            boolean canOpen = engine.validatePositionCapacity(new BigDecimal("100"), 10);

            // Then
            assertThat(canOpen).isFalse();
        }

        @Test
        void validatePositionCapacity_zeroCapital() {
            // Given: Zero portfolio capital
            testProperties.setInitialBalance(BigDecimal.ZERO);
<<<<<<< HEAD
            engine = new PaperTradingEngine(orderManager, positionManager, testProperties, null, null);
=======
            engine = new PaperTradingEngine(orderManager, positionManager, testProperties,
                org.mockito.Mockito.mock(com.swingtrade.core.metrics.TradeMetrics.class));
>>>>>>> pr-93
            when(positionManager.hasReachedPositionLimit()).thenReturn(false);

            // When
            boolean canOpen = engine.validatePositionCapacity(new BigDecimal("100"), 10);

            // Then
            assertThat(canOpen).isFalse();
        }

        @Test
        void validatePositionCapacity_capitalRatioExceeded() {
            // Given: Small capital, large position, tight max capital per position
            when(positionManager.hasReachedPositionLimit()).thenReturn(false);
            testProperties.setMaxCapitalPerPosition(new BigDecimal("100"));
<<<<<<< HEAD
            engine = new PaperTradingEngine(orderManager, positionManager, testProperties, null, null);
=======
            engine = new PaperTradingEngine(orderManager, positionManager, testProperties,
                org.mockito.Mockito.mock(com.swingtrade.core.metrics.TradeMetrics.class));
>>>>>>> pr-93

            // When: 100 * 100 = 10000, ratio = 0.0133
            // maxCapitalPerPositionDiv100 = 100/100 = 1, 0.0133 > 1 => false, passes
            // Need bigger position: 100 * 1000 = 100000, ratio = 0.1333
            // 0.1333 > 1 => false still
            // Use 100 * 200000 = 20000000, ratio = 26.6667
            // 26.6667 > 1 => fails
            boolean canOpen = engine.validatePositionCapacity(new BigDecimal("100"), 200000);

            // Then
            assertThat(canOpen).isFalse();
        }

        @Test
        void canOpenPosition_delegatesToValidate() {
            // Given: Position within limits
            when(positionManager.hasReachedPositionLimit()).thenReturn(false);
            when(properties.getMaxCapitalPerPosition()).thenReturn(new BigDecimal("20"));

            // When
            boolean canOpen = engine.canOpenPosition(new BigDecimal("100"), 10);

            // Then
            assertThat(canOpen).isTrue();
        }
    }

    // ==================== Position Size Calculation ====================

    @Nested
    class PositionSizeCalc {

        @Test
        void calculatePositionSize_normalAtr() {
            // Given: Entry 1000, SL 990 (risk per share = 10)
            // riskPerTrade = 1% of capital = 7500
            // quantity = 7500 / 10 = 750
            BigDecimal entryPrice = new BigDecimal("1000.00");
            BigDecimal stopLoss = new BigDecimal("990.00");

            // When
            BigDecimal size = engine.calculatePositionSize("RELIANCE-EQ", entryPrice, stopLoss);

            // Then
            assertThat(size).isEqualByComparingTo("750");
        }

        @Test
        void calculatePositionSize_invalidSl_returnsDefault100() {
            // Given: Entry 100, SL 110 (risk per share negative)
            BigDecimal entryPrice = new BigDecimal("100.00");
            BigDecimal stopLoss = new BigDecimal("110.00");

            // When
            BigDecimal size = engine.calculatePositionSize("RELIANCE-EQ", entryPrice, stopLoss);

            // Then
            assertThat(size).isEqualTo(BigDecimal.valueOf(100));
        }

        @Test
        void calculatePositionSize_nullStopLoss_returnsDefault100() {
            // Given: Entry 100, null SL
            BigDecimal entryPrice = new BigDecimal("100.00");

            // When
            BigDecimal size = engine.calculatePositionSize("RELIANCE-EQ", entryPrice, null);

            // Then
            assertThat(size).isEqualTo(BigDecimal.valueOf(100));
        }

        @Test
        void calculatePositionSize_boundaryMinimum() {
            // Given: Very large risk per share so calculated qty < 100
            BigDecimal entryPrice = new BigDecimal("100.00");
            BigDecimal stopLoss = new BigDecimal("10.00"); // risk = 90, qty = 10000/90 = 111

            // When
            BigDecimal size = engine.calculatePositionSize("RELIANCE-EQ", entryPrice, stopLoss);

            // Then
            assertThat(size).isGreaterThanOrEqualTo(BigDecimal.valueOf(100));
        }
    }

    // ==================== Portfolio Entry ====================

    @Nested
    class PortfolioEntry {

        @Test
        void executePendingOrder_reducesCapital() {
            // Given: A filled order
            BigDecimal entryPrice = new BigDecimal("2500.00");
            BigDecimal quantity = BigDecimal.valueOf(100);

            Order filledOrder = new Order();
            filledOrder.setOrderId("ORD_00000001");
            filledOrder.setSymbol("RELIANCE-EQ");
            filledOrder.setDirection(TradeDirection.LONG);
            filledOrder.setQuantity(quantity);
            filledOrder.setPrice(entryPrice);
            filledOrder.setStatus(OrderStatus.FILLED);
            filledOrder.setAdditionalProperties(new java.util.HashMap<>());

            when(orderManager.executeOrder("ORD_00000001", entryPrice)).thenReturn(filledOrder);
            lenient().when(positionManager.createPosition(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        String posId = inv.getArgument(0);
                        return new Position(null, "PAPER", "RELIANCE-EQ", entryPrice, LocalDate.now(),
                                100, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                                "Test", entryPrice, posId, null, null,
                                TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                                java.time.LocalDateTime.now(), null, null, null);
                    });

            // When
            Order result = engine.executePendingOrder("ORD_00000001", entryPrice);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(OrderStatus.FILLED);
            // 750000 - 250000 - 5 (commission) = 499995
            assertThat(engine.getCurrentCash()).isEqualByComparingTo("499995");
        }

        @Test
        void executePendingOrder_commissionDeducted() {
            // Given: A filled order
            BigDecimal entryPrice = new BigDecimal("100.00");
            BigDecimal quantity = BigDecimal.valueOf(10);

            Order filledOrder = new Order();
            filledOrder.setOrderId("ORD_00000001");
            filledOrder.setSymbol("RELIANCE-EQ");
            filledOrder.setDirection(TradeDirection.LONG);
            filledOrder.setQuantity(quantity);
            filledOrder.setPrice(entryPrice);
            filledOrder.setStatus(OrderStatus.FILLED);
            filledOrder.setAdditionalProperties(new java.util.HashMap<>());

            when(orderManager.executeOrder("ORD_00000001", entryPrice)).thenReturn(filledOrder);
            lenient().when(positionManager.createPosition(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        String posId = inv.getArgument(0);
                        return new Position(null, "PAPER", "RELIANCE-EQ", entryPrice, LocalDate.now(),
                                10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                                "Test", entryPrice, posId, null, null,
                                TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                                java.time.LocalDateTime.now(), null, null, null);
                    });

            // When
            engine.executePendingOrder("ORD_00000001", entryPrice);

            // Then: commission = 10 * 0.05 = 0.5, positionValue = 1000
            // capital = 750000 - 1000 - 0.5 = 748999.5
            assertThat(engine.getCurrentCash()).isEqualByComparingTo("748999.50");
        }

        @Test
        void executePendingOrder_positionCreated() {
            // Given: A filled order
            BigDecimal entryPrice = new BigDecimal("2500.00");

            Order filledOrder = new Order();
            filledOrder.setOrderId("ORD_00000001");
            filledOrder.setSymbol("RELIANCE-EQ");
            filledOrder.setDirection(TradeDirection.LONG);
            filledOrder.setQuantity(BigDecimal.valueOf(100));
            filledOrder.setPrice(entryPrice);
            filledOrder.setStatus(OrderStatus.FILLED);
            filledOrder.setAdditionalProperties(new java.util.HashMap<>());

            when(orderManager.executeOrder("ORD_00000001", entryPrice)).thenReturn(filledOrder);
            lenient().when(positionManager.createPosition(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        String posId = inv.getArgument(0);
                        return new Position(null, "PAPER", "RELIANCE-EQ", entryPrice, LocalDate.now(),
                                100, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                                "Test", entryPrice, posId, null, null,
                                TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                                java.time.LocalDateTime.now(), null, null, null);
                    });

            // When
            engine.executePendingOrder("ORD_00000001", entryPrice);

            // Then
            verify(stateService).savePosition(any(Position.class));
        }
    }

    // ==================== Portfolio Exit ====================

    @Nested
    class PortfolioExit {

        @Test
        void closePosition_increasesCapital() {
            // Given: An open position
            BigDecimal entryPrice = new BigDecimal("100.00");
            Position position = new Position(null, "PAPER", "RELIANCE-EQ", entryPrice, LocalDate.now(),
                    10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("105.00"), "POS_00000001", null, null,
                    TradeDirection.LONG, null, new BigDecimal("50.00"), BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.closePosition("POS_00000001", new BigDecimal("105.00"), "manual_close"))
                    .thenReturn(position);

            // Simulate position entry to set correct capital state
            BigDecimal exitPrice = new BigDecimal("105.00");
            Position closedPos = new Position(null, "PAPER", "RELIANCE-EQ", entryPrice, LocalDate.now(),
                    10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", exitPrice, "POS_00000001", null, null,
                    TradeDirection.LONG, null, entryPrice, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);
            when(positionManager.getPosition("POS_00000001")).thenReturn(closedPos);
            // Capital after entry: 750000 - 1000 - 0.5 = 748999.5
            engine.getPortfolio().setCurrentCapital(new BigDecimal("748999.5"));

            // When
            engine.closePosition("POS_00000001", new BigDecimal("105.00"), "manual_close");

            // Then: exitValue = 105*10 = 1050, commission = 10*0.05 = 0.5
            // netProceeds = 1049.5, capital = 748999.5 + 1049.5 = 750049.0
            assertThat(engine.getCurrentCash()).isEqualByComparingTo("750049.0");
        }

        @Test
        void closePosition_commissionDeducted() {
            // Given: An open position
            BigDecimal entryPrice = new BigDecimal("100.00");

            // Simulate position entry to set correct capital state
            when(positionManager.closePosition("POS_00000001", new BigDecimal("105.00"), "manual_close"))
                    .thenAnswer(inv -> new Position(null, "PAPER", "RELIANCE-EQ", entryPrice, LocalDate.now(),
                            10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                            "Test", new BigDecimal("105.00"), "POS_00000001", null, null,
                            TradeDirection.LONG, null, entryPrice, BigDecimal.ZERO, null,
                            java.time.LocalDateTime.now(), null, null, null));
            // Capital after entry: 750000 - 1000 - 0.5 = 748999.5
            engine.getPortfolio().setCurrentCapital(new BigDecimal("748999.5"));

            // When
            engine.closePosition("POS_00000001", new BigDecimal("105.00"), "manual_close");

            // Then: exitValue = 105*10 = 1050, commission = 0.5, netProceeds = 1049.5
            // capital = 748999.5 + 1049.5 = 750049.0
            assertThat(engine.getCurrentCash()).isEqualByComparingTo("750049.0");
        }

        @Test
        void closePosition_persistsState() {
            // Given: An open position
            Position position = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("105.00"), "POS_00000001", null, null,
                    TradeDirection.LONG, null, new BigDecimal("50.00"), BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.closePosition("POS_00000001", new BigDecimal("105.00"), "manual_close"))
                    .thenReturn(position);

            // When
            engine.closePosition("POS_00000001", new BigDecimal("105.00"), "manual_close");

            // Then
            verify(stateService).closePosition("POS_00000001", position);
            verify(stateService).savePortfolio();
        }
    }

    // ==================== Position Triggers Via Candle ====================

    @Nested
    class PositionTriggersViaCandle {

        @Test
        void longPosition_slHit() {
            // Given: A long position with SL 90
            Position position = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getOpenPositions()).thenReturn(List.of(position));

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("92"), new BigDecimal("95"), new BigDecimal("88"),
                    new BigDecimal("91"), 100000L, new BigDecimal("91"));

            when(positionManager.findOpenPositionBySymbol("RELIANCE-EQ")).thenReturn(position);
            when(positionManager.getPosition("POS_00000001")).thenReturn(position);

            // When
            engine.checkPositionTriggers("RELIANCE-EQ", candle);

            // Then
            verify(positionManager).checkPositionTriggers(position, candle);
        }

        @Test
        void longPosition_tpHit() {
            // Given: A long position with target 125
            Position position = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getOpenPositions()).thenReturn(List.of(position));
            when(positionManager.findOpenPositionBySymbol("RELIANCE-EQ")).thenReturn(position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("120"), new BigDecimal("128"), new BigDecimal("121"),
                    new BigDecimal("126"), 100000L, new BigDecimal("126"));

            // When
            engine.checkPositionTriggers("RELIANCE-EQ", candle);

            // Then
            verify(positionManager).checkPositionTriggers(position, candle);
        }

        @Test
        void shortPosition_slHit() {
            // Given: A short position with SL 110
            Position position = new Position(null, "PAPER", "TCS-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, new BigDecimal("110.00"), new BigDecimal("75.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.SHORT, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getOpenPositions()).thenReturn(List.of(position));
            when(positionManager.findOpenPositionBySymbol("TCS-EQ")).thenReturn(position);

            OhlcvCandle candle = new OhlcvCandle("TCS-EQ", LocalDate.now(),
                    new BigDecimal("108"), new BigDecimal("112"), new BigDecimal("109"),
                    new BigDecimal("111"), 50000L, new BigDecimal("111"));

            // When
            engine.checkPositionTriggers("TCS-EQ", candle);

            // Then
            verify(positionManager).checkPositionTriggers(position, candle);
        }

        @Test
        void shortPosition_tpHit() {
            // Given: A short position with target 75
            Position position = new Position(null, "PAPER", "TCS-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, new BigDecimal("110.00"), new BigDecimal("75.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.SHORT, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getOpenPositions()).thenReturn(List.of(position));
            when(positionManager.findOpenPositionBySymbol("TCS-EQ")).thenReturn(position);

            OhlcvCandle candle = new OhlcvCandle("TCS-EQ", LocalDate.now(),
                    new BigDecimal("70"), new BigDecimal("78"), new BigDecimal("72"),
                    new BigDecimal("74"), 50000L, new BigDecimal("74"));

            // When
            engine.checkPositionTriggers("TCS-EQ", candle);

            // Then
            verify(positionManager).checkPositionTriggers(position, candle);
        }

        @Test
        void noTrigger() {
            // Given: A long position, candle within SL-TP range
            Position position = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getOpenPositions()).thenReturn(List.of(position));
            when(positionManager.findOpenPositionBySymbol("RELIANCE-EQ")).thenReturn(position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("98"), new BigDecimal("103"), new BigDecimal("97"),
                    new BigDecimal("101"), 100000L, new BigDecimal("101"));

            // When
            engine.checkPositionTriggers("RELIANCE-EQ", candle);

            // Then
            verify(positionManager).checkPositionTriggers(position, candle);
        }
    }

    // ==================== P&L Tracking ====================

    @Nested
    class PnLTracking {

        @Test
        void singleUnrealizedPnL() {
            // Given: One open position
            Position position = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("110.00"), "POS_00000001", null, null,
                    TradeDirection.LONG, null, new BigDecimal("100.00"), BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getOpenPositions()).thenReturn(List.of(position));
            when(positionManager.getTotalUnrealizedPnL()).thenReturn(new BigDecimal("100.00"));
            when(positionManager.getTotalRealizedPnL()).thenReturn(BigDecimal.ZERO);

            // When
            BigDecimal total = engine.getTotalUnrealizedPnL();

            // Then
            assertThat(total).isEqualTo(new BigDecimal("100.00"));
        }

        @Test
        void multipleUnrealizedPnL() {
            // Given: Two open positions
            Position p1 = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("110.00"), "POS_00000001", null, null,
                    TradeDirection.LONG, null, new BigDecimal("100.00"), BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);
            Position p2 = new Position(null, "PAPER", "TCS-EQ", new BigDecimal("200.00"),
                    LocalDate.now(), 5, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("210.00"), "POS_00000002", null, null,
                    TradeDirection.LONG, null, new BigDecimal("50.00"), BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getOpenPositions()).thenReturn(List.of(p1, p2));
            when(positionManager.getTotalUnrealizedPnL()).thenReturn(new BigDecimal("150.00"));
            when(positionManager.getTotalRealizedPnL()).thenReturn(BigDecimal.ZERO);

            // When
            BigDecimal total = engine.getTotalUnrealizedPnL();

            // Then
            assertThat(total).isEqualTo(new BigDecimal("150.00"));
        }

        @Test
        void realizedPnL() {
            // Given: A closed position with realized P&L
            when(positionManager.getTotalRealizedPnL()).thenReturn(new BigDecimal("500.00"));
            when(positionManager.getTotalUnrealizedPnL()).thenReturn(BigDecimal.ZERO);

            // When
            BigDecimal realized = engine.getTotalRealizedPnL();

            // Then
            assertThat(realized).isEqualTo(new BigDecimal("500.00"));
        }

        @Test
        void totalPnL() {
            // Given: Both realized and unrealized P&L
            when(positionManager.getTotalRealizedPnL()).thenReturn(new BigDecimal("500.00"));
            when(positionManager.getTotalUnrealizedPnL()).thenReturn(new BigDecimal("100.00"));

            // When
            BigDecimal total = engine.getTotalPnL();

            // Then
            assertThat(total).isEqualTo(new BigDecimal("600.00"));
        }

        @Test
        void returnPercentage() {
            // Given: Total P&L of 100000 on initial capital 1000000
            when(positionManager.getTotalRealizedPnL()).thenReturn(new BigDecimal("50000.00"));
            when(positionManager.getTotalUnrealizedPnL()).thenReturn(new BigDecimal("50000.00"));

            // When
            BigDecimal pct = engine.getReturnPercentage();

            // Then: 100000 / 750000 * 100 = 13.33% (HALF_UP, 4 decimal places)
            assertThat(pct).isEqualByComparingTo("13.3300");
        }

        @Test
        void returnPercentage_zeroCapital() {
            // Given: Zero initial capital (override properties)
            testProperties.setInitialBalance(BigDecimal.ZERO);
<<<<<<< HEAD
            engine = new PaperTradingEngine(orderManager, positionManager, testProperties, null, null);
=======
            engine = new PaperTradingEngine(orderManager, positionManager, testProperties,
                org.mockito.Mockito.mock(com.swingtrade.core.metrics.TradeMetrics.class));
>>>>>>> pr-93

            // When
            BigDecimal pct = engine.getReturnPercentage();

            // Then
            assertThat(pct).isEqualTo(BigDecimal.ZERO);
        }
    }

    // ==================== Portfolio Queries ====================

    @Nested
    class PortfolioQueries {

        @Test
        void getOpenPositions() {
            // Given: Two open positions
            Position p1 = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("105.00"), "POS_00000001", null, null,
                    TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);
            Position p2 = new Position(null, "PAPER", "TCS-EQ", new BigDecimal("200.00"),
                    LocalDate.now(), 5, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("205.00"), "POS_00000002", null, null,
                    TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getOpenPositions()).thenReturn(List.of(p1, p2));

            // When
            List<Position> open = engine.getOpenPositions();

            // Then
            assertThat(open).hasSize(2);
            assertThat(open.get(0).symbol()).isEqualTo("RELIANCE-EQ");
            assertThat(open.get(1).symbol()).isEqualTo("TCS-EQ");
        }

        @Test
        void getClosedPositions() {
            // Given: Two closed positions
            Position c1 = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.CLOSED,
                    "Test", new BigDecimal("105.00"), "POS_00000001", null, null,
                    TradeDirection.LONG, null, BigDecimal.ZERO, new BigDecimal("50.00"), null,
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), "Manual", null);
            Position c2 = new Position(null, "PAPER", "TCS-EQ", new BigDecimal("200.00"),
                    LocalDate.now(), 5, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.TARGET_HIT,
                    "Test", new BigDecimal("210.00"), "POS_00000002", null, null,
                    TradeDirection.LONG, null, BigDecimal.ZERO, new BigDecimal("50.00"), null,
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), "Target hit", null);

            when(positionManager.getClosedPositions()).thenReturn(List.of(c1, c2));

            // When
            List<Position> closed = engine.getClosedPositions();

            // Then
            assertThat(closed).hasSize(2);
        }

        @Test
        void getPosition() {
            // Given: A position in the manager
            Position position = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("105.00"), "POS_00000001", null, null,
                    TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.getPosition("POS_00000001")).thenReturn(position);

            // When
            Optional<Position> found = engine.getPosition("POS_00000001");

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().symbol()).isEqualTo("RELIANCE-EQ");
        }

        @Test
        void getPosition_notFound() {
            // Given: No position with that ID
            when(positionManager.getPosition("POS_NONEXIST")).thenReturn(null);

            // When
            Optional<Position> found = engine.getPosition("POS_NONEXIST");

            // Then
            assertThat(found).isEmpty();
        }

        @Test
        void findOpenPositionBySymbol() {
            // Given: A position for RELIANCE-EQ
            Position position = new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                    LocalDate.now(), 10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", new BigDecimal("105.00"), "POS_00000001", null, null,
                    TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            when(positionManager.findOpenPositionBySymbol("RELIANCE-EQ")).thenReturn(position);

            // When
            Position found = engine.findOpenPositionBySymbol("RELIANCE-EQ");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.symbol()).isEqualTo("RELIANCE-EQ");
        }

        @Test
        void findOpenPositionBySymbol_notFound() {
            // Given: No position for the symbol
            when(positionManager.findOpenPositionBySymbol("TCS-EQ")).thenReturn(null);

            // When
            Position found = engine.findOpenPositionBySymbol("TCS-EQ");

            // Then
            assertThat(found).isNull();
        }
    }

    // ==================== Order Management ====================

    @Nested
    class OrderManagement {

        @Test
        void getPendingOrders() {
            // Given: Two pending orders
            Order o1 = new Order();
            o1.setOrderId("ORD_00000001");
            o1.setStatus(OrderStatus.PENDING);
            Order o2 = new Order();
            o2.setOrderId("ORD_00000002");
            o2.setStatus(OrderStatus.PENDING);

            when(orderManager.getPendingOrders()).thenReturn(List.of(o1, o2));

            // When
            List<Order> pending = engine.getPendingOrders();

            // Then
            assertThat(pending).hasSize(2);
        }

        @Test
        void cancelOrder() {
            // Given: A pending order
            when(orderManager.cancelOrder("ORD_00000001")).thenReturn(true);

            // When
            boolean result = engine.cancelOrder("ORD_00000001");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void cancelOrder_notFound() {
            // Given: A non-existent order
            when(orderManager.cancelOrder("ORD_NONEXIST")).thenReturn(false);

            // When
            boolean result = engine.cancelOrder("ORD_NONEXIST");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void executePendingOrder() {
            // Given: A filled order
            Order filledOrder = new Order();
            filledOrder.setOrderId("ORD_00000001");
            filledOrder.setSymbol("RELIANCE-EQ");
            filledOrder.setDirection(TradeDirection.LONG);
            filledOrder.setQuantity(BigDecimal.valueOf(10));
            filledOrder.setPrice(new BigDecimal("100.00"));
            filledOrder.setStatus(OrderStatus.FILLED);
            filledOrder.setAdditionalProperties(new java.util.HashMap<>());

            when(orderManager.executeOrder("ORD_00000001", new BigDecimal("100.00"))).thenReturn(filledOrder);
            lenient().when(positionManager.createPosition(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        String posId = inv.getArgument(0);
                        return new Position(null, "PAPER", "RELIANCE-EQ", new BigDecimal("100.00"),
                                LocalDate.now(), 10, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                                "Test", new BigDecimal("100.00"), posId, null, null,
                                TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                                java.time.LocalDateTime.now(), null, null, null);
                    });

            // When
            Order result = engine.executePendingOrder("ORD_00000001", new BigDecimal("100.00"));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(OrderStatus.FILLED);
        }
    }

    // ==================== Clear All ====================

    @Nested
    class ClearAll {

        @Test
        void clearAll_resetsPositions() {
            // Given: Open positions
            when(positionManager.getOpenPositionCount()).thenReturn(2);

            // When
            engine.clearAll();

            // Then
            verify(positionManager).clearAllPositions();
        }

        @Test
        void clearAll_resetsOrders() {
            // Given: Pending orders
            when(orderManager.getPendingOrderCount()).thenReturn(3);

            // When
            engine.clearAll();

            // Then
            verify(orderManager).clearAllOrders();
        }

        @Test
        void clearAll_resetsCapital() {
            // When
            engine.clearAll();

            // Then
            assertThat(engine.getCurrentCash()).isEqualTo(new BigDecimal("750000"));
        }

        @Test
        void clearAll_resetsCounters() {
            // When
            engine.clearAll();

            // Then
            verify(positionManager).clearAllPositions();
            verify(orderManager).clearAllOrders();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void getPortfolio() {
            // When
            Portfolio portfolio = engine.getPortfolio();

            // Then
            assertThat(portfolio).isNotNull();
            assertThat(portfolio.getCurrentCapital()).isEqualTo(new BigDecimal("750000"));
        }

        @Test
        void getCurrentCash() {
            // When
            BigDecimal cash = engine.getCurrentCash();

            // Then
            assertThat(cash).isEqualTo(new BigDecimal("750000"));
        }

        @Test
        void getInitialCapital() {
            // When
            BigDecimal capital = engine.getInitialCapital();

            // Then
            assertThat(capital).isEqualTo(new BigDecimal("750000"));
        }

        @Test
        void getMaxConcurrentPositions() {
            // When
            int max = engine.getMaxConcurrentPositions();

            // Then
            assertThat(max).isEqualTo(5);
        }

        @Test
        void getMaxCapitalPerPosition() {
            // When
            BigDecimal max = engine.getMaxCapitalPerPosition();

            // Then
            assertThat(max).isEqualTo(new BigDecimal("500000"));
        }

        @Test
        void getOpenPositionCount() {
            // Given: 3 open positions
            when(positionManager.getOpenPositionCount()).thenReturn(3);

            // When
            int count = engine.getOpenPositionCount();

            // Then
            assertThat(count).isEqualTo(3);
        }

        @Test
        void updatePositions() {
            // Given: Candle data
            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("98"),
                    new BigDecimal("103"), 100000L, new BigDecimal("103"));

            when(positionManager.updatePositionsWithCandleData("RELIANCE-EQ", candle))
                    .thenReturn(List.of());

            // When
            engine.updatePositions(candle);

            // Then
            verify(positionManager).updatePositionsWithCandleData("RELIANCE-EQ", candle);
        }

        @Test
        void updatePositionsFromDomain() {
            // Given: A domain candle
            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("98"),
                    new BigDecimal("103"), 100000L, new BigDecimal("103"));

            when(positionManager.updatePositionsWithCandleData("RELIANCE-EQ", candle))
                    .thenReturn(List.of());

            // When
            engine.updatePositionsFromDomain(candle);

            // Then
            verify(positionManager).updatePositionsWithCandleData("RELIANCE-EQ", candle);
        }

        @Test
        void calculateCommission_normal() {
            // Given: An order
            Order order = new Order();
            order.setQuantity(BigDecimal.valueOf(100));

            // When
            BigDecimal commission = engine.calculateCommission(order);

            // Then: 100 * 0.05 = 5
            assertThat(commission).isEqualTo(new BigDecimal("5.00"));
        }

        @Test
        void calculateCommission_nullOrder_returnsZero() {
            // When
            BigDecimal commission = engine.calculateCommission(null);

            // Then
            assertThat(commission).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void calculateCommission_zeroQuantity_returnsZero() {
            // Given: An order with zero quantity
            Order order = new Order();
            order.setQuantity(BigDecimal.ZERO);

            // When
            BigDecimal commission = engine.calculateCommission(order);

            // Then
            assertThat(commission).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void calculateStopLossFromOrder_nullOrder_returnsNull() {
            // When
            BigDecimal sl = engine.calculateStopLossFromOrder(null);

            // Then
            assertThat(sl).isNull();
        }

        @Test
        void calculateTargetFromOrder_nullOrder_returnsNull() {
            // When
            BigDecimal target = engine.calculateTargetFromOrder(null);

            // Then
            assertThat(target).isNull();
        }

        @Test
        void getATRFromOrder_nullOrder_returnsNull() {
            // When
            BigDecimal atr = engine.getATRFromOrder(null);

            // Then
            assertThat(atr).isNull();
        }

        @Test
        void getATRFromOrder_withAtrInMetadata() {
            // Given: An order with ATR in metadata
            Order order = new Order();
            java.util.Map<String, Object> props = new java.util.HashMap<>();
            props.put("atr", "5.00");
            order.setAdditionalProperties(props);

            // When
            BigDecimal atr = engine.getATRFromOrder(order);

            // Then
            assertThat(atr).isEqualTo(new BigDecimal("5.00"));
        }

        @Test
        void createPositionFromOrder_nonFilled_throwsException() {
            // Given: A pending order
            Order order = new Order();
            order.setStatus(OrderStatus.PENDING);

            // When / Then
            assertThatThrownBy(() -> engine.createPositionFromOrder(order))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only create position from FILLED order");
        }

        @Test
        void closePosition_byDbId_notFound() {
            // Given: State service returns null for position lookup
            when(stateService.getPositionById(1L)).thenReturn(null);

            // When / Then
            assertThatThrownBy(() -> engine.closePosition(1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Position not found: 1");
        }

        @Test
        void clearAll_emptyState() {
            // When
            engine.clearAll();

            // Then
            assertThat(engine.getCurrentCash()).isEqualTo(new BigDecimal("750000"));
            verify(positionManager).clearAllPositions();
            verify(orderManager).clearAllOrders();
        }
    }
}
