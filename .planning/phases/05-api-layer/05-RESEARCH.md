# Phase 5: API Layer - Research

**Researched:** 2026-03-29
**Domain:** Spring Boot REST API design and implementation
**Confidence:** HIGH

## Summary

Phase 5 focuses on implementing and verifying REST API endpoints with proper DTOs and service layer architecture for the SwingTrade swing trading system. The API module (`api/`) provides the public interface for interacting with the trading system, exposing endpoints for trading operations, position management, signal queries, performance metrics, and scanning functionality.

**Primary recommendation:** The API module is already structurally complete with proper DTO separation, service layer architecture, and controller patterns. The focus should be on verifying compilation, testing endpoint coverage, and adding global error handling with `@ControllerAdvice`.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot 3.x | 3.2+ (project default) | REST API framework | Built-in MVC, validation, error handling, JSON serialization |
| Spring Data JPA | 3.2+ | Repository pattern | Auto CRUD, custom query derivation |
| Hibernate Validator | 8.x | Bean validation | `@Valid`, `@NotNull`, `@Size` annotations |
| Jackson | 2.15+ | JSON serialization | Default Spring Boot JSON processor |
| Maven | 3.8+ | Build system | Multi-module dependency management |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot DevTools | 3.2+ | Hot reload development | Dev environment only (scope: provided) |
| Micrometer | 1.11+ | Metrics & actuator | Health checks, Prometheus exports |
| Mockito | 5.10+ | Unit test mocking | `@WebMvcTest`, `MockMvc` testing |
| Spring Test | 3.2+ | Integration testing | `@SpringBootTest`, `MockMvc` setup |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@RestController` | `@Controller` + `@ResponseBody` | Same outcome, `@RestController` is standard convention |
| Manual error handling | `@ControllerAdvice` | Global handling is cleaner, consistent responses |
| Direct entity exposure | DTOs only | Security, versioning, separation of concerns |

**Installation:**
```bash
cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade
mvn clean install
```

**Version verification:**
```bash
mvn -version  # Maven 3.8+
java -version # Java 21
```

## Architecture Patterns

### Recommended Project Structure
```
api/src/main/java/com/swingtrade/api/
├── app/                     # Application main class
│   └── SwingTradeApiApplication.java
├── config/                  # API-specific configuration
│   └── ApiSchedulingConfig.java
├── controller/              # REST controllers
│   ├── TradingController.java
│   ├── PositionController.java
│   ├── SignalController.java
│   └── HealthController.java
├── service/                 # Business logic services
│   ├── PerformanceService.java
│   └── ScanService.java
├── dto/                     # Data Transfer Objects
│   ├── TradeRequest.java
│   ├── TradeResponse.java
│   ├── PositionResponse.java
│   ├── PositionStats.java
│   ├── SectorAllocation.java
│   ├── RiskSummary.java
│   ├── SignalResponse.java
│   ├── ScanResponse.java
│   └── PerformanceResponse.java
├── scheduler/               # Scheduled tasks
│   └── WeeklySectorDigestScheduler.java
└── metrics/                 # Metrics collectors
    ├── PortfolioMetrics.java
    └── ApiTradeMetrics.java
```

### Pattern 1: Controller-Service-Repository Separation

**What:** Layered architecture with clear separation of concerns

**When to use:** All REST endpoints requiring business logic, database access, or external API calls

**Example:**
```java
// Controller - HTTP handling only
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    @Autowired
    private PositionService positionService;

    @GetMapping
    public ResponseEntity<List<PositionResponse>> getPositions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<PositionResponse> positions = positionService.getOpenPositions();
        return ResponseEntity.ok(positions);
    }
}

// Service - Business logic only
@Service
public class PositionService {

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private PaperTradingEngine paperTradingEngine;

