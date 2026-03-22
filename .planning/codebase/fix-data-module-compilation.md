# Fix Data Module Compilation Errors

## Root Cause Analysis

### Issue 1: `CandleData` Record Method Access
**File:** `data/src/main/java/com/swingtrade/data/service/CandleData.java`

The `CandleData` record uses **Java record canonical accessors**:
```java
public record CandleData(
    String symbol,
    LocalDate date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume
)
```

Record accessors are: `candle.date()`, `candle.open()`, `candle.high()`, etc. (not camelCase getters)

**Location of error:** `data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`
- Lines 168-173 use incorrect getter syntax: `candle.getDate()`, `candle.getOpen()`, etc.

### Issue 2: `Instrument` Class Naming Conflict
**File:** `data/src/main/java/com/swingtrade/data/service/UpstoxRestClient.java`

The code uses `com.swingtrade.data.service.Instrument` at:
- Line 299: `map(com.swingtrade.data.service.Instrument::getSymbol)`
- Line 344: `toInstrumentDetails(com.swingtrade.data.service.Instrument)`

**Problem:** No `Instrument` class exists in the `com.swingtrade.data.service` package. Only:
- `InstrumentDetails.java` (public record)
- `Instrument` (nested private static class inside `UpstoxRestClient`)

### Issue 3: Return Type Mismatch
**File:** `data/src/main/java/com/swingtrade/data/service/UpstoxRestClient.java`

Method signature (line 225):
```java
public Iterable<CandleData> fetchCandles(String symbol, LocalDate startDate, LocalDate endDate)
```

Implementation (line 244) returns `Stream<CandleData>` instead of `Iterable<CandleData>`.

### Issue 4: Invalid Map Manipulation in `fetchCandle`
**File:** `data/src/main/java/com/swingtrade/data/service/UpstoxRestClient.java`

Lines 211-216 have broken logic for mapping candles:
```java
var candleMap = new HashMap<LocalDate, OhlcCandleData>();
candleList.forEach(c -> c.getDate()
    .map(d -> new HashMap<LocalDate, OhlcCandleData>() {{
        put(d, c);
    }})
    .ifPresent(m -> m.forEach(candleMap::put)));
```

This is convoluted and incorrect. Should use `Collectors.toMap()` or stream API.

## Fix Plan

### Fix 1: Update `DataIngestionService.saveCandle()` (DataIngestionService.java)
**Lines 165-176:** Change record accessor calls from getter-style to record-style:
- `candle.getDate()` → `candle.date()`
- `candle.getOpen()` → `candle.open()`
- `candle.getHigh()` → `candle.high()`
- `candle.getLow()` → `candle.low()`
- `candle.getClose()` → `candle.close()`
- `candle.getVolume()` → `candle.volume()`

### Fix 2: Update `UpstoxRestClient.fetchAllStockSymbols()` (UpstoxRestClient.java)
**Line 299:** Change `com.swingtrade.data.service.Instrument::getSymbol` to use `InstrumentDetails`:
- Use `com.swingtrade.data.service.InstrumentDetails` instead (but this doesn't have symbol from API response)
- **Better approach:** Create a DTO class for API response or use the nested `Instrument` class directly

### Fix 3: Update `UpstoxRestClient.toInstrumentDetails()` (UpstoxRestClient.java)
**Line 344:** Change parameter type from `com.swingtrade.data.service.Instrument` to nested `Instrument`:
- Remove the package qualification and use the nested class

### Fix 4: Fix Return Type in `fetchCandles()` (UpstoxRestClient.java)
**Line 237, 244:** Change to return `Iterable`:
```java
return getWithAuth(uri, OhlcResponse.class)
    .map(response -> {
        if (response == null || response.getData() == null) {
            return List.<CandleData>of();
        }
        return response.getData().getCandles().stream()
                .map(this::toCandleData)
                .toList(); // Collect to List which is Iterable
    })
    .block(); // Return List directly, not Stream
```

### Fix 5: Fix `fetchCandle()` Map Logic (UpstoxRestClient.java)
**Lines 206-222:** Simplify the mapping logic:
```java
return getWithAuth(uri, OhlcResponse.class)
    .map(response -> {
        if (response != null && response.getData() != null &&
                response.getData().getCandles() != null &&
                !response.getData().getCandles().isEmpty()) {
            return response.getData().getCandles().stream()
                .filter(c -> c.getDate().filter(d -> d.equals(date)).isPresent())
                .findFirst()
                .orElse(null);
        }
        return null;
    })
    .block();
```

### Fix 6: Fix `fetchLatestCandle()` Return Type (UpstoxRestClient.java)
**Line 266:** Returns `OhlcCandleData` but should return `CandleData`:
- Use `toCandleData()` conversion function

## Files to Modify
1. `/Users/kayisrahman/Documents/workspace/ideas/swing-trade/data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`
2. `/Users/kayisrahman/Documents/workspace/ideas/swing-trade/data/src/main/java/com/swingtrade/data/service/UpstoxRestClient.java`

## Success Criteria
- `mvn compile` succeeds in `/Users/kayisrahman/Documents/workspace/ideas/swing-trade/data`
- No compilation errors related to `OhlcCandleData`, `Instrument`, or `CandleData` accessor methods
- Test can run successfully
