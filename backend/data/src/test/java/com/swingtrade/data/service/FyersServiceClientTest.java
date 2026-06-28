package com.swingtrade.data.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FyersServiceClientTest {

    private MockWebServer mockWebServer;
    private FyersAuthService mockAuthService;
    private FyersServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        mockAuthService = mock(FyersAuthService.class);
        when(mockAuthService.getAccessToken()).thenReturn("test-fyers-token");

        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        client = new FyersServiceClient(builder, mockAuthService);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void parsesCandleArrayResponse() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                  "s": "success",
                  "candles": [
                    [1705272600, 2500.0, 2550.5, 2490.0, 2530.0, 5000000],
                    [1704927000, 2450.0, 2510.0, 2440.0, 2500.0, 4800000]
                  ]
                }
                """)
            .addHeader("Content-Type", "application/json")
        );

        List<CandleData> candles = client.fetchCandlesList("RELIANCE",
            LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 15));

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).symbol()).isEqualTo("RELIANCE");
        assertThat(candles.get(0).open()).isEqualByComparingTo(new BigDecimal("2500.0"));
        assertThat(candles.get(0).high()).isEqualByComparingTo(new BigDecimal("2550.5"));
        assertThat(candles.get(0).low()).isEqualByComparingTo(new BigDecimal("2490.0"));
        assertThat(candles.get(0).close()).isEqualByComparingTo(new BigDecimal("2530.0"));
        assertThat(candles.get(0).volume()).isEqualTo(5000000L);
    }

    @Test
    void fetchCandleReturnsSingleDayCandle() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {"s":"success","candles":[
                  [1705272600,2500.0,2550.5,2490.0,2530.0,5000000]
                ]}
                """)
            .addHeader("Content-Type", "application/json")
        );

        CandleData candle = client.fetchCandle("RELIANCE", LocalDate.of(2024, 1, 15));

        assertThat(candle).isNotNull();
        assertThat(candle.symbol()).isEqualTo("RELIANCE");
    }

    @Test
    void fetchCandleReturnsEmptyWhenNoToken() {
        when(mockAuthService.getAccessToken()).thenReturn(null);

        List<CandleData> candles = client.fetchCandlesList("RELIANCE",
            LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 15));

        assertThat(candles).isEmpty();
    }

    @Test
    void fetchCandleReturnsEmptyWhenApiReturnsError() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"s\":\"error\",\"message\":\"Invalid symbol\"}")
            .addHeader("Content-Type", "application/json")
        );

        List<CandleData> candles = client.fetchCandlesList("INVALID",
            LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 15));

        assertThat(candles).isEmpty();
    }

    @Test
    void fetchCandleReturnsEmptyWhenApiReturnsEmptyCandles() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"s\":\"success\",\"candles\":[]}")
            .addHeader("Content-Type", "application/json")
        );

        CandleData candle = client.fetchCandle("RELIANCE", LocalDate.of(2024, 1, 15));
        assertThat(candle).isNull();
    }

    @Test
    void requestIncludesTokenAuthHeader() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {"s":"success","candles":[
                  [1705272600,100.0,110.0,90.0,105.0,1000]
                ]}
                """)
            .addHeader("Content-Type", "application/json")
        );

        client.fetchCandle("RELIANCE", LocalDate.of(2024, 1, 15));

        var recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("token test-fyers-token");
    }

    @Test
    void fetchesCorrectFyersSymbolFormat() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"s\":\"success\",\"candles\":[]}")
            .addHeader("Content-Type", "application/json")
        );

        client.fetchCandle("RELIANCE", LocalDate.of(2024, 1, 15));

        var request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("symbol=NSE:RELIANCE-EQ");
        assertThat(request.getPath()).contains("resolution=D");
    }

    @Test
    void isConnectedDelegatesToAuthService() {
        when(mockAuthService.validateToken()).thenReturn(true);
        assertThat(client.isConnected()).isTrue();

        when(mockAuthService.validateToken()).thenReturn(false);
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void fetchLatestCandleReturnsMostRecent() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                  "s": "success",
                  "candles": [
                    [1705272600, 2500.0, 2550.5, 2490.0, 2530.0, 5000000],
                    [1705359000, 2520.0, 2560.0, 2510.0, 2545.0, 5200000]
                  ]
                }
                """)
            .addHeader("Content-Type", "application/json")
        );

        CandleData latest = client.fetchLatestCandle("RELIANCE");

        assertThat(latest).isNotNull();
        assertThat(latest.close()).isEqualByComparingTo(new BigDecimal("2545.0"));
    }

    @Test
    void fetchInstrumentDetailsReturnsFallback() {
        InstrumentDetails details = client.fetchInstrumentDetails("RELIANCE");

        assertThat(details).isNotNull();
        assertThat(details.symbol()).isEqualTo("RELIANCE");
    }

    @Test
    void fetchAllStockSymbolsReturnsEmpty() {
        assertThat(client.fetchAllStockSymbols()).isEmpty();
    }
}
