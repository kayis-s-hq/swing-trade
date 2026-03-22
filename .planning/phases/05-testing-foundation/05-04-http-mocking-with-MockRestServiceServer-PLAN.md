---
phase: 05-testing-foundation
plan: 04
type: execute
wave: 4
depends_on:
  - 05-03
files_modified:
  - data/src/test/java/com/swingtrade/data/test/UpstoxRestClientTest.java
  - data/src/test/java/com/swingtrade/data/test/UpstoxRestClientMockTest.java
  - data/src/test/resources/mock-upstox-login-response.json
  - data/src/test/resources/mock-upstox-ohlcv-response.json
  - llm/src/test/java/com/swingtrade/llm/test/VLLMClientTest.java
  - llm/src/test/java/com/swingtrade/llm/test/VLLMClientMockTest.java
  - llm/src/test/resources/mock-vllm-sentiment-response.json
autonomous: true
requirements:
  - REQ-104
user_setup: []

must_haves:
  truths:
    - UpstoxRestClient login endpoint mocked with MockRestServiceServer
    - Token refresh endpoint mocked with expected JWT response
    - OHLCV data endpoint mocked with realistic market data
    - vLLM sentiment endpoint mocked with OpenAI-compatible response
    - Mock responses return correct HTTP status codes
    - Error scenarios (401, 500) handled correctly
  artifacts:
    - path: "data/src/test/java/com/swingtrade/data/test/UpstoxRestClientTest.java"
      provides: "UpstoxRestClient unit tests with mock responses"
      min_lines: 120
    - path: "data/src/test/java/com/swingtrade/data/test/UpstoxRestClientMockTest.java"
      provides: "Upstox API mocking with MockRestServiceServer"
      min_lines: 80
    - path: "llm/src/test/java/com/swingtrade/llm/test/VLLMClientTest.java"
      provides: "VLLM client unit tests with mock responses"
      min_lines: 100
    - path: "llm/src/test/java/com/swingtrade/llm/test/VLLMClientMockTest.java"
      provides: "vLLM endpoint mocking with MockRestServiceServer"
      min_lines: 80
    - path: "data/src/test/resources/mock-upstox-login-response.json"
      provides: "Mock Upstox login response"
      min_lines: 20
    - path: "data/src/test/resources/mock-upstox-ohlcv-response.json"
      provides: "Mock Upstox OHLCV response"
      min_lines: 50
    - path: "llm/src/test/resources/mock-vllm-sentiment-response.json"
      provides: "Mock vLLM sentiment response"
      min_lines: 30
  key_links:
    - from: "data/src/test/java/com/swingtrade/data/test/UpstoxRestClientMockTest.java"
      to: "data/src/main/java/com/swingtrade/data/client/UpstoxRestClient.java"
      via: "MockRestServiceServer creating stub for POST /v2/login, GET /v2/market-data/ohlcv"
      pattern: "MockRestServiceServer|expect.*Post.*Login"
    - from: "llm/src/test/java/com/swingtrade/llm/test/VLLMClientMockTest.java"
      to: "llm/src/main/java/com/swingtrade/llm/client/VLLMClient.java"
      via: "MockRestServiceServer for chat completion endpoint"
      pattern: "MockRestServiceServer.*chat.*completions"
---

<objective>
Create HTTP mocking tests using Spring's MockRestServiceServer for Upstox API and vLLM endpoint (NOT WireMock), including realistic mock responses and error scenario testing.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
@/Users/kayisrahman/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS.md (REQ-104)
@.planning/ROADMAP.md (Phase 5 goal)
@05-03-integration-test-infrastructure-SUMMARY.md (TestContainers patterns)

# Decision: NO WireMock - Use MockRestServiceServer
<!-- Explicit project decision per user requirements -->

Key differences:
| Feature | MockRestServiceServer | WireMock |
|---------|----------------------|----------|
| Native | Spring-native | Separate library |
| Setup | Simple within Spring test context | Requires standalone server |
| Performance | Lighter | Heavier (standalone JVM) |
| Flexibility | Basic request matching | Advanced (XPath, regex, etc.) |

