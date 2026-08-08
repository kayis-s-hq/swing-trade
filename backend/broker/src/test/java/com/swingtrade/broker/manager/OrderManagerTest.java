package com.swingtrade.broker.manager;

import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OrderManager covering order creation, validation, execution,
 * cancellation, queries, execution validation, commission, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class OrderManagerTest {

    private OrderManager orderManager;

    @BeforeEach
    void setUp() {
        orderManager = new OrderManager();
    }

    // ==================== Order Creation ====================

    @Nested
    class OrderCreation {

        @Test
        void createMarketOrder_long() {
            // Given: A valid market order request
            String symbol = "RELIANCE-EQ";
            TradeDirection direction = TradeDirection.LONG;
            int quantity = 10;
            BigDecimal price = new BigDecimal("2500.00");

            // When
            Order order = orderManager.createMarketOrder(symbol, direction, quantity, price);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getOrderId()).startsWith("ORD_");
            assertThat(order.getSymbol()).isEqualTo(symbol);
            assertThat(order.getType()).isEqualTo(OrderType.MARKET);
            assertThat(order.getDirection()).isEqualTo(direction);
            assertThat(order.getQuantity()).isEqualTo(BigDecimal.valueOf(quantity));
            assertThat(order.getPrice()).isEqualTo(price);
            assertThat(order.getLimitPrice()).isNull();
            assertThat(order.getStopPrice()).isNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        void createMarketOrder_short() {
            // Given: A short market order request
            String symbol = "TCS-EQ";
            TradeDirection direction = TradeDirection.SHORT;
            int quantity = 5;
            BigDecimal price = new BigDecimal("3500.00");

            // When
            Order order = orderManager.createMarketOrder(symbol, direction, quantity, price);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getDirection()).isEqualTo(direction);
            assertThat(order.getType()).isEqualTo(OrderType.MARKET);
        }

        @Test
        void createLimitOrder() {
            // Given: A limit order request
            String symbol = "INFY-EQ";
            TradeDirection direction = TradeDirection.LONG;
            int quantity = 20;
            BigDecimal limitPrice = new BigDecimal("1350.00");

            // When
            Order order = orderManager.createLimitOrder(symbol, direction, quantity, limitPrice);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getType()).isEqualTo(OrderType.LIMIT);
            assertThat(order.getLimitPrice()).isEqualTo(limitPrice);
            assertThat(order.getPrice()).isEqualTo(limitPrice);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        void createBuyOrder() {
            // Given: A buy order request
            String symbol = "HDFC-EQ";
            int quantity = 15;
            BigDecimal price = new BigDecimal("1450.00");

            // When
            Order order = orderManager.createBuyOrder(symbol, quantity, price);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getDirection()).isEqualTo(TradeDirection.LONG);
            assertThat(order.getType()).isEqualTo(OrderType.MARKET);
            assertThat(order.getSymbol()).isEqualTo(symbol);
        }

        @Test
        void createSellOrder() {
            // Given: A sell order request
            String symbol = "SBIN-EQ";
            int quantity = 10;
            BigDecimal price = new BigDecimal("550.00");

            // When
            Order order = orderManager.createSellOrder(symbol, quantity, price);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getDirection()).isEqualTo(TradeDirection.SHORT);
            assertThat(order.getType()).isEqualTo(OrderType.MARKET);
            assertThat(order.getSymbol()).isEqualTo(symbol);
        }

        @Test
        void createStopLossOrder() {
            // Given: A stop loss order request
            String symbol = "RELIANCE-EQ";
            TradeDirection direction = TradeDirection.LONG;
            int quantity = 10;
            BigDecimal stopPrice = new BigDecimal("2400.00");

            // When
            Order order = orderManager.createStopLossOrder(symbol, direction, quantity, stopPrice);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getType()).isEqualTo(OrderType.STOP_LOSS);
            assertThat(order.getStopPrice()).isEqualTo(stopPrice);
            assertThat(order.getPrice()).isNull();
            assertThat(order.getLimitPrice()).isNull();
        }

        @Test
        void createTakeProfitOrder() {
            // Given: A take profit order request
            String symbol = "TCS-EQ";
            TradeDirection direction = TradeDirection.SHORT;
            int quantity = 5;
            BigDecimal targetPrice = new BigDecimal("3400.00");

            // When
            Order order = orderManager.createTakeProfitOrder(symbol, direction, quantity, targetPrice);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getType()).isEqualTo(OrderType.TAKE_PROFIT);
            assertThat(order.getStopPrice()).isEqualTo(targetPrice);
        }

        @Test
        void createStopOrder() {
            // Given: A stop order request
            String symbol = "INFY-EQ";
            TradeDirection direction = TradeDirection.LONG;
            int quantity = 25;
            BigDecimal stopPrice = new BigDecimal("1300.00");

            // When
            Order order = orderManager.createStopOrder(symbol, direction, quantity, stopPrice);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getType()).isEqualTo(OrderType.STOP);
            assertThat(order.getStopPrice()).isEqualTo(stopPrice);
        }

        @Test
        void createStopLimitOrder() {
            // Given: A stop-limit order request
            String symbol = "KOTAKBANK-EQ";
            TradeDirection direction = TradeDirection.LONG;
            int quantity = 8;
            BigDecimal stopPrice = new BigDecimal("1700.00");
            BigDecimal limitPrice = new BigDecimal("1690.00");

            // When
            Order order = orderManager.createStopLimitOrder(symbol, direction, quantity, stopPrice, limitPrice);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getType()).isEqualTo(OrderType.STOP_LIMIT);
            assertThat(order.getStopPrice()).isEqualTo(stopPrice);
            assertThat(order.getLimitPrice()).isEqualTo(limitPrice);
            assertThat(order.getPrice()).isEqualTo(limitPrice);
        }

        @Test
        void createOrderFromSignal_buy() {
            // Given: A BUY signal with stop loss and target
            String symbol = "RELIANCE-EQ";
            String signalType = "BUY";
            int quantity = 10;
            BigDecimal entryPrice = new BigDecimal("2500.00");
            BigDecimal stopLoss = new BigDecimal("2400.00");
            BigDecimal target = new BigDecimal("2700.00");

            // When
            Order order = orderManager.createOrderFromSignal(symbol, signalType, quantity, entryPrice, stopLoss, target);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getDirection()).isEqualTo(TradeDirection.LONG);
            assertThat(order.getType()).isEqualTo(OrderType.MARKET);
            assertThat(order.getSymbol()).isEqualTo(symbol);
            assertThat(order.getAdditionalProperties()).containsEntry("signalType", signalType);
            assertThat(order.getAdditionalProperties()).containsEntry("stopLoss", "2400.00");
            assertThat(order.getAdditionalProperties()).containsEntry("target", "2700.00");
        }

        @Test
        void createOrderFromSignal_sell() {
            // Given: A SELL signal
            String symbol = "TCS-EQ";
            String signalType = "SELL";
            int quantity = 5;
            BigDecimal entryPrice = new BigDecimal("3500.00");
            BigDecimal stopLoss = new BigDecimal("3600.00");
            BigDecimal target = new BigDecimal("3300.00");

            // When
            Order order = orderManager.createOrderFromSignal(symbol, signalType, quantity, entryPrice, stopLoss, target);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getDirection()).isEqualTo(TradeDirection.SHORT);
            assertThat(order.getAdditionalProperties()).containsEntry("signalType", signalType);
        }

        @Test
        void createOrderFromSignal_nullStopLossAndTarget() {
            // Given: A BUY signal with null stop loss and target
            String symbol = "INFY-EQ";
            String signalType = "BUY";
            int quantity = 10;
            BigDecimal entryPrice = new BigDecimal("1350.00");

            // When
            Order order = orderManager.createOrderFromSignal(symbol, signalType, quantity, entryPrice, null, null);

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getAdditionalProperties()).containsEntry("stopLoss", "");
            assertThat(order.getAdditionalProperties()).containsEntry("target", "");
        }
    }

    // ==================== Order Validation ====================

    @Nested
    class OrderValidation {

        @Test
        void createMarketOrder_nullSymbol_throwsException() {
            // Given: A null symbol
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder(null, TradeDirection.LONG, 10, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Symbol cannot be empty");
        }

        @Test
        void createMarketOrder_emptySymbol_throwsException() {
            // Given: An empty symbol
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder("   ", TradeDirection.LONG, 10, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Symbol cannot be empty");
        }

        @Test
        void createMarketOrder_nullDirection_throwsException() {
            // Given: A null direction
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder("RELIANCE-EQ", null, 10, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Trade direction is required");
        }

        @Test
        void createMarketOrder_nullQuantity_throwsException() {
            // Given: A null quantity
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, null, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than 0");
        }

        @Test
        void createMarketOrder_zeroQuantity_throwsException() {
            // Given: A zero quantity
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 0, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than 0");
        }

        @Test
        void createMarketOrder_negativeQuantity_throwsException() {
            // Given: A negative quantity
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, -5, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than 0");
        }

        @Test
        void createMarketOrder_nullPrice_throwsException() {
            // Given: A null price for a market order
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Market orders require a valid price");
        }

        @Test
        void createMarketOrder_zeroPrice_throwsException() {
            // Given: A zero price for a market order
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Market orders require a valid price");
        }

        @Test
        void createMarketOrder_negativePrice_throwsException() {
            // Given: A negative price for a market order
            // When / Then
            assertThatThrownBy(() -> orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("-100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Market orders require a valid price");
        }

        @Test
        void createLimitOrder_nullLimitPrice_throwsException() {
            // Given: A null limit price
            // When / Then
            assertThatThrownBy(() -> orderManager.createLimitOrder("RELIANCE-EQ", TradeDirection.LONG, 10, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Limit orders require a valid limit price");
        }

        @Test
        void createLimitOrder_zeroLimitPrice_throwsException() {
            // Given: A zero limit price
            // When / Then
            assertThatThrownBy(() -> orderManager.createLimitOrder("RELIANCE-EQ", TradeDirection.LONG, 10, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Limit orders require a valid limit price");
        }

        @Test
        void createStopLossOrder_nullStopPrice_throwsException() {
            // Given: A null stop price
            // When / Then
            assertThatThrownBy(() -> orderManager.createStopLossOrder("RELIANCE-EQ", TradeDirection.LONG, 10, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stop orders require a valid stop price");
        }

        @Test
        void createStopLossOrder_zeroStopPrice_throwsException() {
            // Given: A zero stop price
            // When / Then
            assertThatThrownBy(() -> orderManager.createStopLossOrder("RELIANCE-EQ", TradeDirection.LONG, 10, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stop orders require a valid stop price");
        }

        @Test
        void createTakeProfitOrder_nullStopPrice_throwsException() {
            // Given: A null stop price for take profit
            // When / Then
            assertThatThrownBy(() -> orderManager.createTakeProfitOrder("RELIANCE-EQ", TradeDirection.LONG, 10, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stop orders require a valid stop price");
        }

        @Test
        void createStopOrder_nullStopPrice_throwsException() {
            // Given: A null stop price for stop order
            // When / Then
            assertThatThrownBy(() -> orderManager.createStopOrder("RELIANCE-EQ", TradeDirection.LONG, 10, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stop orders require a valid stop price");
        }

        @Test
        void createStopLimitOrder_nullStopPrice_throwsException() {
            // Given: A null stop price for stop-limit
            // When / Then
            assertThatThrownBy(() -> orderManager.createStopLimitOrder("RELIANCE-EQ", TradeDirection.LONG, 10, null, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stop orders require a valid stop price");
        }
    }

    // ==================== Order Execution ====================

    @Nested
    class OrderExecution {

        @Test
        void executeOrder_pendingToFilled() {
            // Given: A pending market order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            String orderId = order.getOrderId();

            // When
            Order executed = orderManager.executeOrder(orderId, new BigDecimal("2500.00"));

            // Then
            assertThat(executed).isNotNull();
            assertThat(executed.getOrderId()).isEqualTo(orderId);
            assertThat(executed.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(executed.getPrice()).isEqualTo(new BigDecimal("2500.00"));
            assertThat(executed.getExecutionTime()).isNotNull();
        }

        @Test
        void executeOrder_customPrice() {
            // Given: A pending limit order
            Order order = orderManager.createLimitOrder("TCS-EQ", TradeDirection.LONG, 5, new BigDecimal("3500.00"));
            String orderId = order.getOrderId();
            BigDecimal execPrice = new BigDecimal("3495.00");

            // When
            Order executed = orderManager.executeOrder(orderId, execPrice);

            // Then
            assertThat(executed).isNotNull();
            assertThat(executed.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(executed.getPrice()).isEqualTo(execPrice);
        }

        @Test
        void executeOrder_notFound_throwsException() {
            // Given: A non-existent order ID
            String nonExistentId = "ORD_NONEXIST";

            // When / Then
            assertThatThrownBy(() -> orderManager.executeOrder(nonExistentId, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Order not found: " + nonExistentId);
        }

        @Test
        void executeOrder_alreadyFilled_throwsException() {
            // Given: An already filled order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.executeOrder(order.getOrderId(), new BigDecimal("2500.00"));

            // When / Then
            assertThatThrownBy(() -> orderManager.executeOrder(order.getOrderId(), new BigDecimal("2600.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Order cannot be executed - status: FILLED");
        }

        @Test
        void executeOrder_cancelled_throwsException() {
            // Given: A cancelled order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.cancelOrder(order.getOrderId());

            // When / Then
            assertThatThrownBy(() -> orderManager.executeOrder(order.getOrderId(), new BigDecimal("2500.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Order cannot be executed - status: CANCELLED");
        }
    }

    // ==================== Cancellation ====================

    @Nested
    class Cancellation {

        @Test
        void cancelOrder_pending() {
            // Given: A pending order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            String orderId = order.getOrderId();

            // When
            boolean result = orderManager.cancelOrder(orderId);

            // Then
            assertThat(result).isTrue();
            assertThat(orderManager.getOrder(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void cancelOrder_accepted() {
            // Given: An accepted order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            order.setStatus(OrderStatus.ACCEPTED);

            // When
            boolean result = orderManager.cancelOrder(order.getOrderId());

            // Then
            assertThat(result).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void cancelOrder_filled_rejected() {
            // Given: A filled order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.executeOrder(order.getOrderId(), new BigDecimal("2500.00"));

            // When
            boolean result = orderManager.cancelOrder(order.getOrderId());

            // Then
            assertThat(result).isFalse();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        void cancelOrder_alreadyCancelled() {
            // Given: An already cancelled order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.cancelOrder(order.getOrderId());

            // When
            boolean result = orderManager.cancelOrder(order.getOrderId());

            // Then
            assertThat(result).isFalse();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void cancelOrder_notFound() {
            // Given: A non-existent order ID
            String nonExistentId = "ORD_NONEXIST";

            // When
            boolean result = orderManager.cancelOrder(nonExistentId);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== Queries ====================

    @Nested
    class Queries {

        @Test
        void getOrder_found() {
            // Given: Orders in the manager
            Order order1 = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            Order order2 = orderManager.createMarketOrder("TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("3500.00"));

            // When
            Order found = orderManager.getOrder(order1.getOrderId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getOrderId()).isEqualTo(order1.getOrderId());
            assertThat(found.getSymbol()).isEqualTo("RELIANCE-EQ");
        }

        @Test
        void getOrder_notFound() {
            // Given: No orders
            // When
            Order found = orderManager.getOrder("ORD_NONEXIST");

            // Then
            assertThat(found).isNull();
        }

        @Test
        void getPendingOrders() {
            // Given: Mix of pending and filled orders
            Order pending1 = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            Order pending2 = orderManager.createMarketOrder("TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("3500.00"));
            Order filled = orderManager.createMarketOrder("INFY-EQ", TradeDirection.LONG, 20, new BigDecimal("1350.00"));
            orderManager.executeOrder(filled.getOrderId(), new BigDecimal("1350.00"));

            // When
            List<Order> pendingOrders = orderManager.getPendingOrders();

            // Then
            assertThat(pendingOrders).hasSize(2);
            assertThat(pendingOrders).contains(pending1, pending2);
        }

        @Test
        void getOrdersBySymbol() {
            // Given: Multiple orders for the same symbol
            orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.createLimitOrder("RELIANCE-EQ", TradeDirection.SHORT, 5, new BigDecimal("2600.00"));
            orderManager.createMarketOrder("TCS-EQ", TradeDirection.LONG, 5, new BigDecimal("3500.00"));

            // When
            List<Order> relianceOrders = orderManager.getOrdersBySymbol("RELIANCE-EQ");

            // Then
            assertThat(relianceOrders).hasSize(2);
            assertThat(relianceOrders).allMatch(o -> o.getSymbol().equals("RELIANCE-EQ"));
        }

        @Test
        void getOrdersBySymbolAndStatus() {
            // Given: Orders with different statuses
            Order pending = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            Order filled = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.SHORT, 5, new BigDecimal("2550.00"));
            orderManager.executeOrder(filled.getOrderId(), new BigDecimal("2550.00"));

            // When
            List<Order> pendingOrders = orderManager.getOrdersBySymbolAndStatus("RELIANCE-EQ", OrderStatus.PENDING);

            // Then
            assertThat(pendingOrders).hasSize(1);
            assertThat(pendingOrders.get(0).getOrderId()).isEqualTo(pending.getOrderId());
        }

        @Test
        void getOrdersByType() {
            // Given: Orders of different types
            orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.createLimitOrder("RELIANCE-EQ", TradeDirection.SHORT, 5, new BigDecimal("2600.00"));
            orderManager.createStopLossOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2400.00"));
            orderManager.createStopOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2450.00"));

            // When
            List<Order> marketOrders = orderManager.getOrdersByType(OrderType.MARKET);
            List<Order> limitOrders = orderManager.getOrdersByType(OrderType.LIMIT);
            List<Order> stopOrders = orderManager.getOrdersByType(OrderType.STOP);

            // Then
            assertThat(marketOrders).hasSize(1);
            assertThat(limitOrders).hasSize(1);
            assertThat(stopOrders).hasSize(1);
        }

        @Test
        void getTotalOrderCount() {
            // Given: Multiple orders
            orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.createMarketOrder("TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("3500.00"));
            orderManager.createLimitOrder("INFY-EQ", TradeDirection.LONG, 20, new BigDecimal("1350.00"));

            // When
            int count = orderManager.getTotalOrderCount();

            // Then
            assertThat(count).isEqualTo(3);
        }

        @Test
        void getPendingOrderCount() {
            // Given: Mix of pending and filled orders
            orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            Order filled = orderManager.createMarketOrder("TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("3500.00"));
            orderManager.executeOrder(filled.getOrderId(), new BigDecimal("3500.00"));

            // When
            int count = orderManager.getPendingOrderCount();

            // Then
            assertThat(count).isEqualTo(1);
        }
    }

    // ==================== Execution Validation ====================

    @Nested
    class ExecutionValidation {

        @Test
        void validateOrderForExecution_marketAlwaysExecutes() {
            // Given: A pending market order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("2600.00"), 2);

            // Then
            assertThat(canExecute).isTrue();
        }

        @Test
        void validateOrderForExecution_limitLong_priceBelowLimit() {
            // Given: A long limit order with limit price 100, current price 95
            Order order = orderManager.createLimitOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("95.00"), 2);

            // Then
            assertThat(canExecute).isTrue();
        }

        @Test
        void validateOrderForExecution_limitLong_priceAboveLimit() {
            // Given: A long limit order with limit price 100, current price 105
            Order order = orderManager.createLimitOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("105.00"), 2);

            // Then
            assertThat(canExecute).isFalse();
        }

        @Test
        void validateOrderForExecution_limitShort_priceAboveLimit() {
            // Given: A short limit order with limit price 100, current price 105
            Order order = orderManager.createLimitOrder("RELIANCE-EQ", TradeDirection.SHORT, 10, new BigDecimal("100.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("105.00"), 2);

            // Then
            assertThat(canExecute).isTrue();
        }

        @Test
        void validateOrderForExecution_limitShort_priceBelowLimit() {
            // Given: A short limit order with limit price 100, current price 95
            Order order = orderManager.createLimitOrder("RELIANCE-EQ", TradeDirection.SHORT, 10, new BigDecimal("100.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("95.00"), 2);

            // Then
            assertThat(canExecute).isFalse();
        }

        @Test
        void validateOrderForExecution_stopLong_triggered() {
            // Given: A long stop order with stop price 90, current price 88
            Order order = orderManager.createStopOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("90.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("88.00"), 2);

            // Then
            assertThat(canExecute).isTrue();
        }

        @Test
        void validateOrderForExecution_stopLong_notTriggered() {
            // Given: A long stop order with stop price 90, current price 95
            Order order = orderManager.createStopOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("90.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("95.00"), 2);

            // Then
            assertThat(canExecute).isFalse();
        }

        @Test
        void validateOrderForExecution_stopShort_triggered() {
            // Given: A short stop order with stop price 110, current price 112
            Order order = orderManager.createStopOrder("RELIANCE-EQ", TradeDirection.SHORT, 10, new BigDecimal("110.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("112.00"), 2);

            // Then
            assertThat(canExecute).isTrue();
        }

        @Test
        void validateOrderForExecution_stopShort_notTriggered() {
            // Given: A short stop order with stop price 110, current price 105
            Order order = orderManager.createStopOrder("RELIANCE-EQ", TradeDirection.SHORT, 10, new BigDecimal("110.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("105.00"), 2);

            // Then
            assertThat(canExecute).isFalse();
        }

        @Test
        void validateOrderForExecution_stopLimit_long_triggered() {
            // Given: A long stop-limit with stop 90, limit 89, current price 88
            Order order = orderManager.createStopLimitOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("90.00"), new BigDecimal("89.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("88.00"), 2);

            // Then
            assertThat(canExecute).isTrue();
        }

        @Test
        void validateOrderForExecution_stopLimit_long_notTriggered() {
            // Given: A long stop-limit with stop 90, limit 89, current price 95
            Order order = orderManager.createStopLimitOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("90.00"), new BigDecimal("89.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("95.00"), 2);

            // Then
            assertThat(canExecute).isFalse();
        }

        @Test
        void validateOrderForExecution_nonPending_returnsFalse() {
            // Given: A filled order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.executeOrder(order.getOrderId(), new BigDecimal("2500.00"));

            // When
            boolean canExecute = orderManager.validateOrderForExecution(order, new BigDecimal("2600.00"), 2);

            // Then
            assertThat(canExecute).isFalse();
        }

        @Test
        void validateOrderForExecution_nullOrder_returnsFalse() {
            // When
            boolean canExecute = orderManager.validateOrderForExecution(null, new BigDecimal("100"), 2);

            // Then
            assertThat(canExecute).isFalse();
        }
    }

    // ==================== Commission ====================

    @Nested
    class Commission {

        @Test
        void calculateCommission_normal() {
            // Given: An order with quantity 10 and commission rate 0.05 per share
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));

            // When
            BigDecimal commission = orderManager.calculateCommission(order, new BigDecimal("0.05"));

            // Then
            assertThat(commission).isEqualTo(new BigDecimal("0.50"));
        }

        @Test
        void calculateCommission_nullOrder_returnsZero() {
            // When
            BigDecimal commission = orderManager.calculateCommission(null, new BigDecimal("0.05"));

            // Then
            assertThat(commission).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void calculateCommission_nullRate_returnsZero() {
            // Given: A valid order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));

            // When
            BigDecimal commission = orderManager.calculateCommission(order, null);

            // Then
            assertThat(commission).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void calculateCommission_zeroRate_returnsZero() {
            // Given: A valid order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));

            // When
            BigDecimal commission = orderManager.calculateCommission(order, BigDecimal.ZERO);

            // Then
            assertThat(commission).isEqualTo(BigDecimal.ZERO);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void clearAllOrders() {
            // Given: Multiple orders
            orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.createMarketOrder("TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("3500.00"));
            assertThat(orderManager.getTotalOrderCount()).isEqualTo(2);

            // When
            orderManager.clearAllOrders();

            // Then
            assertThat(orderManager.getTotalOrderCount()).isEqualTo(0);
            assertThat(orderManager.getPendingOrderCount()).isEqualTo(0);
        }

        @Test
        void orderDefaults() {
            // Given: A newly created order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getCommission()).isEqualTo(BigDecimal.ZERO);
            assertThat(order.getTimestamp()).isNotNull();
            assertThat(order.getExecutionTime()).isNull();
        }

        @Test
        void idempotentCancel() {
            // Given: A pending order
            Order order = orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            String orderId = order.getOrderId();

            // When: Cancel twice
            boolean first = orderManager.cancelOrder(orderId);
            boolean second = orderManager.cancelOrder(orderId);

            // Then
            assertThat(first).isTrue();
            assertThat(second).isFalse();
            assertThat(orderManager.getOrder(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void getDefaultMaxSlippagePercent() {
            // When
            int slippage = OrderManager.getDefaultMaxSlippagePercent();

            // Then
            assertThat(slippage).isEqualTo(2);
        }

        @Test
        void getMinOrderSize() {
            // When
            BigDecimal minSize = OrderManager.getMinOrderSize();

            // Then
            assertThat(minSize).isEqualTo(BigDecimal.ONE);
        }

        @Test
        void ordersAreUnmodifiableList() {
            // Given: Multiple orders
            orderManager.createMarketOrder("RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("2500.00"));
            orderManager.createMarketOrder("TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("3500.00"));

            // When / Then: getOrders returns the internal map
            assertThat(orderManager.getOrders()).isNotNull();
            assertThat(orderManager.getOrders()).hasSize(2);
        }
    }
}
