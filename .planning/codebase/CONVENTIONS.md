# Coding Conventions

**Analysis Date:** 2026-03-07

## Project Overview

This is a Java 21 Swing Trading System built with Spring Boot 3.x and Maven multi-module architecture. The codebase follows consistent conventions across its 6 modules: `core`, `data`, `strategy`, `llm`, `broker`, and `api`.

## Naming Patterns

### Files
- **Source files:** Use the class name with `.java` extension
- **Package structure:** Lowercase domain names with subdomains (`com.swingtrade.module`)
- **Test files:** Match source file names with `Test` suffix (e.g., `SignalServiceTest.java`)
- **Implementation files:** Use `Default*` prefix for default implementations in `impl` subpackages

**Examples:**
- `core/src/main/java/com/swingtrade/domain/Stock.java`
- `api/src/test/java/com/swingtrade/api/SignalServiceTest.java`
- `strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategy.java`

### Classes and Interfaces
- **Class names:** PascalCase (e.g., `SignalService`, `PaperTradeEngine`, `DataIngestionService`)
- **Interface names:** Descriptive without prefixes/suffixes (e.g., `IndicatorService`, `BrokerService`, `Strategy`)
- **Implementation classes:** Use `Default*` prefix for main implementations in `impl` subpackage
- **DTOs:** Explicitly named as `*Request`, `*Response`, `*Dto` (e.g., `TradeRequest`, `PerformanceResponse`)

**Examples:**
```java
@Service
public class SignalService { ... }

@Service
public class PaperTradingServiceImpl implements BrokerService { ... }

public record Stock(String symbol, Exchange exchange, String name, ...) { ... }
```

### Functions and Methods
- **Method names:** camelCase, verb-first for actions (e.g., `getLatestSignals()`, `placeOrder()`, `calculateProfitLoss()`)
- **Getter/Setter methods:** Standard JavaBean conventions (`get*()`, `set*()`)
- **Boolean methods:** Use `is*()` prefix for boolean checks (e.g., `isBullish()`, `isBuySignal()`)
- **Private helpers:** Use descriptive names with `calculate*()`, `validate*()`, `parse*()` prefixes

**Examples:**
```java
public BigDecimal calculateProfitLoss(Position position) { ... }
public boolean validateOrderConstraints(Order order) { ... }
private SentimentType parseSentimentFromResponse(String response) { ... }
```

### Variables
- **Instance variables:** camelCase, descriptive names (e.g., `activePositions`, `paperTradeEngine`, `initialCapital`)
- **Constants:** UPPER_SNAKE_CASE with clear meaning (e.g., `MAX_CONCURRENT_POSITIONS`, `DEFAULT_EMA_PERIOD`)
- **Loop variables:** Single lowercase letters for simple loops (e.g., `for (var candle : candles)`), descriptive for complex cases

**Examples:**
```java
private static final int MAX_CONCURRENT_POSITIONS = 5;
private final Map<String, Position> activePositions;
```

### Enums
- **Enum names:** PascalCase representing a category (e.g., `Exchange`, `Sector`, `TradeDirection`, `OrderStatus`)
- **Enum constants:** UPPER_SNAKE_CASE (e.g., `NSE`, `BSE`, `BUY`, `SELL`, `HOLD`)

**Examples:**
```java
public enum Exchange {
    NSE("National Stock Exchange of India"),
    BSE("Bombay Stock Exchange");
}

public enum TradeDirection {
    LONG,
    SHORT
}
```

## Code Style

### Formatting
- **Indentation:** 4 spaces (standard Java convention)
- **Line length:** No strict limit, but aim for ~120 characters
- **Braces:** K&R style - opening brace on same line
- **Blank lines:** One blank line between methods, two blank lines between logical sections

### Annotation Placement
- Spring annotations (`@Service`, `@Component`, `@Repository`) on class declaration
- `@Override` on method declaration
- Method-level annotations follow the method signature

**Example:**
```java
@Service
public class SignalService {

    @Override
    public List<Signal> getLatestSignals() {
        // ...
    }
}
```

### Imports
- Standard Java imports first
- Spring framework imports
- Third-party library imports
- Project imports (other packages in `com.swingtrade`)

### Comments and Documentation

**Javadoc Comments:**
- Required for public classes, methods, and fields
- Include `@param` for each parameter
- Include `@return` for methods with return values
- Include `@throws` for documented exceptions
- Use `/** */` format for Javadoc

**Example:**
```java
/**
 * Calculates the current profit and loss for a position.
 *
 * @param position the position to calculate P&L for
 * @return the calculated profit and loss
 * @throws IllegalArgumentException if position is null
 */
public BigDecimal calculateProfitLoss(Position position) {
    if (position == null) {
        throw new IllegalArgumentException("Position cannot be null");
    }
    // ...
}
```

**Inline Comments:**
- Use `//` for simple explanations
- Use `/* */` for longer inline documentation
- Explain "why" not "what" when the code isn't self-explanatory

**Example:**
```java
// Skip if candle already exists
if (candleRepository.existsBySymbolAndDate(symbol, date)) {
    logger.debug("Candle already exists for {}: {}", symbol, date);
    continue;
}
```

## Function Design

### Size and Complexity
- Methods should be focused and do one thing
- Target 20-50 lines per method
- Break complex methods into smaller helper methods
- Use early returns to avoid deep nesting