**For this project:** MockRestServiceServer is sufficient for:
- Upstox REST API (login, token refresh, OHLCV, instruments)
- vLLM OpenAI-compatible endpoint (chat completions)

# MockRestServiceServer Pattern
<!-- How to mock external HTTP calls with Spring -->

```java
@SpringBootTest
class UpstoxRestClientTest {

    @Autowired
    private TestRestRestClient testRestClient;

    @Autowired
    private UpstoxRestClient upstoxRestClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        mockServer = MockRestServiceServer.createServer();
        // Override restTemplate with mock server
        ((SingletonBeanResolver) restTemplate.getBeanFactory()
            .getBean("upstoxRestClient").getClass()
            // Set mock server URL
        );
    }

    @Test
    void testLogin() {
        mockServer.expect(requestTo("https://api.upstox.com/v2/login"))
            .andExpect(method(Post))
            .andExpect(content().json(loginRequest))
            .andRespond(withSuccess(loginResponse, MediaType.APPLICATION_JSON));

        AuthResponse response = upstoxRestClient.login(apiKey, secret);

        assertThat(response.getAccessToken()).isNotNull();
        mockServer.verify();
    }
}
```

# Upstox API Mock Responses
<!-- Realistic mock response structures -->

**Login Response:**
```json
{
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
    "token_type": "Bearer",
    "expires_in": 3600
}
```

**OHLCV Response:**
```json
{
    "status": "success",
    "data": [
        {
            "symbol": "RELIANCE",
            "date": "2026-03-22",
            "open": 2500.00,
            "high": 2550.00,
            "low": 2480.00,
            "close": 2540.00,
            "volume": 1000000
        }
    ]
}
```

# vLLM OpenAI-Compatible Response
<!-- Chat completion endpoint structure -->

```json
{
    "id": "chatcmpl-xxx",
    "object": "chat.completion",
    "created": 1711111111,
    "model": "qwen3-30b",
    "choices": [
        {
            "index": 0,
            "message": {
                "role": "assistant",
                "content": "Based on the news analysis, the sentiment for RELIANCE is POSITIVE. Key factors: strong quarterly earnings, new manufacturing expansion, positive analyst upgrades."
            },
            "finish_reason": "stop"
        }
    ],
    "usage": {
        "prompt_tokens": 250,
        "completion_tokens": 50,
        "total_tokens": 300
    }
}
```

# Error Scenario Mocking
<!-- Test error handling with MockRestServiceServer -->

```java
@Test
void testLoginTimeout() {
    mockServer.expect(requestTo("https://api.upstox.com/v2/login"))
        .andRespond(withServiceUnavailable());

    assertThatThrownBy(() -> upstoxRestClient.login(apiKey, secret))
        .isInstanceOf(FeignException.ServiceUnavailable.class);
}

@Test
void testLoginUnauthorized() {
    mockServer.expect(requestTo("https://api.upstox.com/v2/login"))
        .andExpect(header("Authorization", "invalid"))
        .andRespond(withStatus(401));

    assertThatThrownBy(() -> upstoxRestClient.login(apiKey, secret))
        .isInstanceOf(FeignException.Unauthorized.class);
}
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create UpstoxRestClientTest with MockRestServiceServer setup</name>
  <files>data/src/test/java/com/swingtrade/data/test/UpstoxRestClientMockTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - MockRestServiceServer intercepts Upstox API calls
    - Login endpoint returns valid JWT tokens
    - Token refresh endpoint returns new access token
    - OHLCV endpoint returns realistic market data
    - Error scenarios (401, 404, 500) handled correctly
    - Mock verification ensures correct request format
  </behavior>
  <action>
Create UpstoxRestClientMockTest.java:

```java
package com.swingtrade.data.test;

import com.swingtrade.data.client.UpstoxRestClient;
import com.swingtrade.data.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.*;

@SpringBootTest
class UpstoxRestClientMockTest {

    @Autowired
    private UpstoxRestClient upstoxRestClient;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void teardown() {
        mockServer.verify();
        mockServer.reset();
    }

    @Nested
    class LoginTests {

