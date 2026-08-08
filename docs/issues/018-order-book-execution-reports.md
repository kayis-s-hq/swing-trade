# feat(broker): add order book and execution reports

**Labels:** `enhancement` `tier-3-infra` `broker`
**Estimated effort:** 2-3 days

## Problem

The broker module has an `Order` entity and `OrderManager` but there is no REST endpoint to view the order book or execution reports. Users cannot see order status, fill details, or pending orders.

## Proposed Solution

Add order book and execution report endpoints that expose order lifecycle data from the broker module.

## API Endpoints

```
GET  /api/orders                    - Get all orders (paginated)
GET  /api/orders/{orderId}          - Get order details
GET  /api/orders/symbol/{symbol}    - Get orders for a symbol
GET  /api/orders/status/{status}    - Get orders by status
GET  /api/orders/executions         - Get execution report summary
DELETE /api/orders/{orderId}/cancel  - Cancel a pending order
```

## Response DTOs

```java
public class OrderResponse {
    private String orderId;              // Broker-generated or system-generated ID
    private String symbol;
    private OrderType type;              // BUY, SELL
    private OrderStatus status;          // PENDING, FILLED, PARTIALLY_FILLED, CANCELLED, REJECTED
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal price;
    private BigDecimal avgPrice;
    private BigDecimal fees;
    private String exchange;             // NSE, BSE
    private String exchangeOrderId;      // Broker's order ID
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String errorMessage;         // If rejected
}

public class ExecutionReport {
    private int totalOrders;
    private int filledOrders;
    private int pendingOrders;
    private int rejectedOrders;
    private int cancelledOrders;
    private List<OrderExecution> executions;
    private BigDecimal totalTurnover;
    private BigDecimal totalFees;
}

public class OrderExecution {
    private String orderId;
    private String symbol;
    private BigDecimal filledQuantity;
    private BigDecimal price;
    private BigDecimal fees;
    private LocalDateTime filledAt;
}
```

## Broker Module Changes

### OrderManager enhancements

```java
@Service
public class OrderManager {

    public OrderResponse getOrder(String orderId) { ... }
    public List<OrderResponse> getAllOrders(int page, int size) { ... }
    public List<OrderResponse> getOrdersBySymbol(String symbol) { ... }
    public List<OrderResponse> getOrdersByStatus(OrderStatus status) { ... }
    public OrderResponse cancelOrder(String orderId) { ... }
    public ExecutionReport getExecutionReport() { ... }
}
```

### PaperTradeEngine integration

When an order is filled in `PaperTradeEngine`, update the order status and record:
- Filled quantity
- Average fill price
- Fill timestamp
- Fees

```java
private void fillOrder(Order order, BigDecimal fillPrice, BigDecimal fillQuantity) {
    order.setStatus(OrderStatus.FILLED);
    order.setFilledQuantity(fillQuantity);
    order.setAvgPrice(fillPrice);
    order.setUpdatedAt(LocalDateTime.now());
    orderRepository.save(order);
}
```

### KiteConnectClient integration

When Kite returns an execution update, map it to the internal order format:

```java
private OrderResponse mapToOrderResponse(KiteOrder kiteOrder) {
    return OrderResponse.builder()
        .orderId(kiteOrder.getOrderId())
        .exchangeOrderId(kiteOrder.getExchangeOrderId())
        .status(mapOrderStatus(kiteOrder.getOrderStatus()))
        .filledQuantity(BigDecimal.valueOf(kiteOrder.getFilledQuantity()))
        .avgPrice(BigDecimal.valueOf(kiteOrder.getAveragePrice()))
        .build();
}
```

## Files to Create

- `api/src/main/java/com/swingtrade/api/dto/OrderResponse.java`
- `api/src/main/java/com/swingtrade/api/dto/ExecutionReport.java`
- `api/src/main/java/com/swingtrade/api/dto/OrderExecution.java`
- `api/src/main/java/com/swingtrade/api/controller/OrderBookController.java`
- `data/src/main/java/com/swingtrade/data/entity/OrderEntity.java` (if not exists)
- `data/src/main/java/com/swingtrade/data/repository/OrderRepository.java` (if not exists)

## Files to Modify

- `broker/src/main/java/com/swingtrade/broker/manager/OrderManager.java` - Add query methods
- `broker/src/main/java/com/swingtrade/broker/engine/PaperTradeEngine.java` - Update order status on fill
- `broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java` - Map Kite executions
- `api/pom.xml` - Add broker module dependency if not present

## Order Status Flow

```
PENDING → FILLED
PENDING → PARTIALLY_FILLED → FILLED
PENDING → CANCELLED
PENDING → REJECTED
```

## Acceptance Criteria

- [ ] GET /api/orders returns paginated order list
- [ ] GET /api/orders/{orderId} returns single order details
- [ ] GET /api/orders/executions returns summary + recent executions
- [ ] DELETE /api/orders/{orderId}/cancel cancels pending order
- [ ] Order status updates when paper trade fills
- [ ] Kite execution updates mapped correctly
- [ ] Execution report includes turnover and fees totals
- [ ] All endpoints have unit tests
- [ ] Integration test with database
- [ ] Code coverage >= 80%

## Notes

- The existing `Order.java` in broker module may need to be moved or wrapped for API exposure
- Consider adding `@Cacheable` on order list endpoint (5-min TTL)
- Cancel endpoint should validate order is still PENDING before cancelling
- For live trading, respect exchange order ID for cancellation
