package com.swingtrade.broker.service;

import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PaperTradingServiceImpl.
 * Tests that it properly delegates to PaperTradingEngine and handles null inputs.
 */
class PaperTradingServiceImplTest {

    private PaperTradingEngine paperTradingEngine;
    private PaperTradingServiceImpl service;
    private OrderManager orderManager;

    private EnginePair createPair(BigDecimal initialCapital) {
        PaperTradingProperties props = new PaperTradingProperties();
        props.setInitialBalance(initialCapital);
        props.setMaxCapitalPerPosition(new BigDecimal("20"));
        OrderManager om = new OrderManager();
        PaperTradingEngine pe = new PaperTradingEngine(om, new PositionManager(props), props,
                org.mockito.Mockito.mock(com.swingtrade.core.metrics.TradeMetrics.class),
                org.mockito.Mockito.mock(com.swingtrade.domain.service.TradingStatePersistence.class));
        return new EnginePair(pe, om);
    }

    @BeforeEach
    void setUp() {
        EnginePair pair = createPair(new BigDecimal("100000.00"));
        paperTradingEngine = pair.engine;
        orderManager = pair.orderManager;
        service = new PaperTradingServiceImpl(paperTradingEngine, orderManager, null);
    }

    @Test
    void testPlaceOrder_nullOrder_throwsException() {
        assertThatThrownBy(() -> service.placeOrder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order cannot be null");
    }

    @Test
    void testCancelOrder_nullOrderId_returnsFalse() {
        assertThat(service.cancelOrder(null)).isFalse();
    }

    @Test
    void testCancelOrder_emptyOrderId_returnsFalse() {
        assertThat(service.cancelOrder("")).isFalse();
    }

    @Test
    void testGetPortfolio_delegatesToEngine() {
        var result = service.getPortfolio();
        assertThat(result).isNotNull();
        assertThat(result.getPortfolioId()).isEqualTo("default-portfolio");
        assertThat(result.getCurrentCapital()).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    void testGetOpenPositions_delegatesToEngine() {
        List<Position> result = service.getOpenPositions();
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void testGetPosition_notFound() {
        var result = service.getPosition("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void testCalculateProfitLoss_nullPosition_throwsException() {
        assertThatThrownBy(() -> service.calculateProfitLoss(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Position cannot be null");
    }

    @Test
    void testGetMaxConcurrentPositions_returnsFromEngine() {
        assertThat(service.getMaxConcurrentPositions()).isEqualTo(5);
    }

    @Test
    void testGetMaxCapitalPerPosition_returnsFromEngine() {
        assertThat(service.getMaxCapitalPerPosition()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void testServiceIsPaperTradingImpl() {
        assertThat(service).isInstanceOf(PaperTradingServiceImpl.class);
    }

    @Test
    void testPlaceOrder_createsAndExecutesOrder() {
        EnginePair pair = createPair(new BigDecimal("100000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order = new Order();
        order.setSymbol("RELIANCE-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("5"));
        order.setType(OrderType.MARKET);
        order.setPrice(new BigDecimal("2500.00"));

        Order result = localService.placeOrder(order);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isNotNull();
        assertThat(result.getOrderId()).startsWith("ORD_");
        assertThat(result.getSymbol()).isEqualTo("RELIANCE-EQ");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(pair.engine.getOpenPositions()).hasSize(1);
    }

    @Test
    void testPlaceOrder_multipleOrders_createsMultiplePositions() {
        EnginePair pair = createPair(new BigDecimal("500000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order1 = createOrder(pair, "TCS-EQ", new BigDecimal("3500"));
        Order order2 = createOrder(pair, "HDFC-EQ", new BigDecimal("1450"));
        Order order3 = createOrder(pair, "INFY-EQ", new BigDecimal("1350"));

        localService.placeOrder(order1);
        localService.placeOrder(order2);
        localService.placeOrder(order3);

        assertThat(localService.getOpenPositions()).hasSize(3);
    }

    @Test
    void testCancelOrder_afterExecution_returnsFalse() {
        EnginePair pair = createPair(new BigDecimal("100000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order = createOrder(pair, "TCS-EQ", new BigDecimal("3500"));
        Order placed = localService.placeOrder(order);

        // Order is FILLED, so cancel should return false
        boolean result = localService.cancelOrder(placed.getOrderId());
        assertThat(result).isFalse();
    }

    @Test
    void testCancelOrder_nonexistent_returnsFalse() {
        assertThat(service.cancelOrder("nonexistent")).isFalse();
    }

    @Test
    void testServiceDelegationPattern() {
        EnginePair pair = createPair(new BigDecimal("100000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order = createOrder(pair, "KOTAKBANK-EQ", new BigDecimal("1700"));
        Order placed = localService.placeOrder(order);
        List<Position> positions = localService.getOpenPositions();

        assertThat(positions).hasSize(1);
        Position position = positions.get(0);

        var portfolio = localService.getPortfolio();
        assertThat(portfolio).isNotNull();
        assertThat(position.symbol()).isEqualTo("KOTAKBANK-EQ");

        var pnl = localService.calculateProfitLoss(position);
        assertThat(pnl).isNotNull();
        assertThat(localService.getMaxConcurrentPositions()).isEqualTo(5);
        assertThat(localService.getMaxCapitalPerPosition()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void testCalculateProfitLoss_profit() {
        EnginePair pair = createPair(new BigDecimal("100000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order = createOrder(pair, "HDFC-EQ", new BigDecimal("1450.00"));
        localService.placeOrder(order);

        Position position = pair.engine.getOpenPositions().get(0);
        Position updated = Position.of(
            position.id(), "PAPER", position.symbol(), position.entryPrice(), position.entryDate(),
            position.quantity(), position.stopLoss(), position.target(), position.status(),
            position.entryReason(), new BigDecimal("1500.00"),
            position.positionId(), position.brokerPositionId(), position.exchange(),
            position.direction(), position.averagePrice(), new BigDecimal("250.00"),
            position.realizedPnL(), position.marginUtilized(), position.entryTime(),
            position.exitTime(), position.exitReason(), position.orders());

        BigDecimal result = localService.calculateProfitLoss(updated);
        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void testCalculateProfitLoss_withLoss() {
        EnginePair pair = createPair(new BigDecimal("100000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order = createOrder(pair, "AXISBANK-EQ", new BigDecimal("1100.00"));
        localService.placeOrder(order);

        Position position = pair.engine.getOpenPositions().get(0);
        Position updated = Position.of(
            position.id(), "PAPER", position.symbol(), position.entryPrice(), position.entryDate(),
            position.quantity(), position.stopLoss(), position.target(), position.status(),
            position.entryReason(), new BigDecimal("1050.00"),
            position.positionId(), position.brokerPositionId(), position.exchange(),
            position.direction(), position.averagePrice(), new BigDecimal("-250.00"),
            position.realizedPnL(), position.marginUtilized(), position.entryTime(),
            position.exitTime(), position.exitReason(), position.orders());

        BigDecimal result = localService.calculateProfitLoss(updated);
        assertThat(result).isNegative();
        assertThat(result).isEqualByComparingTo(new BigDecimal("-250.00"));
    }

    @Test
    void testGetPosition_found() {
        EnginePair pair = createPair(new BigDecimal("100000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order = createOrder(pair, "INFY-EQ", new BigDecimal("1350.00"));
        localService.placeOrder(order);

        Position position = pair.engine.getOpenPositions().get(0);
        String positionId = position.positionId();

        var result = localService.getPosition(positionId);
        assertThat(result).isPresent();
        assertThat(result.get().symbol()).isEqualTo("INFY-EQ");
    }

    @Test
    void testPlaceOrder_withLargeCapital() {
        EnginePair pair = createPair(new BigDecimal("1000000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order = createOrder(pair, "RELIANCE-EQ", new BigDecimal("2500.00"));
        order.setQuantity(new BigDecimal("50"));
        Order result = localService.placeOrder(order);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(pair.engine.getOpenPositions()).hasSize(1);
    }

    @Test
    void testServiceNullHandling() {
        assertThatThrownBy(() -> service.placeOrder(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculateProfitLoss(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.cancelOrder(null)).isFalse();
        assertThat(service.cancelOrder("")).isFalse();
        assertThat(service.getPosition("nonexistent")).isEmpty();
    }

    @Test
    void testPlaceOrder_createsPositionWithCorrectSymbol() {
        EnginePair pair = createPair(new BigDecimal("100000.00"));
        PaperTradingServiceImpl localService = new PaperTradingServiceImpl(pair.engine, pair.orderManager, null);

        Order order = createOrder(pair, "SBIN-EQ", new BigDecimal("550.00"));
        localService.placeOrder(order);

        List<Position> positions = localService.getOpenPositions();
        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).symbol()).isEqualTo("SBIN-EQ");
    }

    private Order createOrder(EnginePair pair, String symbol, BigDecimal price) {
        Order order = new Order();
        order.setSymbol(symbol);
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("5"));
        order.setType(OrderType.MARKET);
        order.setPrice(price);
        return order;
    }

    private record EnginePair(PaperTradingEngine engine, OrderManager orderManager) {}
}