        @Test
        void testSuccessfulLogin() {
            String loginRequest = "{\"api_key\":\"test_key\",\"password\":\"test_password\"}";
            String loginResponse = "{\"access_token\":\"jwt_token\",\"refresh_token\":\"refresh_token\",\"expires_in\":3600}";

            mockServer.expect(requestTo("https://api.upstox.com/v2/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(loginRequest))
                .andRespond(MockRestResponseCreators.withSuccess(loginResponse, MediaType.APPLICATION_JSON));

            LoginResponse response = upstoxRestClient.login("test_key", "test_password");

            assertThat(response.getAccessToken()).isEqualTo("jwt_token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
            assertThat(response.getExpiresIn()).isEqualTo(3600);
        }

        @Test
        void testLoginUnauthorized() {
            mockServer.expect(requestTo("https://api.upstox.com/v2/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> upstoxRestClient.login("invalid", "invalid"))
                .isInstanceOf(Exception.class);
        }
    }

    @Nested
    class TokenRefreshTests {

        @Test
        void testTokenRefresh() {
            String refreshTokenRequest = "{\"refresh_token\":\"old_refresh\"}";
            String refreshResponse = "{\"access_token\":\"new_jwt_token\",\"expires_in\":3600}";

            mockServer.expect(requestTo("https://api.upstox.com/v2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(refreshTokenRequest))
                .andRespond(MockRestResponseCreators.withSuccess(refreshResponse, MediaType.APPLICATION_JSON));

            TokenResponse response = upstoxRestClient.refreshToken("old_refresh");

            assertThat(response.getAccessToken()).isEqualTo("new_jwt_token");
        }
    }

    @Nested
    class OHLCVTests {

        @Test
        void testGetOHLCVData() {
            String symbol = "RELIANCE";
            String ohlcvResponse = "{\"status\":\"success\",\"data\":[{\"symbol\":\"RELIANCE\",\"date\":\"2026-03-22\",\"open\":2500.00,\"high\":2550.00,\"low\":2480.00,\"close\":2540.00,\"volume\":1000000}]}";

            mockServer.expect(requestTo("https://api.upstox.com/v2/market-data/ohlcv"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(param("symbol", "RELIANCE"))
                .andExpect(param("from", "2026-03-01"))
                .andRespond(MockRestResponseCreators.withSuccess(ohlcvResponse, MediaType.APPLICATION_JSON));

            OhlcvResponse response = upstoxRestClient.getOHLCVData(symbol, "2026-03-01", "2026-03-22");

            assertThat(response.getData()).isNotEmpty();
            assertThat(response.getData().get(0).getClose()).isEqualByComparingTo(new BigDecimal("2540.00"));
        }
    }
}
```
</action>
  <verify>
    <automated>mvn test -pl data -Dtest=UpstoxRestClientMockTest</automated>
  </verify>
  <done>UpstoxRestClientMockTest.java exists with 6+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 2: Create VLLMClientMockTest for sentiment endpoint</name>
  <files>llm/src/test/java/com/swingtrade/llm/test/VLLMClientMockTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - MockRestServiceServer intercepts vLLM chat completions
    - Successful sentiment analysis returns structured response
    - Token usage tracked correctly
    - Error scenarios (timeout, 500) handled correctly
    - OpenAI-compatible response structure validated
  </behavior>
  <action>
Create VLLMClientMockTest.java:

```java
package com.swingtrade.llm.test;

import com.swingtrade.llm.client.VLLMClient;
import com.swingtrade.llm.dto.ChatRequest;
import com.swingtrade.llm.dto.ChatResponse;
import com.swingtrade.llm.dto.SentimentAnalysisRequest;
import com.swingtrade.llm.dto.SentimentAnalysisResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest
class VLLMClientMockTest {

    @Autowired
    private VLLMClient vllmClient;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void teardown() {
        mockServer.verify();
        mockServer.reset();
    }

    @Nested
    class SentimentAnalysisTests {

        @Test
        void testSuccessfulSentimentAnalysis() {
            String prompt = "Analyze sentiment for RELIANCE news...";
            String vllmResponse = "{\n" +
                "    \"choices\": [{\n" +
                "        \"message\": {\n" +
                "            \"content\": \"{\\\"score\\\":\\\"POSITIVE\\\",\\\"reasoning\\\":\\\"Strong earnings\\\"}\"\n" +
                "        }\n" +
                "    }]\n" +
                "}";

            mockServer.expect(requestTo("http://localhost:8000/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"prompt\":\"\"}"))
                .andRespond(MockRestResponseCreators.withSuccess(vllmResponse, MediaType.APPLICATION_JSON));

            SentimentAnalysisResponse response = vllmClient.analyzeSentiment("RELIANCE", prompt);

            assertThat(response.getScore()).isEqualTo("POSITIVE");
            assertThat(response.getReasoning()).isEqualTo("Strong earnings");
        }

        @Test
        void testSentimentTimeout() {
            mockServer.expect(requestTo("http://localhost:8000/v1/chat/completions"))
                .andRespond(MockRestResponseCreators.withServiceUnavailable());

            assertThatThrownBy(() -> vllmClient.analyzeSentiment("RELIANCE", "prompt"))
                .isInstanceOf(Exception.class);
        }
    }
}
```
</action>
  <verify>
    <automated>mvn test -pl llm -Dtest=VLLMClientMockTest</automated>
  </verify>
  <done>VLLMClientMockTest.java exists with 4+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 3: Create mock response JSON files</name>
  <files>
    data/src/test/resources/mock-upstox-login-response.json,
    data/src/test/resources/mock-upstox-ohlcv-response.json,
    llm/src/test/resources/mock-vllm-sentiment-response.json
  </files>
  <action>
Create mock response files for easier test maintenance:

**mock-upstox-login-response.json:**
```json
{
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test",
    "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4",
    "token_type": "Bearer",
    "expires_in": 3600
}
```

**mock-upstox-ohlcv-response.json:**
```json
{
    "status": "success",
    "data": [
        {
            "symbol": "RELIANCE",
            "date": "2026-03-22",
            "open": 2500.00,
            "high": 2550.00,
            "low": 2480.00,
            "close": 2540.00,
            "volume": 1000000
        },
        {
            "symbol": "RELIANCE",
            "date": "2026-03-21",
            "open": 2480.00,
            "high": 2520.00,
            "low": 2470.00,
            "close": 2500.00,
            "volume": 950000
        }
    ]
}
```

**mock-vllm-sentiment-response.json:**
```json
{
    "id": "chatcmpl-xxx",
    "object": "chat.completion",
    "created": 1711111111,
    "model": "qwen3-30b",
    "choices": [
        {
            "index": 0,
            "message": {
                "role": "assistant",
                "content": "{\n    \"score\": \"POSITIVE\",\n    \"reasoning\": \"Strong quarterly earnings, positive analyst upgrades, new manufacturing expansion announced.\"\n}"
            },
            "finish_reason": "stop"
        }
    ],
    "usage": {
        "prompt_tokens": 250,
        "completion_tokens": 50,
        "total_tokens": 300
    }
}
```
</action>
  <verify>
    <automated>cat data/src/test/resources/mock-upstox-login-response.json && cat data/src/test/resources/mock-upstox-ohlcv-response.json && cat llm/src/test/resources/mock-vllm-sentiment-response.json</automated>
  </verify>
  <done>Mock response files created in test resources</done>
</task>

</tasks>

<verification>
Overall checks:
1. Run `mvn test -pl data -Dtest=UpstoxRestClientMockTest` to verify Upstox mocking
2. Run `mvn test -pl llm -Dtest=VLLMClientMockTest` to verify vLLM mocking
3. Verify no WireMock usage (check imports)
4. Ensure MockRestServiceServer correctly intercepts HTTP calls
</verification>

<success_criteria>
- UpstoxRestClientMockTest.java with 6+ tests (login, refresh, OHLCV)
- VLLMClientMockTest.java with 4+ tests (sentiment, errors)
- 3 mock response JSON files in test resources
- All tests pass with `mvn test`
- NO WireMock usage (only MockRestServiceServer)
- Error scenarios tested (401, 404, 500, timeout)
</success_criteria>

<output>
After completion, create `.planning/phases/05-testing-foundation/05-04-http-mocking-with-MockRestServiceServer-SUMMARY.md`
</output>
