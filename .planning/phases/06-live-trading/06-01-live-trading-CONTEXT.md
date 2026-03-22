# Phase 6: Live Trading Verification

**Context Document for Plan 06-01: Live Trading Verification**

---

## Zerodha Kite Connect Setup

This document provides step-by-step instructions for setting up Zerodha Kite Connect API for live trading integration with the SwingTrade system.

### What is Zerodha Kite Connect?

Zerodha Kite Connect is a RESTful API suite that provides programmatic access to Zerodha's trading platform. It allows automated order placement, position tracking, portfolio management, and market data retrieval.

**Key Features:**
- Order placement (Market, Limit, Stop-Loss)
- Position and holdings management
- Portfolio and account details
- Market data and quotes
- Transaction history

**Cost:** ₹2,000/year subscription required for API access

---

### Step 1: Register for Zerodha Trading Account

If you don't already have a Zerodha trading account:

1. Visit [Zerodha.com](https://zerodha.com)
2. Click "Open Account" and complete the KYC process
3. Choose Equity (CNC - Cash and Carry) segment for swing trading
4. Complete e-sign (Aadhaar) and bank verification
5. Account typically activated within 2-3 business days

**Requirements:**
- PAN Card
- Aadhaar Card (for e-KYC)
- Bank account (cancelled cheque or bank statement)
- Signature photograph

---

### Step 2: Get API Key from Kite Dashboard

1. **Login to Kite Dashboard:**
   - Go to [kite.trade](https://kite.trade)
   - Login with your trading credentials

2. **Navigate to Settings:**
   - Click on your profile name in the top-right corner
   - Select "Settings" from the dropdown
   - Click on "API Keys" tab

3. **Generate API Key:**
   - Click "Generate Key" button
   - Enter a descriptive name (e.g., "SwingTrade Live Trading")
   - Select environment: **Production** (for live trading)
   - Click "Generate"

4. **Copy Your API Key:**
   - Your API key will be displayed (starts with a 6-character prefix)
   - **IMPORTANT:** Copy both the API Key and Secret Key
   - Store securely - you won't see the secret key again

**Example API Key Format:** `abcdef`

---

### Step 3: OAuth Authorization Flow

Kite Connect uses OAuth 2.0 for authentication. The access token is required for all trading operations.

#### OAuth Flow Steps:

1. **Generate Login URL:**
   ```java
   KiteConnect kiteConnect = new KiteConnect();
   kiteConnect.setApiKey("your_api_key");
   String loginUrl = kiteConnect.generateLoginUrl();
   ```

2. **User Authorization:**
   - Visit the login URL in a browser
   - You'll be redirected to Zerodha's authorization page
   - Login with your trading credentials
   - Authorize the application

3. **Exchange Authorization Code for Access Token:**
   ```java
   // After authorization, you get a request_token
   KiteConnect kiteConnect = new KiteConnect(apiKey);
   kiteConnect.setAccessToken(requestToken);

   // Exchange for actual access token
   KiteTokens tokens = kiteConnect.generateSession(requestToken, apiSecret);
   String accessToken = tokens.getAccessToken();
   ```

4. **Store Access Token Securely:**
   - Save the access token in your application configuration
   - Token is valid until explicitly revoked or account closed
   - Can be refreshed using the same request_token if needed

#### Programmatic OAuth Flow (Recommended):

For automated systems, implement a local OAuth handler:

```java
// 1. Get login URL
String loginUrl = kiteConnect.generateLoginUrl();
System.out.println("Visit: " + loginUrl);

// 2. User opens URL, authorizes, gets redirected with request_token
// Example: http://localhost:8080/callback?request_token=xyz123

// 3. Extract request_token from callback
String requestToken = extractFromUrl(request);

// 4. Generate session
KiteTokens tokens = kiteConnect.generateSession(requestToken, apiSecret);
String accessToken = tokens.getAccessToken();

// 5. Save access token for future use
saveAccessToken(accessToken);
```

---

### Step 4: Configure Environment Settings

#### Environment Modes:

Kite Connect supports two environments:

1. **Live (Production):**
   - Real money trading
   - Requires valid Zerodha account with funds
   - Set `kite.environment=live`

2. **Sandbox (Testing):**
   - Paper trading mode (if available)
   - Use for pre-live testing
   - Set `kite.environment=sandbox`

#### Configuration in application.properties:

```properties
# Broker Mode: paper, live, dry_run
broker.mode=dry_run

# Zerodha Kite Connect Configuration
kite.api-key=your_actual_api_key_here
kite.access-token=your_access_token_from_oauth_flow
kite.environment=live
```

---

### Step 5: Security Best Practices

#### API Key Security:

1. **Never Hardcode Credentials:**
   ```java
   // ❌ BAD - Don't do this
   private String apiKey = "abcdef123456";

   // ✅ GOOD - Use configuration
   @Value("${kite.api-key}")
   private String apiKey;
   ```

2. **Use Environment Variables:**
   ```bash
   export SWING_TRADE_KITE_API_KEY="your_api_key"
   export SWING_TRADE_KITE_ACCESS_TOKEN="your_access_token"
   ```

3. **Use .env Files Locally:**
   ```properties
   # .env file (gitignored)
   KITE_API_KEY=your_api_key
   KITE_ACCESS_TOKEN=your_access_token
   ```

4. **Store in Secure Vault:**
   - Use AWS Secrets Manager
   - Use HashiCorp Vault
   - Use cloud provider secret management

5. **Git Security:**
   - Add `.env` to `.gitignore`
   - Add `application-local.properties` to `.gitignore`
   - Never commit actual credentials

#### Access Token Management:

```java
// Store in secure, encrypted storage
@Component
public class AccessTokenManager {
    private final SecretStore secretStore;

    public void saveAccessToken(String token) {
        secretStore.encryptAndStore("kite.access_token", token);
    }

    public String getAccessToken() {
        return secretStore.decrypt("kite.access_token");
    }
}
```

---

### Step 6: Activation Steps

Follow this safe progression to activate live trading:

#### Phase 1: Dry-Run Testing (Recommended)

```properties
broker.mode=dry_run
kite.api-key=your_api_key
kite.access-token=your_access_token
```

**Expected Behavior:**
- All orders are logged but NOT sent to Zerodha
- Test signal generation and order logic
- Verify risk controls work correctly
- No real money at risk

**Duration:** 1-2 weeks minimum

#### Phase 2: Paper Trading Validation

```properties
broker.mode=paper
```

**Expected Behavior:**
- Orders simulated within SwingTrade system
- Full position tracking
- P&L calculations
- No broker API calls

#### Phase 3: Live Trading (After Validation)

```properties
broker.mode=live
kite.api-key=your_api_key
kite.access-token=your_access_token
kite.environment=live
```

**Pre-Live Checklist:**
- [ ] Dry-run tested for 1+ week
- [ ] All risk controls verified
- [ ] Kill switch tested
- [ ] Daily loss circuit breaker validated
- [ ] Position limits confirmed
- [ ] Initial capital deployed: ₹50,000
- [ ] Maximum 3 concurrent positions
- [ ] Maximum 20% capital per position (₹10,000)

---

### Troubleshooting

#### Common Issues:

1. **"Invalid access token" Error:**
   - Re-run OAuth flow to get new token
   - Check token hasn't expired
   - Verify token is correctly stored

2. **"API key invalid" Error:**
   - Verify API key copied correctly
   - Check API key hasn't been revoked
   - Ensure subscription is active

3. **"Insufficient funds" Error:**
   - Check account balance
   - Verify segment (CNC) is enabled
   - Ensure funds available for position

4. **"Market closed" Error:**
   - Kite market hours: 9:15 AM - 3:30 PM IST
   - Pre-market: 9:00 AM - 9:15 AM
   - Post-market: 3:30 PM - 4:00 PM

---

### Rate Limits and Best Practices

#### API Rate Limits:

- **Orders:** 20 orders/second
- **Quotes:** 5 requests/second
- **Holdings:** 10 requests/second

#### Best Practices:

1. **Use Connection Pooling:**
   - Reuse KiteConnect instance
   - Don't create new instance per request

2. **Implement Retry Logic:**
   - Handle transient API failures
   - Exponential backoff for retries

3. **Cache Market Data:**
   - Don't poll quotes unnecessarily
   - Use WebSocket for real-time updates (if available)

4. **Log All API Calls:**
   - Track order placement attempts
   - Monitor API response times
   - Alert on failures

---

### Testing the Connection

Use the built-in connection test:

```java
@Autowired
private KiteConnectClient kiteConnectClient;

public void testKiteConnection() {
    boolean isConnected = kiteConnectClient.testConnection();
    System.out.println("Connection status: " + isConnected);
}
```

**Expected Output:**
```
INFO  Kite Connect connection test successful. User: John Doe
```

---

### References

- [Zerodha Kite Connect Docs](https://kite.trade/docs/connect/v3/)
- [Kite Connect Java SDK](https://github.com/zerodha/kiteconnect)
- [API Reference](https://kite.trade/docs/api/v3/)
- [Market Hours](https://zerodha.com/support/knowledge-base/market-hours/)

---

*Document Version: 1.0*
*Last Updated: 2026-03-22*