**Example:**
```java
public Order placeOrder(Order order) {
    if (order == null) {
        throw new IllegalArgumentException("Order cannot be null");
    }

    if (!validateOrderConstraints(order)) {
        throw new IllegalStateException("Order validation failed");
    }

    order.setStatus(OrderStatus.ACCEPTED);
    activeOrders.put(order.getOrderId(), order);

    return order;
}
```

### Parameters
- Limit parameters to 4-5 maximum
- Use objects for related parameters (e.g., `Order` instead of individual price/quantity parameters)
- Use optional parameters or builder pattern when many configuration options exist

### Return Values
- Return `Optional` for methods that may not find a result (e.g., `Optional<Position>`)
- Return empty collections instead of null
- Never return null primitives

**Example:**
```java
public Optional<Position> getPosition(String positionId) {
    Position position = activePositions.get(positionId);
    return Optional.ofNullable(position);
}

public List<Position> getOpenPositions() {
    return new ArrayList<>(activePositions.values());
}
```

## Module Organization

### Package Structure
Each module follows this structure:
```
src/main/java/com/swingtrade/module/
├── config/          # Configuration classes
├── controller/      # REST controllers (api module)
├── dto/             # Data transfer objects (api module)
├── entity/          # JPA entities (data module)
├── model/           # Domain models (broker module)
├── repository/      # Spring Data repositories (data module)
├── service/         # Business logic services
├── impl/            # Implementation classes
├── engine/          # Core engine components
├── client/          # External API clients
└── config/          # Module configuration
```

### Dependency Direction
- `core` has no dependencies on other modules (foundation layer)
- `data`, `strategy`, `llm` depend on `core`
- `broker` depends on `core`, `data`, `strategy`
- `api` depends on all other modules (presentation layer)

## Error Handling

### Exception Strategy
- Use unchecked exceptions (`RuntimeException`) for programming errors
- Validate input parameters at method boundaries
- Throw descriptive exceptions with clear messages

**Example:**
```java
public Order placeOrder(Order order) {
    if (order == null) {
        throw new IllegalArgumentException("Order cannot be null");
    }

    if (!validateOrderConstraints(order)) {
        throw new IllegalStateException(
            "Order validation failed - maximum positions exceeded"
        );
    }
}
```

### Logging
- Use SLF4J for all logging
- Class-level logger: `private static final Logger logger = LoggerFactory.getLogger(Class.class);`
- Log levels: `trace` for detailed debugging, `debug` for diagnostic info, `info` for significant events, `warn` for recoverable issues, `error` for failures

**Example:**
```java
private static final Logger logger = LoggerFactory.getLogger(DataIngestionService.class);

logger.info("Starting scheduled data ingestion at {}", LocalDateTime.now());
logger.debug("Candle already exists for {}: {}", symbol, date);
logger.error("Error processing data for stock {}: {}", stock, e.getMessage());
```

### Error Response Format
API errors use structured `ErrorResponse` DTO:
```java
public class ErrorResponse {
    private LocalDateTime timestamp;
    private Integer status;
    private String error;
    private String message;
    private String path;
    private List<FieldError> fieldErrors;
}
```

## Module Design

### Service Classes
- Annotated with `@Service`
- Use constructor injection (not field injection)
- Keep stateless when possible
- Transaction boundaries controlled by `@Transactional` annotation

**Example:**
```java
@Service
public class PaperTradingServiceImpl implements BrokerService {

    private final PaperTradeEngine paperTradeEngine;

    public PaperTradingServiceImpl(PaperTradeEngine paperTradeEngine) {
        this.paperTradeEngine = paperTradeEngine;
    }

    @Transactional
    public Order placeOrder(Order order) {
        // ...
    }
}
```

### Domain Models
- Use Java records for immutable value objects (e.g., `Stock`, `OhlcvCandle`, `Signal`)
- Records include descriptive Javadoc with parameter explanations
- Include utility methods and factory methods (`of()`, `create()`)

**Example:**
```java
public record OhlcvCandle(
    String symbol,
    LocalDate date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    Long volume,
    BigDecimal adjClose
) {
    public static OhlcvCandle of(
        String symbol,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
    ) {
        return new OhlcvCandle(symbol, date, open, high, low, close, volume, close);
    }
}
```

### Spring Components
- Controllers annotated with `@RestController`
- Classes with business logic annotated with `@Service` or `@Component`
- Configuration classes use standard Spring annotations
- Use `@Autowired` explicitly or constructor injection

**Example:**
```java
@RestController
@RequestMapping("/api")
public class SwingTradeController {

    @Autowired
    private SignalService signalService;

    @GetMapping("/signals/latest")
    public ResponseEntity<List<Signal>> getLatestSignals() {
        return ResponseEntity.ok(signalService.getLatestSignals());
    }
}
```

### Scheduling
- Use `@Scheduled` with cron expressions for periodic tasks
- Specify timezone with `zone` parameter for IST operations
- Use `@Transactional` for data modification operations

**Example:**
```java
@Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Kolkata")
@Transactional
public void autoIngestData() {
    // Scheduled data ingestion
}
```

## Barrels and Exports

### No Barrel Files
- No explicit `index.java` or barrel files
- Each class is imported directly from its package
- Keep package imports organized and specific

### Package Visibility
- Use default (package-private) visibility for classes that don't need to be public
- Keep implementation classes in `impl` subpackages to discourage direct access
- Interface-first design in `core` module

---

*Convention analysis: 2026-03-07*
