# Fyers API v3 Reference

> **Source**: Official [fyers-go-sdk](https://github.com/FyersDev/fyers-go-sdk) (v1.4.0) and [fyers-java-sdk](https://github.com/FyersDev/fyers-java-sdk).
> Based on reverse-engineered analysis of the Go SDK source code.

## Host

```
https://api-t1.fyers.in/api/v3   (REST API)
https://api-t1.fyers.in/data     (Market data)
wss://socket.fyers.in/trade/v3   (WebSocket)
```

## Authentication

### 1. Generate Authorization URL

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/generate-authcode`

**Reference**: [auth.go](https://github.com/FyersDev/fyers-go-sdk/blob/main/auth.go)

```
GET https://api-t1.fyers.in/api/v3/generate-authcode?client_id={appId}&redirect_uri={redirectUrl}&response_type=code&state=sample_state
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `client_id` | string | Yes | App ID (e.g., `D2G7EJJ89Z-100`) |
| `redirect_uri` | string | Yes | Registered redirect URL |
| `response_type` | string | Yes | Must be `code` |
| `state` | string | No | Arbitrary state string |

### 2. Exchange Auth Code for Access Token

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/validate-authcode`

**Reference**: [auth.go](https://github.com/FyersDev/fyers-go-sdk/blob/main/auth.go)

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `code` | string | Yes | Auth code (JWT) from callback |
| `appIdHash` | string | Yes | SHA-256 hash of `{appId}:{appSecret}` as hex |
| `grant_type` | string | Yes | `authorization_code` |

```json
// Request body (application/json)
{
  "code": "eyJhbGci...",
  "appIdHash": "a1b2c3d4...",
  "grant_type": "authorization_code"
}
```

### 3. Refresh Access Token

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/validate-refresh-token`

**Reference**: [auth.go](https://github.com/FyersDev/fyers-go-sdk/blob/main/auth.go)

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `refresh_token` | string | Yes | Stored refresh token |
| `appIdHash` | string | Yes | SHA-256 hash of `{appId}:{appSecret}` as hex |
| `grant_type` | string | Yes | `refresh_token` |
| `pin` | string | Yes | 4-digit Fyers PIN |

```json
// Request body (application/json)
{
  "refresh_token": "eyJhbGci...",
  "appIdHash": "a1b2c3d4...",
  "grant_type": "refresh_token",
  "pin": "0000"
}
```

### 4. Logout

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/logout`

**Auth**: `Authorization: {appId}:{accessToken}`

### Auth Header Format

All authenticated API calls use:
```
Authorization: {appId}:{accessToken}
```

Example: `Authorization: D2G7EJJ89Z-100:eyJhbGci...`

### appIdHash Computation

```
appIdHash = SHA-256("{appId}:{appSecret}")
```

Input string: `D2G7EJJ89Z-100:27XMU78XXK` (appId + colon + secret, NOT clientId + colon + secret)
Output: hex-encoded SHA-256 digest (64 chars)

---

## User / Profile

### Get Profile

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/profile`

**Auth**: Required

| Field | Type | Description |
|-------|------|-------------|
| `s` | string | Status (`success` / `error`) |
| `name` | string | Account name |
| `display_name` | string | Display name |
| `email_id` | string | Email |
| `PAN` | string | PAN number |
| `fy_id` | string | Fyers ID |
| `mobile_number` | string | Phone |
| `ddpi_enabled` | bool | DDPI status |
| `mtf_enabled` | bool | MTF status |
| `totp` | bool | TOTP enabled |

### Get Funds

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/funds`

**Auth**: Required

Returns `fund_limit` array with `equityAmount` and `commodityAmount` per fund type.

### Get Holdings

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/holdings`

**Auth**: Required

| Field | Type | Description |
|-------|------|-------------|
| `holdings` | array | Holding items |
| `holdings[].symbol` | string | Stock symbol |
| `holdings[].fyToken` | string | Fyers internal token |
| `holdings[].quantity` | int | Quantity held |
| `holdings[].costPrice` | float | Average cost |
| `holdings[].ltp` | float | Last traded price |
| `holdings[].pl` | float | P&L |
| `overall` | object | Aggregated totals |

---

## Transaction Info

### Get Order Book

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/orders`

**Auth**: Required

| Field | Type | Description |
|-------|------|-------------|
| `orderBook` | array | Order items |
| `orderBook[].id` | string | Order ID |
| `orderBook[].symbol` | string | Symbol |
| `orderBook[].qty` | int | Quantity |
| `orderBook[].remainingQuantity` | int | Remaining |
| `orderBook[].filledQty` | int | Filled quantity |
| `orderBook[].limitPrice` | float | Limit price |
| `orderBook[].stopPrice` | float | Stop price |
| `orderBook[].tradedPrice` | float | Traded price |
| `orderBook[].type` | int | Order type (1=Limit, 2=Market) |
| `orderBook[].productType` | string | CNC, MARGIN, INTRADAY |
| `orderBook[].side` | int | 1=Buy, -1=Sell |
| `orderBook[].status` | int | 1=Cancelled, 2=Traded, 4=Transit, 5=Rejected, 6=Pending |
| `orderBook[].orderDateTime` | string | Timestamp |
| `orderBook[].orderValidity` | string | DAY, IOC, EOF |

**Variants**:
- `GET /api/v3/orders?id={orderId}` — by ID
- `GET /api/v3/orders?order_tag={tag}` — by tag

### Get Order History

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/order-history`

**Auth**: Required

| Parameter | Type | Description |
|-----------|------|-------------|
| `exchange_type` | string | NSE, BSE, etc. |
| `segment_type` | string | EQUITY, DERIVATIVE |
| `status` | string | Pending, Traded, Cancelled, Rejected |
| `symbol` | string | Filter by symbol |
| `from_date` | string | YYYY-MM-DD |
| `to_date` | string | YYYY-MM-DD |
| `page_no` | int | Pagination |
| `page_size` | int | Page size |

### Get Positions

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/positions`

**Auth**: Required

| Field | Type | Description |
|-------|------|-------------|
| `netPositions` | array | Position items |
| `netQty` | int | Net quantity |
| `avgPrice` | float | Average price |
| `netAvg` | float | Net average |
| `side` | int | 1=Long, -1=Short |
| `productType` | string | Product type |
| `realized_profit` | float | Realized P&L |
| `unrealized_profit` | float | Unrealized P&L |
| `ltp` | float | Last traded price |

### Get Trade Book

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/tradebook`

**Auth**: Required

| Field | Type | Description |
|-------|------|-------------|
| `tradeBook` | array | Trade items |
| `orderNumber` | string | Order number |
| `symbol` | string | Symbol |
| `side` | int | Buy/Sell |
| `tradedQty` | int | Traded quantity |
| `tradePrice` | float | Trade price |
| `tradeValue` | float | Trade value |

**Variants**:
- `GET /api/v3/tradebook?order_tag={tag}` — by tag

### Trade Book History

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/trade-history`

Same pagination params as order-history.

### Charges History

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/charges-history`

| Parameter | Type | Description |
|-----------|------|-------------|
| `exchange_type` | string | NSE, BSE, etc. |
| `segment_type` | string | EQUITY, DERIVATIVE |
| `report_type` | int | Type of charge |
| `from_date` | string | YYYY-MM-DD |
| `to_date` | string | YYYY-MM-DD |
| `page_no` | int | Pagination |
| `page_size` | int | Page size |

### Realised Profit History

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/realised-pnl-history`

Same pagination params.

### Tax PnL History

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/tax-pnl-history`

| Parameter | Type | Description |
|-----------|------|-------------|
| `fin_year` | int | Financial year (e.g., 2025) |
| `transaction_type` | string | Buy/Sell |
| `segment` | string | Equity, Derivative |
| `page_no` | int | Pagination |
| `page_size` | int | Page size |

### Ledger History

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/ledger-history`

| Parameter | Type | Description |
|-----------|------|-------------|
| `from_date` | string | YYYY-MM-DD |
| `to_date` | string | YYYY-MM-DD |
| `transaction_type` | string | Type |
| `exchange_type` | string | Exchange |
| `segment_type` | string | Segment |
| `page_no` | int | Pagination |
| `page_size` | int | Page size |

---

## Order Placement

### Place Single Order

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/orders/sync`

**Auth**: Required

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `symbol` | string | Yes | `NSE:SYMBOL-EQ` |
| `qty` | int | Yes | Order quantity |
| `type` | int | Yes | 1=Limit, 2=Market |
| `side` | int | Yes | 1=Buy, -1=Sell |
| `productType` | string | Yes | CNC, MARGIN, INTRADAY |
| `limitPrice` | float | Conditional | Limit price (for type=1) |
| `stopPrice` | float | Conditional | Stop price (for SL orders) |
| `validity` | string | No | DAY, IOC, EOF |
| `disclosedQty` | int | No | Disclosed quantity |
| `offlineOrder` | bool | No | Offline order flag |
| `stopLoss` | float | No | Stop loss price |
| `takeProfit` | float | No | Take profit price |
| `orderTag` | string | No | Internal tracking tag |

**Order Type codes**: 1=Limit, 2=Market, 3=SL-M, 4=SL-L

**Response**:
```json
{
  "s": "success",
  "id": "26070400001234"
}
```

### Place Multiple Orders

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/multi-order/sync`

Body: array of `OrderRequest` objects.

### Place Multi-Leg Order

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/multileg/orders/sync`

| Field | Type | Description |
|-------|------|-------------|
| `orderTag` | string | Tracking tag |
| `productType` | string | Product type |
| `orderType` | string | `3L` for 3-leg |
| `validity` | string | IOC |
| `legs` | object | leg1, leg2, leg3 with symbol, qty, side, type, limitPrice |

### Modify Order

**Endpoint**: `PATCH https://api-t1.fyers.in/api/v3/orders/sync`

Body: `{ "id": "...", "qty": 10, "type": 1, "side": 1, "limitPrice": 100 }`

### Cancel Order

**Endpoint**: `DELETE https://api-t1.fyers.in/api/v3/orders/sync`

Body: `{ "id": "..." }`

### Cancel Multiple Orders

**Endpoint**: `DELETE https://api-t1.fyers.in/api/v3/multi-order/sync`

Body: array of `{ "id": "..." }`.

### Convert Position

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/positions`

| Field | Type | Description |
|-------|------|-------------|
| `symbol` | string | Symbol |
| `positionSide` | int | 1/Long, -1/Short |
| `convertQty` | int | Quantity to convert |
| `convertFrom` | string | Source product |
| `convertTo` | string | Target product |
| `overnight` | int | Overnight flag |

### Exit Position

**Endpoint**: `DELETE https://api-t1.fyers.in/api/v3/positions`

Body: `{ "exit_all": 1 }` or array of `{ "orderId": "..." }`.

### Cancel Pending Orders

**Endpoint**: `DELETE https://api-t1.fyers.in/api/v3/positions`

Body: `{ "pending_orders_cancel": 1, "id": "..." }`

---

## GTT Orders (Good Till Triggered)

### Place GTT Order

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/gtt/orders/sync`

| Field | Type | Description |
|-------|------|-------------|
| `side` | int | 1=Buy, -1=Sell |
| `symbol` | string | Symbol |
| `productType` | string | Product type |
| `orderInfo.leg1.price` | float | Trigger price |
| `orderInfo.leg1.triggerPrice` | float | Trigger price |
| `orderInfo.leg1.qty` | int | Quantity |

### Modify GTT Order

**Endpoint**: `PATCH https://api-t1.fyers.in/api/v3/gtt/orders/sync`

Body: array of `{ "id": "...", "orderInfo": {...} }`.

### Cancel GTT Order

**Endpoint**: `DELETE https://api-t1.fyers.in/api/v3/gtt/orders/sync`

Body: `{ "id": "..." }`.

### Get GTT Order Book

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/gtt/orders`

---

## Smart Orders

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/smart-order/step` | POST | Step order |
| `/smart-order/limit` | POST | Limit order |
| `/smart-order/trail` | POST | Trailing stop loss |
| `/smart-order/sip` | POST | Systematic investment |
| `/smart-order/modify` | PATCH | Modify |
| `/smart-order/cancel` | DELETE | Cancel |
| `/smart-order/pause` | PATCH | Pause |
| `/smart-order/resume` | PATCH | Resume |
| `/smart-order/orderbook` | GET | Order book |
| `/flows/tc/se` | POST/GET | Smart exit trigger |
| `/flows/tc/se/activate` | POST | Activate/deactivate |

---

## Market Data

### Stock History (Candles)

**Endpoint**: `GET https://api-t1.fyers.in/data/history`

**Auth**: Required

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | string | Yes | `NSE:SYMBOL-EQ` |
| `resolution` | string | Yes | `1`, `3`, `5`, `15`, `30`, `60`, `120`, `240`, `D`, `W`, `MN` |
| `date_format` | string | Yes | `1` (ISO format) |
| `date[from]` | string | Yes | Start date (YYYY-MM-DD) |
| `date[to]` | string | Yes | End date (YYYY-MM-DD) |
| `cont_flag` | string | No | `1` for continuous future |

**Response**:
```json
{
  "s": "success",
  "candles": [
    [epoch, open, high, low, close, volume],
    ...
  ]
}
```

Candle arrays: index 0=timestamp, 1=open, 2=high, 3=low, 4=close, 5=volume.

### Stock Quotes

**Endpoint**: `GET https://api-t1.fyers.in/data/quotes`

**Auth**: Required

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbols` | string | Yes | Comma-separated symbols |

**Response**:
```json
{
  "s": "success",
  "d": [
    {
      "n": "NSE:SBIN-EQ",
      "s": "SBIN",
      "v": {
        "ch": 12.30,
        "chp": 0.94,
        "lp": 1316.50,
        "spread": 0.50,
        "ask": 1317.00,
        "bid": 1316.00,
        "open_price": 1305.00,
        "high_price": 1325.00,
        "low_price": 1300.00,
        "prev_close_price": 1304.20,
        "atp": 1310.00,
        "volume": 5432100,
        "short_name": "SBIN",
        "exchange": "NSE",
        "symbol": "SBIN-EQ",
        "fyToken": "13"
      }
    }
  ]
}
```

### Market Depth

**Endpoint**: `GET https://api-t1.fyers.in/data/depth`

**Auth**: Required

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | string | Yes | `NSE:SYMBOL-EQ` |
| `ohlcv_flag` | string | No | `1` to include OHLCV |

**Response**:
```json
{
  "s": "success",
  "d": {
    "NSE:SBIN-EQ": {
      "totalbuyqty": 50000,
      "totalsellqty": 45000,
      "ask": [{ "price": 1317.00, "volume": 1000, "ord": 5 }],
      "bids": [{ "price": 1316.00, "volume": 1500, "ord": 7 }],
      "o": 1305.00, "h": 1325.00, "l": 1300.00, "c": 1316.50,
      "ch": 12.30, "chp": 0.94, "tick_Size": 0.05,
      "ltq": 100, "ltt": 1720000000, "ltp": 1316.50,
      "v": 5432100, "atp": 1310.00,
      "lower_ckt": 1173.80, "upper_ckt": 1434.60,
      "oi": 0, "oiflag": false
    }
  }
}
```

### Option Chain

**Endpoint**: `GET https://api-t1.fyers.in/data/options-chain-v3`

**Auth**: Required

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | string | Yes | `NSE:SYMBOL-EQ` |
| `strikecount` | int | Yes | Number of strikes (e.g., 4) |
| `timestamp` | string | No | Optional timestamp |
| `greeks` | string | No | `1` to include Greeks |

**Response**:
```json
{
  "s": "success",
  "data": {
    "callOi": 0,
    "expiryData": [{ "date": "...", "expiry": "..." }],
    "indiavixData": { ... },
    "optionsChain": [{
      "ask": 100.0, "bid": 99.50, "ltp": 99.75,
      "oi": 50000, "oich": 1000, "oichp": 2.0,
      "option_type": "CE", "strike_price": 13500.0,
      "symbol": "NIFTY24SEP300CE", "volume": 10000,
      "fp": 0, "fpch": 0, "fpchp": 0
    }]
  }
}
```

### Market Status

**Endpoint**: `GET https://api-t1.fyers.in/api/v3/marketStatus`

**Auth**: Required

**Response**:
```json
{
  "s": "success",
  "marketStatus": [{
    "exchange": 1,
    "market_type": "EQUITY",
    "segment": 10,
    "status": "OPEN"
  }]
}
```

### Symbol Token

**Endpoint**: `GET https://api-t1.fyers.in/data/symbol-token`

Used to resolve exchange symbol to Fyers internal token.

---

## Price Alerts

### Create Alert

**Endpoint**: `POST https://api-t1.fyers.in/api/v3/price-alert`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `symbol` | string | Yes | `NSE:SYMBOL-EQ` |
| `agent` | string | No | Default `fyers-api` |
| `alert-type` | int | Yes | Alert type |
| `comparisonType` | string | Yes | `LTP`, `OI`, etc. |
| `condition` | string | Yes | `GT`, `LT`, `EQ`, `GTE`, `LTE` |
| `value` | float | Yes | Threshold value |
| `name` | string | Yes | Alert name |

### Update Alert

**Endpoint**: `PUT https://api-t1.fyers.in/api/v3/price-alert`

Body: `{ "alertId": "...", ...alert fields }`.

### Delete Alert

**Endpoint**: `DELETE https://api-t1.fyers.in/api/v3/price-alert`

Body: `{ "alertId": "...", "agent": "fyers-api" }`.

### Toggle Alert

**Endpoint**: `PUT https://api-t1.fyers.in/api/v3/toggle-alert`

Body: `{ "alertId": "..." }`.

---

## Screeners

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/screeners/config` | GET | Available screeners |
| `/screeners/query` | GET | Query screener |
| `/screeners/candlestick` | GET | Screener candles |
| `/screeners/technical` | GET | Screener technical |

**Query params** for `/screeners/query`:
- `screener` — screener name
- `universe` — universe (e.g., `nifty500`)
- `fields` — comma-separated fields
- `order_by` — sort field
- `order` — `asc` / `desc`

---

## WebSocket

### Data Socket

**Endpoint**: `wss://socket.fyers.in/trade/v3`

Subscribe to real-time market data:

| Parameter | Type | Description |
|-----------|------|-------------|
| `accessToken` | string | Format `{appId}:{accessToken}` |
| `symbols` | array | `NSE:SBIN-EQ`, etc. |
| `dataType` | string | `SymbolUpdate`, `DepthUpdate` |
| `liteMode` | bool | Reduced payload |

### Order Socket

**Endpoint**: `wss://socket.fyers.in/trade/v3`

Subscribe to order/trade/position updates:

| Parameter | Type | Description |
|-----------|------|-------------|
| `tradeOperations` | array | `OnOrders`, `OnTrades`, `OnPositions` |

### HSM WebSocket

**Endpoint**: `socket.fyers.in/hsm/v1-5/prod`

For HSM (Hardware Security Module) data with full market depth.

---

## Error Response Format

All endpoints return errors in a consistent structure:

```json
{
  "code": -16,
  "message": "Could not authenticate the user",
  "s": "error"
}
```

| Code | Message | Meaning |
|------|---------|---------|
| -16 | "Could not authenticate the user" | Invalid credentials or app not verified |
| -1 | "Bad Request" | Missing/invalid parameters |
| -2 | "Internal Server Error" | Fyers server error |

Common error codes:
- `code: -16` — App credentials rejected (check app verification status)
- `code: -1` — Invalid request format or missing fields

---

## Symbol Format

Fyers uses a specific symbol format across all endpoints:

- **Equity**: `NSE:SYMBOL-EQ` (e.g., `NSE:SBIN-EQ`, `NSE:TCS-EQ`)
- **Futures**: `NSE:SYMBOLMONTHYEAR-FUT` (e.g., `NSE:SBIN26FEBFUT`)
- **Options**: `NSE:SYMBOLMONTHSTRIKE-CE/PE` (e.g., `NSE:NIFTY24SEP300CE`)
- **Index**: `NSE:SYMBOL-INDEX` (e.g., `NSE:NIFTY50-INDEX`)
- **Token**: `fyToken` field is the internal identifier for some endpoints

---

## Comparison: Go SDK vs Our Java Implementation

| Feature | Go SDK | Our Java (FyersServiceClient) | Status |
|---------|--------|------------------------------|--------|
| Token exchange endpoint | `/validate-authcode` | `/api/v3/token` | **Mismatch** |
| Token body format | `code` + `appIdHash` + `grant_type` | `app_id` + `secret_key` + `code` + `grant_type` + `redirect_uri` | **Mismatch** |
| Refresh endpoint | `/validate-refresh-token` | `/api/v3/token` | **Mismatch** |
| Refresh body | `refresh_token` + `appIdHash` + `pin` + `grant_type` | `grant_type` + `refresh_token` + `appIdHash` | **Missing pin** |
| Auth header | `{appId}:{accessToken}` | `token {accessToken}` | **Mismatch** |
| Candle endpoint | `/data/history` | `/data/history` | OK |
| Symbol format | `NSE:SYMBOL-EQ` | `NSE:SYMBOL-EQ` | OK |
| Quote endpoint | `/data/quotes?symbols=X` | Not implemented | Missing |
| Market depth | `/data/depth?symbol=X` | Not implemented | Missing |
| Option chain | `/data/options-chain-v3` | Not implemented | Missing |

---

## Reliability Notes

- **PIN required for refresh**: The refresh token endpoint requires a 4-digit Fyers PIN — not the secret key
- **Token format**: Auth header uses `{appId}:{accessToken}` (colon-separated, no `token` prefix)
- **appIdHash**: Always SHA-256 of `{appId}:{appSecret}` where appId is the full ID with `-100` suffix
- **Symbol format**: Must follow `EXCHANGE:SYMBOL-TYPE` convention
- **Rate limits**: Not documented; implement reasonable delays between requests
- **App verification**: Error `-16` often means the app is not verified/activated in the Fyers developer portal

---

## References

- [fyers-go-sdk (official Go SDK)](https://github.com/FyersDev/fyers-go-sdk)
- [fyers-java-sdk (official Java SDK)](https://github.com/FyersDev/fyers-java-sdk)
- [fyers-api (Python)](https://github.com/FyersDev/fyers-api) — `app_id + secret_key` format (older format)
- [Fyers API docs](https://myapi.fyers.in/docsv3) — behind Cloudflare
