# Yahoo Finance API Reference

> **Warning**: Yahoo Finance has NO official API. Yahoo killed the yfapi in 2017.
> All endpoints are undocumented, reverse-engineered, and can break without notice.
> This doc is based on the community-maintained [yahoo-finance2](https://github.com/gadicc/yahoo-finance2) library (v2.x).

## Host

```
query2.finance.yahoo.com   (primary)
query1.finance.yahoo.com   (fallback, rarely used)
```

No authentication, crumb, or cookie required for v8/chart and v7/quote endpoints.
The v1/search endpoint also works without auth.

---

## 1. Historical Chart (OHLCV)

**Endpoint**: `GET https://query2.finance.yahoo.com/v8/finance/chart/{symbol}`

**Reference**: [yahoo-finance2 chart module](https://github.com/gadicc/yahoo-finance2/blob/dev/docs/modules/chart.md)

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `period1` | epoch seconds | Yes | — | Start of range |
| `period2` | epoch seconds | No | `now` | End of range |
| `interval` | string | No | `1d` | `1m` `2m` `5m` `15m` `30m` `60m` `90m` `1h` `1d` `5d` `1wk` `1mo` `3mo` |
| `includePrePost` | boolean | No | `true` | Include pre/post market data (not relevant for Indian stocks) |
| `events` | string | No | `div|split|earn` | Include dividends/splits/earnings data. Use `history` for OHLCV only |
| `lang` | string | No | `en-US` | Language code |
| `useYfid` | boolean | No | `true` | Use Yahoo Finance ID |

### Response Structure

```json
{
  "chart": {
    "result": [{
      "meta": {
        "symbol": "RELIANCE.NS",
        "fullExchangeName": "NSE",
        "instrumentType": "EQUITY",
        "currency": "INR",
        "longName": "Reliance Industries Limited",
        "shortName": "RELIANCE INDUSTRIES LTD",
        "regularMarketPrice": 1316.5,
        "fiftyTwoWeekHigh": 1611.8,
        "fiftyTwoWeekLow": 1253.2,
        "chartPreviousClose": 1356.3,
        "regularMarketTime": 1782381599,
        "firstTradeDate": 820467900,
        "timezone": "IST",
        "gmtoffset": 19800
      },
      "timestamp": [1704998400, 1705257600, ...],
      "indicators": {
        "quote": [{
          "open": [100.0, 2500.0, ...],
          "high": [105.0, 2510.0, ...],
          "low": [99.0, 2440.0, ...],
          "close": [104.0, 2500.0, ...],
          "volume": [5000000, 4800000, ...]
        }],
        "adjclose": [{
          "adjclose": [104.0, 2475.50, ...]
        }]
      }
    }],
    "error": null
  }
}
```

### Key Notes

- Arrays are parallel: `timestamp[i]` corresponds to `quote[0].open[i]`, `quote[0].close[i]`, `adjclose[0].adjclose[i]`
- `adjclose` array may be missing or empty for some symbols
- Pre-market placeholder candles have `volume = 0` — filter these out
- Missing/invalid data returns `null` values in arrays
- `timestamp` values are Unix epoch seconds (NOT days)
- `firstTradeDate` is also epoch seconds

---

## 2. Quote (Real-Time Price)

**Endpoint**: `GET https://query2.finance.yahoo.com/v7/finance/quote?symbols={symbol}`

**Reference**: [yahoo-finance2 quote module](https://github.com/gadicc/yahoo-finance2/blob/dev/docs/modules/quote.md)

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `symbols` | string | Yes | — | Single symbol or comma-separated list |
| `fields` | string | No | all | Comma-separated list of fields to return. Options: `symbol`, `regularMarketPrice`, `currency`, `regularMarketVolume`, `marketState`, `quoteType`, `shortName`, `longName`, `fiftyTwoWeekHigh`, `fiftyTwoWeekLow`, `regularMarketTime`, `chartPreviousClose`, `averageDailyVolume3Month`, `averageDailyVolume10Day`, etc. |

### Response Structure

```json
{
  "finance": {
    "result": [{
      "symbol": "RELIANCE.NS",
      "shortName": "RELIANCE INDUSTRIES LTD",
      "longName": "Reliance Industries Limited",
      "regularMarketPrice": 1316.50,
      "regularMarketChange": 12.30,
      "regularMarketChangePercent": 0.94,
      "regularMarketTime": "4:00PM IST",
      "regularMarketDayHigh": 1325.00,
      "regularMarketDayLow": 1305.00,
      "regularMarketVolume": 5432100,
      "regularMarketPreviousClose": 1304.20,
      "fiftyTwoWeekLow": 1253.20,
      "fiftyTwoWeekHigh": 1611.80,
      "fiftyDayAverage": 1290.45,
      "twoHundredDayAverage": 1350.60,
      "currency": "INR",
      "currencySymbol": "₹",
      "quoteType": "EQUITY",
      "exchange": "NMS",
      "exchangeName": "NSE",
      "exchangeDataDelayedBy": 0,
      "marketState": "CLOSED",
      "fullExchangeName": "NSE",
      "esgPoppedExchange": "",
      "averageDailyVolume3Month": 4500000,
      "averageDailyVolume10Day": 5100000,
      "language": "en-US",
      "region": "IN",
      "quoteSourceName": "Delayed Quote",
      "priceHint": 2,
      "sourceInterval": 15,
      "tradeable": false,
      "triggerable": false,
      "firstTradeDateEpoch": 820467900
    }],
    "error": null
  }
}
```

### Key Notes

- Supports batch queries: `?symbols=AAPL,GOOGL,TSLA`
- `fields` parameter lets you request only specific fields (reduces payload)
- `tradeable: false` for delayed data (Indian markets)
- `marketState`: `PRE`, `REGULAR`, `POST`, `CLOSED`, `CLOSED`
- Delisted symbols return `quoteType: "NONE"` — filter these out
- Some fields only appear during market hours (e.g., `regularMarketPrice` updates)

---

## 3. Search

**Endpoint**: `GET https://query2.finance.yahoo.com/v1/finance/search?q={query}`

**Reference**: [yahoo-finance2 search module](https://github.com/gadicc/yahoo-finance2/blob/dev/docs/modules/search.md)

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `q` | string | Yes | — | Search term (symbol, company name, keyword) |
| `lang` | string | No | `en-US` | Language code |
| `region` | string | No | `US` | Region code |
| `quotesCount` | int | No | `6` | Number of quote results |
| `newsCount` | int | No | `4` | Number of news results |
| `enableFuzzyQuery` | boolean | No | `false` | Fuzzy/partial matching |
| `enableEnhancedTrivialQuery` | boolean | No | `true` | Enhanced trivial query processing |
| `enableCb` | boolean | No | `true` | Enable callback wrapping (JSONP) |
| `enableNavLinks` | boolean | No | `true` | Enable navigation links |

### Response Structure

```json
{
  "quotes": [{
    "symbol": "RELIANCE.NS",
    "shortname": "Reliance Industries Limited",
    "quoteType": "EQUITY",
    "exchange": "NSE",
    "exchangeName": "NSE",
    "index": "quote",
    "isYahooFinance": true
  },
  ...],
  "news": [...],
  "warnings": [...],
  "totalTime": 123,
  "timeTakenForQuotes": 50,
  "timeTakenForNews": 30,
  "timeTakenForPredefinedScreener": 10,
  "timeTakenForPathway": 0,
  "buoys": [...]
}
```

### Key Notes

- Returns both Yahoo Finance symbols AND non-Yahoo entities (Crunchbase companies)
- Filter by `isYahooFinance: true` for valid trading symbols
- `quoteType` distinguishes EQUITY, ETF, MUTUALFUND, CRYPTOCURRENCY, etc.
- `exchange` field indicates the trading venue (NSE, BSE, NASDAQ, etc.)
- Useful for discovering symbols before fetching chart/quote data

---

## Response Error Format

All endpoints return errors in a consistent structure:

```json
{
  "finance": {
    "result": null,
    "error": {
      "code": "Bad Request",
      "description": "Missing required query parameter=q"
    }
  }
}
```

Or for chart:

```json
{
  "chart": {
    "result": null,
    "error": {
      "code": "NotFound",
      "description": "No data found for symbol XYZ"
    }
  }
}
```

HTTP-level errors return the status text as the error message.

---

## Comparison: Yahoo vs Our Implementation

| Feature | Yahoo API | YahooFinanceClient |
|---------|-----------|-------------------|
| Base URL | `query2.finance.yahoo.com` | `query1.finance.yahoo.com` |
| Chart endpoint | `/v8/finance/chart/{symbol}` | `/v8/finance/chart/{symbol}` |
| Quote endpoint | `/v7/finance/quote?symbols=X` | Not implemented |
| Search endpoint | `/v1/finance/search?q=X` | Not implemented |
| Date format | Epoch seconds | Epoch seconds |
| adjClose parsing | `indicators.adjclose[0].adjclose` | Implemented |
| Pre-market filtering | `volume == 0` candles | Implemented |
| Meta extraction | `chart.result[0].meta` | Implemented |
| Crumb auth | Not needed for v8/v7 | N/A |
| Rate limiting | Unknown, aggressive blocking | No rate limiting logic |
| Retry/backoff | Not built in | No retry logic |

---

## Reliability Notes

From the yahoo-finance2 library:

- **Network errors**: request timeouts, no response
- **HTTP errors**: internal errors from Yahoo
- **Missing resources**: asking for data that doesn't exist
- **Validation errors**: Yahoo returns data that doesn't match expected schema
- **Delisted stocks**: queries that worked previously begin to throw errors, including historical data from before delisting
- **No SLA**: endpoints can and do change without notice
- **Blocking**: Yahoo aggressively blocks automated requests; consider adding delays between requests

---

## References

- [yahoo-finance2 (main repo)](https://github.com/gadicc/yahoo-finance2)
- [yahoo-finance2 API docs](https://github.com/gadicc/yahoo-finance2/blob/dev/docs/README.md)
- [yahoo-finance2 chart module docs](https://github.com/gadicc/yahoo-finance2/blob/dev/docs/modules/chart.md)
- [yfinance (Python)](https://github.com/ranaroussi/yfinance) — best reference for endpoint behavior
- `YF_QUERY_HOST` default: `query2.finance.yahoo.com` (from [yahooFinanceFetch.ts](https://github.com/gadicc/yahoo-finance2/blob/dev/src/lib/yahooFinanceFetch.ts))