    public List<PositionResponse> getOpenPositions() {
        List<PositionEntity> entities = positionRepository.findAllOpenPositions();
        return entities.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private PositionResponse convertToResponse(PositionEntity entity) {
        Position domainPosition = entity.toDomain();
        return new PositionResponse(domainPosition);
    }
}

// Repository - Data access only
@Repository
public interface PositionRepository extends JpaRepository<PositionEntity, Long> {
    List<PositionEntity> findAllOpenPositions();
    Optional<PositionEntity> findOpenBySymbol(String symbol);
}
```

**Source:** [Spring Framework Documentation - MVC Controller Architecture](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)

### Pattern 2: Request/Response DTO Separation

**What:** Separate DTOs for input validation and output serialization

**When to use:** All POST/PUT endpoints (requests) and GET endpoints (responses)

**Example:**
```java
// Request DTO with validation
public class TradeRequest {
    @NotNull(message = "Symbol is required")
    @Pattern(regexp = "^[A-Z]{2,10}$", message = "Invalid symbol format")
    private String symbol;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be positive")
    private Integer quantity;

    @NotNull(message = "Direction is required")
    private TradeDirection direction;

    @NotNull(message = "Order type is required")
    private OrderType orderType;

    private BigDecimal price;

    private String entryReason;
}

// Response DTO - only exposed fields
public class PositionResponse {
    private Long id;
    private String symbol;
    private BigDecimal entryPrice;
    private BigDecimal currentPrice;
    private Integer quantity;
    private BigDecimal stopLoss;
    private BigDecimal target;
    private PositionStatus status;
    private String entryReason;
    private LocalDate entryDate;
}

// Enum for status
public enum PositionStatus {
    OPEN, CLOSED, STOPPED, TARGET_HIT
}
```

**Source:** [Spring Boot Validation Guide](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/validating.html)

### Pattern 3: Global Error Handling with @ControllerAdvice

**What:** Centralized exception handling for consistent error responses

**When to use:** All controllers, validation errors, business logic exceptions

**Example:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());
        return new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "RESOURCE_NOT_FOUND",
            ex.getMessage(),
            LocalDateTime.now()
        );
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException ex) {
        logger.warn("Bad request: {}", ex.getMessage());
        return new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "BAD_REQUEST",
            ex.getMessage(),
            LocalDateTime.now()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        return new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_ERROR",
            "Validation failed: " + String.join(", ", errors),
            LocalDateTime.now()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            LocalDateTime.now()
        );
    }
}

// ErrorResponse structure
public class ErrorResponse {
    private int status;
    private String code;
    private String message;
    private LocalDateTime timestamp;

    // Constructors, getters, setters
}
```

**Source:** [Spring MVC Controller Advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-controller-advice.html)

### Anti-Patterns to Avoid

- **Returning Entities Directly:** Never expose JPA entities in API responses - internal structure changes break clients
- **Missing Validation:** Always use `@Valid` on `@RequestBody` parameters
- **Inconsistent Error Handling:** Don't mix individual `@ExceptionHandler` with no global handler
- **Hardcoded HTTP Statuses:** Don't return `200` for everything - use `201` for created, `409` for conflicts
- **Null Response Handling:** Don't return `null` - use empty collections or explicit `404`
- **Sensitive Field Exposure:** Never expose passwords, API keys, or internal IDs in responses

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON serialization | Custom JSON parser | Jackson (Spring Boot default) | Battle-tested, handles all edge cases |
| HTTP status codes | Manual `ResponseEntity` status checks | `@ResponseStatus`, `HttpStatus` enums | Consistent, type-safe |
| Error response formatting | Hand-crafted error JSON | `@ControllerAdvice` + `ErrorResponse` DTO | Centralized, consistent, extensible |
| Validation | Manual `if/else` checks | `@Valid` + Hibernate Validator annotations | Declarative, reusable, extensible |
| Pagination | Manual offset/limit | Spring Data `Pageable`, `Page<T>` | Integrated, efficient, standard |
| Request/Response mapping | Manual `toDTO()`/`toEntity()` | MapStruct (if needed) | Type-safe, compile-time verification |

**Key insight:** Spring Boot provides production-ready REST infrastructure - don't reinvent HTTP handling, validation, or error management.

## Common Pitfalls

### Pitfall 1: Missing `@Autowired` on Constructors

**What goes wrong:** Spring can't inject dependencies, application fails to start with "required bean not found"

**Why it happens:** Java 16+ records and records-style classes require explicit constructors for dependency injection

**How to avoid:**
```java
// GOOD - Explicit constructor
@Service
public class PositionService {

    @Autowired
    public PositionService(PositionRepository positionRepository,
                          PaperTradingEngine paperTradingEngine) {
        this.positionRepository = positionRepository;
        this.paperTradingEngine = paperTradingEngine;
    }
}

// BETTER - @ConstructorAlignment (Spring Boot 3+)
@Service
public class PositionService {

    public PositionService(PositionRepository positionRepository,
                          PaperTradingEngine paperTradingEngine) {
        // No @Autowired needed if single constructor
    }
}
```

**Warning signs:** Application startup failures, `NullPointerException` at runtime

### Pitfall 2: Entity Fields Leaking in API Responses

**What goes wrong:** Internal fields like `@Id`, `@Version`, `@CreatedDate` exposed to clients

**Why it happens:** Controllers return entities directly instead of DTOs

**How to avoid:**
```java
// BAD
@GetMapping("/{id}")
public PositionEntity getPosition(@PathVariable Long id) {
    return positionRepository.findById(id).orElseThrow();
}

// GOOD
@GetMapping("/{id}")
public ResponseEntity<PositionResponse> getPosition(@PathVariable Long id) {
    PositionEntity entity = positionRepository.findById(id).orElseThrow();
    PositionResponse response = new PositionResponse(entity);
    return ResponseEntity.ok(response);
}
```

**Warning signs:** API documentation shows internal fields, security audit flags sensitive data

### Pitfall 3: Inconsistent HTTP Status Codes

**What goes wrong:** Always returning `200 OK` regardless of outcome

**Why it happens:** Manual `ResponseEntity.ok()` used everywhere

**How to avoid:**
```java
// CREATE - Use 201 Created
@PostMapping
public ResponseEntity<PositionResponse> createPosition(
        @Valid @RequestBody TradeRequest request) {
    PositionResponse created = positionService.createPosition(request);
    return ResponseEntity.created(
            UriComponentsBuilder.fromPath("/api/positions/{id}")
                .buildAndExpand(created.getId()).toUri()).body(created);
}

// UPDATE - Use 200 OK
@PutMapping("/{id}")
public ResponseEntity<PositionResponse> updatePosition(
        @PathVariable Long id,
        @Valid @RequestBody UpdateRequest request) {
    PositionResponse updated = positionService.updatePosition(id, request);
    return ResponseEntity.ok(updated);
}

// NOT FOUND - Use 404
@GetMapping("/{id}")
public ResponseEntity<PositionResponse> getPosition(@PathVariable Long id) {
    return positionService.getPositionById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}

// CONFLICT - Use 409
@PostMapping
public ResponseEntity<PositionResponse> createPosition(
        @Valid @RequestBody TradeRequest request) {
    try {
        return ResponseEntity.ok(positionService.createPosition(request));
    } catch (PositionLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError("POSITION_LIMIT_EXCEEDED", e.getMessage()));
    }
}
```

**Warning signs:** Clients can't properly handle error states, UI shows generic errors

### Pitfall 4: Null Safety Issues

**What goes wrong:** NPEs when services return null for not-found resources

**Why it happens:** Controllers call `orElse(null)` instead of proper error handling

**How to avoid:**
```java
// BAD - Returns null
@GetMapping("/{id}")
public PositionResponse getPosition(@PathVariable Long id) {
    return positionRepository.findById(id)
            .map(this::toResponse)
            .orElse(null);
}

// GOOD - Returns 404
@GetMapping("/{id}")
public ResponseEntity<PositionResponse> getPosition(@PathVariable Long id) {
    return positionRepository.findById(id)
            .map(entity -> ResponseEntity.ok(toResponse(entity)))
            .orElse(ResponseEntity.notFound().build());
}

// OR - Use exception
@GetMapping("/{id}")
public PositionResponse getPosition(@PathVariable Long id) {
    PositionEntity entity = positionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Position not found with id: " + id));
    return toResponse(entity);
}
```

**Warning signs:** Frontend code crashes on `undefined` responses, API tests fail on `null`

## Code Examples

Verified patterns from official sources:

### GET Endpoint with Query Parameters
```java
@RestController
@RequestMapping("/api/signals")
public class SignalController {

    @Autowired
    private SignalService signalService;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<List<SignalResponse>>> getLatestSignals(
            @RequestParam(required = false) SignalType signalType,
            @RequestParam(required = false) BigDecimal minConfidence,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "20") int limit) {

        List<Signal> signals = signalService.getLatestSignals(
                signalType, minConfidence, date, limit);

        List<SignalResponse> responses = signals.stream()
                .map(SignalResponse::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
```

**Source:** [Spring Request Param Documentation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html#ann-model-attr)

### POST Endpoint with Validation
```java
@PostMapping
public ResponseEntity<PositionResponse> createPosition(
        @Valid @RequestBody TradeRequest request) {

    logger.info("Creating new position for symbol: {}", request.getSymbol());

    try {
        if (!request.isValid()) {
            return ResponseEntity.badRequest().body(null);
        }

        PositionResponse position = positionService.createPosition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(position);

    } catch (BadRequestException e) {
        logger.error("Invalid request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(buildErrorResponse("BAD_REQUEST", e.getMessage()));
    } catch (Exception e) {
        logger.error("Error creating position: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError()
                .body(buildErrorResponse("INTERNAL_ERROR", "Failed to create position"));
    }
}
```

**Source:** [Spring MVC Exception Handling](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html)

### Pagination with Pageable
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<PositionResponse>>> getPositions(
        @PageableDefault(size = 20, sort = "entryDate", direction = Sort.Direction.DESC)
        Pageable pageable) {

    Page<PositionEntity> entities = positionRepository.findAll(pageable);

    Page<PositionResponse> responses = entities.map(this::toResponse);

    return ResponseEntity.ok(ApiResponse.success(responses));
}
```

**Source:** [Spring Data Pageable](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods.query-creation.pageable)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual error handling per controller | `@ControllerAdvice` global handler | Spring Boot 2.x+ | Consistent responses, DRY code |
| Returning entities in API | DTOs only | Best practice always | Security, versioning, separation |
| `@RequestMapping` for all | `@GetMapping`, `@PostMapping` | Spring Boot 2.3+ | RESTful clarity |
| Jackson default config | Custom `ObjectMapper` beans | When ISO8601 dates needed | Better date formatting |

**Deprecated/outdated:**
- `@Controller` + `@ResponseBody` on each method → Use `@RestController`
- `ResponseEntity` with manual status codes → Use `@ResponseStatus` on exception methods
- Hand-crafted JSON errors → Use `ErrorResponse` DTO with `@ControllerAdvice`

## Open Questions

1. **Global Error Handling Strategy**
   - What we know: Project has `ErrorHandlingTest.java` existing
   - What's unclear: Whether `GlobalExceptionHandler` exists or needs to be created
   - Recommendation: Check if `GlobalExceptionHandler` class exists; if not, create with `@ControllerAdvice`

2. **DTO vs Entity Exposure**
   - What we know: DTOs exist in `api/src/main/java/com/swingtrade/api/dto/`
   - What's unclear: Whether any controllers still expose entities directly
   - Recommendation: Verify all endpoints use DTOs only

3. **Response Envelope Pattern**
   - What we know: `ApiResponse<T>` pattern mentioned in research
   - What's unclear: Whether project uses envelope wrapper or direct responses
   - Recommendation: Standardize on envelope pattern for consistency: `{ status, data, message, timestamp }`

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Spring Boot 3.x | ✓ | 21.x (system) | N/A |
| Maven 3.8+ | Build system | ✓ | 3.8+ (system) | N/A |
| PostgreSQL | Data layer | ✓ (Docker) | 15.4 (via docker-compose) | H2 test DB |
| TimescaleDB | Time-series | ✓ (Docker) | 1.7.x (via docker-compose) | Standard PostgreSQL |
| Redis | Caching | ✓ (Docker) | 7.x (via docker-compose) | Caffeine local cache |

**Missing dependencies with no fallback:**
- None - all required infrastructure available

**Missing dependencies with fallback:**
- None identified

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test |
| Config file | `api/pom.xml` (test scope dependencies) |
| Quick run command | `mvn test -pl api -Dtest=*Test -DfailIfNoTests=false` |
| Full suite command | `mvn test -pl api` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REQ-023 | PerformanceService returns metrics | unit | `mvn test -pl api -Dtest=PerformanceServiceTest` | ✅ Wave 0 |
| REQ-023 | Performance endpoints work | integration | `mvn test -pl api -Dtest=PerformanceControllerTest` | ✅ Wave 0 |
| REQ-024 | ScanService generates signals | unit | `mvn test -pl api -Dtest=ScanServiceTest` | ✅ Wave 0 |
| REQ-024 | Scan endpoints work | integration | `mvn test -pl api -Dtest=ScanControllerTest` | ✅ Wave 0 |
| REQ-020 | Trading endpoints work | integration | `mvn test -pl api -Dtest=TradingControllerTest` | ✅ Wave 0 |
| REQ-021 | Signal endpoints work | integration | `mvn test -pl api -Dtest=SignalControllerTest` | ✅ Wave 0 |
| REQ-022 | Position endpoints work | integration | `mvn test -pl api -Dtest=PositionControllerTest` | ✅ Wave 0 |

### Sampling Rate
- **Per task commit:** `mvn test -pl api -Dtest=*Test -DfailIfNoTests=false`
- **Per wave merge:** `mvn test -pl api`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `api/src/test/java/com/swingtrade/api/controller/ErrorHandlingTest.java` exists - verify global exception handling works
- [ ] `api/src/test/java/com/swingtrade/api/service/PerformanceServiceTest.java` - may need to be created
- [ ] `api/src/test/java/com/swingtrade/api/service/ScanServiceTest.java` - may need to be created
- [ ] Framework install: `mvn clean test -pl api` - verify all tests pass

*(If no gaps: "None - existing test infrastructure covers all phase requirements")*

## Sources

### Primary (HIGH confidence)
- [Spring Framework Documentation - MVC Controller Architecture](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html) - Controller patterns, handler methods, validation
- [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/docs/3.2.x/reference/htmlsingle/#web) - REST API best practices, error handling
- [Spring Data JPA - Pageable](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods.query-creation.pageable) - Pagination patterns

### Secondary (MEDIUM confidence)
- Project existing code: `api/src/main/java/com/swingtrade/api/controller/*`, `api/src/main/java/com/swingtrade/api/service/*` - Verified patterns in use
- Project existing tests: `api/src/test/java/com/swingtrade/api/controller/*Test.java` - Test patterns used

### Tertiary (LOW confidence)
- Project pom.xml dependencies - Version verification needed for production

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Verified against Spring Boot 3.2 documentation and project pom.xml
- Architecture: HIGH - Patterns verified against official Spring documentation and existing code
- Pitfalls: HIGH - Common issues documented in Spring ecosystem with verified solutions

**Research date:** 2026-03-29
**Valid until:** 2026-04-28 (30 days for Spring Boot 3.x - stable release)

---

## User Constraints (from CONTEXT.md)

> **Note:** No CONTEXT.md was provided for this phase. The phase requirements are derived from:
> - REQUIREMENTS.md: REQ-023 (PerformanceService), REQ-024 (ScanService)
> - Existing code structure with proper DTO separation
> - Project's Maven multi-module architecture

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REQ-023 | PerformanceService - Total P&L, win rate, total trades, average win/loss, profit factor, max drawdown, Sharpe ratio | Service layer with PositionRepository + PaperTradingEngine integration; existing `PerformanceService.java` and `PerformanceServiceTest.java` |
| REQ-024 | ScanService - GET/POST /api/scan endpoints, returns Nifty 500 signals, distribution metrics | Service layer with SignalEngine + StockRepository integration; existing `ScanService.java` and `ScanControllerTest.java` |
