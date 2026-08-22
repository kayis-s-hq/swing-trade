# AD-C3: Wire Resilience4j into External API Clients

## Already Done (Previous Session)
- Resilience4j 2.2.0 dependencies in data, llm, api, broker build.gradle.kts
- `Resilience4jConfig.java` — programmatic CB, bulkhead, time limiter beans
- Read timeouts on all WebClients
- LLM double-call fixed in SignalPipeline
- CachedThreadPool → bounded FixedThreadPool in JobOrchestratorService
- Yahoo URL configurable via @Value
- Upstox config still present in MarketDataClientConfig

## Phase 3: Wire Circuit Breakers into Data Clients

### 3.1 YahooFinanceClient — decorate with circuit breaker + time limiter

File: `backend/data/src/main/java/com/swingtrade/data/client/YahooFinanceClient.java`

Add a constructor that accepts `CircuitBreaker`, `Bulkhead`, `TimeLimiter`:

```java
// Add imports
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import reactor.core.publisher.Mono;

// Add static field (avoids repeated string allocation)
private static final String CANDLE_URI_FMT = "/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d&events=history&includePrePost=false";

// Add field
private final java.util.function.Supplier<Mono<String>> decoratedYahooCall;

// Thread pool naming enum (avoids hardcoded strings in multiple places)
enum ResilienceThreadName { YAHOO("yahoo-timelimiter"), FYERS("fyers-timelimiter"), FYERS_AUTH("fyers-auth-timelimiter"), LLM("llm-timelimiter"), NEWS("news-timelimiter"), DISCORD("discord-timelimiter"); private final String label; ResilienceThreadName(String label) { this.label = label; } @Override public String toString() { return label; } }

// Add constructor (called from Spring config)
public YahooFinanceClient(String baseUrl, ObjectMapper objectMapper, java.time.Clock clock,
                          CircuitBreaker circuitBreaker, Bulkhead bulkhead, TimeLimiter timeLimiter) {
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                HttpClient.create()
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                    .responseTimeout(READ_TIMEOUT)))
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build();
    // Decorate: time limiter → bulkhead → circuit breaker
    this.decoratedYahooCall = Decorators.ofSupplier(
            () -> fetchFromYahoo())
        .withTimeLimiter(timeLimiter,
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, ResilienceThreadName.YAHOO.toString());
                t.setDaemon(true);
                return t;
            }))
        .withBulkhead(bulkhead)
        .withCircuitBreaker(circuitBreaker)
        .decorate();
}

// Private method that does the actual HTTP call (extracted from fetchCandle, fetchQuotes, etc.)
private Mono<String> fetchFromYahoo() {
    // This won't work because each method has different URI logic.
    // Better approach: wrap each public method.
}
```

**Better approach** — wrap each public method individually:

```java
// In fetchCandle:
public CandleData fetchCandle(String symbol, LocalDate date) {
    try {
        enforceRateLimit();
        String yfinanceSymbol = formatSymbolForYahoo(symbol);
        long timestamp = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long nextDay = date.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        String uri = String.format(CANDLE_URI_FMT, yfinanceSymbol, timestamp, nextDay);

        String response = decoratedYahooCall.get()
            .timeout(READ_TIMEOUT)
            .onErrorResume(CallNotPermittedException.class, e -> {
                logger.warn("Circuit breaker open for yahoo, skipping fetch");
                return Mono.empty();
            })
            .block();
        // ... rest of parsing logic
    } catch (Exception e) {
        logger.warn("Failed to fetch candle for {} on {}: {}", symbol, date, e.getMessage());
        return null;
    }
}
```

**Even better** — use Reactor operators directly since Yahoo uses `Mono<String>`:

```java
// Add to constructor
this.yahooCircuitBreaker = circuitBreaker;
this.yahooBulkhead = bulkhead;
this.yahooTimeLimiter = timeLimiter;
```

Then in each method, wrap the `webClient` call:

```java
// In fetchCandle, replace the webClient call with:
Mono<String> responseMono = webClient.get()
    .uri(uri)
    .retrieve()
    .onStatus(status -> status.value() == 404, r -> Mono.empty())
    .onStatus(status -> status.value() >= 400, r -> Mono.empty())
    .bodyToMono(String.class);

String response = responseMono
    .transformDeferred(CircuitBreakerOperator.of(yahooCircuitBreaker))
    .transformDeferred(BulkheadOperator.of(yahooBulkhead))
    .timeout(TimeLimiterOperator.of(yahooTimeLimiter))
    .onErrorResume(CallNotPermittedException.class, e -> {
        logger.warn("Circuit breaker open for yahoo");
        return Mono.empty();
    })
    .block();
```

### 3.2 FyersServiceClient — same pattern

File: `backend/data/src/main/java/com/swingtrade/data/service/FyersServiceClient.java`

Add circuit breaker, bulkhead, time limiter fields. In `getWithAuthRetry` / `doGet`:

```java
private String doGet(URI uri, String appId, String token,
                     CircuitBreaker circuitBreaker, Bulkhead bulkhead, TimeLimiter timeLimiter) {
    return webClient.get()
        .uri(uriBuilder -> uriBuilder.replacePath(uri.getRawPath())
            .replaceQuery(uri.getRawQuery()).build())
        .header("Authorization", appId + ":" + token)
        .retrieve().bodyToMono(String.class)
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .transformDeferred(BulkheadOperator.of(bulkhead))
        .timeout(TimeLimiterOperator.of(timeLimiter))
        .onErrorResume(CallNotPermittedException.class, e -> {
            logger.warn("Circuit breaker open for fyers");
            return Mono.empty();
        })
        .block();
}
```

