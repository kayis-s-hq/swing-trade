# feat(api): add API documentation with OpenAPI/Swagger

**Labels:** `enhancement` `tier-3-infra` `api` `documentation`
**Estimated effort:** 1-2 days

## Problem

There is no machine-readable API documentation. Developers must read controller source code or guess endpoint shapes. No Swagger UI for interactive API exploration.

## Proposed Solution

Add springdoc-openapi to generate OpenAPI 3.0 spec and serve Swagger UI at `/swagger-ui.html`.

## Dependencies

Add to `api/pom.xml`:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```

## Configuration

### OpenApiConfig.java

```java
@Configuration
@EnableOpenApi
@Profile("!test")
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Swing Trade API")
                .version("1.0.0")
                .description("REST API for automated swing trading system with multi-factor technical analysis and LLM sentiment filtering.")
                .contact(new Contact().name("Swing Trade Team").email("kayisrahman@gmail.com"))
                .license(new License().name("Apache 2.0").url("https://spdx.org/licenses/Apache-2.0.html")))
            .servers(List.of(
                new Server().url("http://localhost:8080/api").description("Local server"),
                new Server().url("https://api.swingtrade.com").description("Production")
            ));
    }
}
```

### application.properties

```properties
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.tryItOutEnabled=true
```

## Controller Annotations

Add to each controller:

```java
@RestController
@RequestMapping("/api/positions")
@Tag(name = "Position", description = "Operations related to trading positions")
public class PositionController {

    @Operation(
        summary = "Get all open positions",
        description = "Returns a paginated list of all open trading positions"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated position list",
            content = @Content(schema = @Schema(implementation = PaginatedResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<PaginatedResponse<PositionResponse>> getPositions(...) { ... }
}
```

Add to each DTO:

```java
@Data
public class PositionResponse {
    @Schema(description = "Unique position identifier", example = "1")
    private Long id;

    @Schema(description = "Stock symbol", example = "RELIANCE", maxLength = 10)
    private String symbol;

    @Schema(description = "Entry price per share", example = "2850.00")
    private BigDecimal entryPrice;

    @Schema(description = "Position status: OPEN, CLOSED, STOPPED", example = "OPEN")
    private String status;
}
```

## Tags for All Controllers

| Controller | Tag Name | Description |
|-----------|----------|-------------|
| HealthController | Health | System health checks |
| SignalController | Signals | Signal generation and queries |
| PositionController | Positions | Position management |
| TradingController | Trading | Trade operations |
| AdminController | Admin | Administrative operations |
| StockDetailController | Stocks | Stock information and charts |
| SettingsController | Settings | System configuration |

## Files to Create

- `api/src/main/java/com/swingtrade/api/config/OpenApiConfig.java`

## Files to Modify

- `api/pom.xml` - Add springdoc dependency
- All `*Controller.java` files - Add `@Tag`, `@Operation`, `@ApiResponses` annotations
- All DTO classes - Add `@Schema` annotations
- `README.md` - Add API documentation link

## Acceptance Criteria

- [ ] springdoc-openapi dependency added to api/pom.xml
- [ ] `/swagger-ui.html` serves interactive API documentation
- [ ] `/api-docs` serves OpenAPI 3.0 JSON spec
- [ ] All controllers have `@Tag` descriptions
- [ ] All endpoints have `@Operation` summaries
- [ ] All DTOs have `@Schema` field descriptions
- [ ] Swagger UI shows request/response examples
- [ ] Swagger UI excluded from test profile
- [ ] API info has correct title, version, description
- [ ] No compilation errors or warnings

## Notes

- Exclude from `test` profile to avoid slowing down tests
- springdoc-openapi v2.x works with Spring Boot 3.x
- The OpenAPI spec can be used to generate client code (TypeScript, Java)
- Consider adding `@Parameter` annotations for path variables and query params