### 3.3 FyersAuthService — same pattern

File: `backend/data/src/main/java/com/swingtrade/data/service/FyersAuthService.java`

In `postTokenRequest`:

```java
private String postTokenRequest(String path, String body,
                                CircuitBreaker circuitBreaker, Bulkhead bulkhead, TimeLimiter timeLimiter) {
    return webClient.post()
        .uri(path)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .bodyValue(body)
        .retrieve()
        .bodyToMono(String.class)
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .transformDeferred(BulkheadOperator.of(bulkhead))
        .timeout(TimeLimiterOperator.of(timeLimiter))
        .onErrorResume(CallNotPermittedException.class, e -> {
            logger.warn("Circuit breaker open for fyers auth");
            return Mono.empty();
        })
        .block();
}
```

### 3.4 Update MarketDataClientConfig to inject Resilience4j instances

File: `backend/data/src/main/java/com/swingtrade/data/config/MarketDataClientConfig.java`

```java
// Add imports
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.util.Map;

// Update yahoo bean
@Bean(name = "yahoo")
public MarketDataClient yahooFinanceClient(
        @Value("${yahoo.api.base-url:https://query1.finance.yahoo.com}") String baseUrl,
        Map<String, CircuitBreaker> circuitBreakers,
        Map<String, Bulkhead> bulkheads,
        Map<String, TimeLimiter> timeLimiters) {
    CircuitBreaker cb = circuitBreakers.get("yahoo");
    Bulkhead bh = bulkheads.get("yahoo");
    TimeLimiter tl = timeLimiters.get("yahoo");
    return new YahooFinanceClient(baseUrl, new ObjectMapper(), java.time.Clock.systemUTC(), cb, bh, tl);
}

// Update fyers bean — same pattern
@Bean(name = "fyers")
public MarketDataClient fyersServiceClient(
        WebClient.Builder webClientBuilder,
        FyersAuthService authService,
        FyersSymbolMasterService symbolMasterService,
        Map<String, CircuitBreaker> circuitBreakers,
        Map<String, Bulkhead> bulkheads,
        Map<String, TimeLimiter> timeLimiters) {
    CircuitBreaker cb = circuitBreakers.get("fyers");
    Bulkhead bh = bulkheads.get("fyers");
    TimeLimiter tl = timeLimiters.get("fyers");
    FyersServiceClient client = new FyersServiceClient(webClientBuilder, authService, symbolMasterService, BASE_URL);
    // Set Resilience4j instances on client via setter or new constructor
    client.setResilience4j(cb, bh, tl);
    return client;
}
```

## Phase 5: Wire Resilience4j into News + Discord

### 5.1 NewsIngestionService

File: `backend/llm/src/main/java/com/swingtrade/llm/service/NewsIngestionService.java`

Add circuit breaker + time limiter to the parallel fetch. In `fetchStockNews`, wrap the `CompletableFuture` chain:

```java
// Add field
private final CircuitBreaker newsCircuitBreaker;
private final TimeLimiter newsTimeLimiter;

// In constructor (injected from Spring)
public NewsIngestionService(
        // ... existing params ...
        CircuitBreaker newsCircuitBreaker,
        TimeLimiter newsTimeLimiter) {
    // ... existing init ...
    this.newsCircuitBreaker = newsCircuitBreaker;
    this.newsTimeLimiter = newsTimeLimiter;
}

// Wrap the CompletableFuture.allOf block:
CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
try {
    // Decorate with circuit breaker
    Runnable decorated = CircuitBreaker.decorateRunnable(
        newsCircuitBreaker,
        () -> {
            try {
                all.get(timeoutSeconds * 10, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Parallel fetch interrupted for {}: {}", stockSymbol, e.getMessage());
            }
        }
    );
    decorated.run();
} catch (Exception e) {
    logger.warn("News fetch circuit breaker tripped for {}: {}", stockSymbol, e.getMessage());
}
```

### 5.2 DiscordNotificationService

<!-- DEFERRED: Discord circuit breaker — not yet needed, no active Discord integration -->
<!-- File: `backend/broker/src/main/java/com/swingtrade/broker/service/DiscordNotificationService.java`
In `postWebhook`:
```java
private final CircuitBreaker discordCircuitBreaker;
private final Bulkhead discordBulkhead;
// ... wire via constructor injection, wrap postWebhook with operators ...
```
-->

## Phase 7: Comment Out Upstox Config

File: `backend/data/src/main/java/com/swingtrade/data/config/MarketDataClientConfig.java`

```java
// @Bean(name = "upstox")
// @Profile("upstox")
// public MarketDataClient upstoxServiceClient(
//         WebClient.Builder webClientBuilder,
//         UpstoxAuthService authService,
//         NseInstrumentService instrumentService) {
//     return new UpstoxServiceClient(webClientBuilder, authService, instrumentService);
// }
```

Also comment out the Upstox-related imports if they become unused after this.

## Verification
- `./gradlew compileJava` from `backend/`
- `./gradlew test` — ensure no regressions
- Verify Prometheus metrics for circuit breaker states at `/actuator/prometheus`