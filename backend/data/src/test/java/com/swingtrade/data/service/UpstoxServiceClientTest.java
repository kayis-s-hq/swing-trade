package com.swingtrade.data.service;

// Upstox integration is unconfigured — the whole implementation is commented out
// (kept in place, not deleted, so it can be restored when Upstox is configured again).
//
// import okhttp3.mockwebserver.MockResponse;
// import okhttp3.mockwebserver.MockWebServer;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.web.reactive.function.client.WebClient;
//
// import java.io.IOException;
// import java.math.BigDecimal;
// import java.time.LocalDate;
// import java.util.List;
// import java.util.Optional;
//
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.when;
//
// class UpstoxServiceClientTest {
//
//     private MockWebServer mockWebServer;
//     private UpstoxAuthService mockAuthService;
//     private NseInstrumentService mockInstrumentService;
//     private UpstoxServiceClient client;
//
//     @BeforeEach
//     void setUp() throws IOException {
//         mockWebServer = new MockWebServer();
//         mockWebServer.start();
//
//         mockAuthService = mock(UpstoxAuthService.class);
//         when(mockAuthService.getAccessToken()).thenReturn("test-bearer-token");
//
//         mockInstrumentService = mock(NseInstrumentService.class);
//         when(mockInstrumentService.getEncodedInstrumentKey("RELIANCE"))
//             .thenReturn(Optional.of("NSE_EQ%7CINE002A01018"));
//         when(mockInstrumentService.getEncodedInstrumentKey("UNKNOWN"))
//             .thenReturn(Optional.empty());
//
//         String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
//         WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
//         client = new UpstoxServiceClient(builder, mockAuthService, mockInstrumentService, baseUrl);
//     }
//
//     @AfterEach
//     void tearDown() throws IOException {
//         mockWebServer.shutdown();
//     }
//
//     @Test
//     void parsesHistoricalCandleArrayResponse() {
//         mockWebServer.enqueue(new MockResponse()
//             .setBody("""
//                 {
//                   "status": "success",
//                   "data": {
//                     "candles": [
//                       ["2024-01-15T00:00:00+05:30", 2500.0, 2550.5, 2490.0, 2530.0, 5000000, 0],
//                       ["2024-01-12T00:00:00+05:30", 2450.0, 2510.0, 2440.0, 2500.0, 4800000, 0]
//                     ]
//                   }
//                 }
//                 """)
//             .addHeader("Content-Type", "application/json")
//         );
//
//         List<CandleData> candles = client.fetchCandlesList("RELIANCE",
//             LocalDate.of(2024, 1, 12), LocalDate.of(2024, 1, 15));
//
//         assertThat(candles).hasSize(2);
//         // Candles should be in chronological order (oldest first — API returns newest first, we reverse)
//         assertThat(candles.get(0).date()).isEqualTo(LocalDate.of(2024, 1, 12));
//         assertThat(candles.get(0).open()).isEqualByComparingTo(new BigDecimal("2450.0"));
//         assertThat(candles.get(0).high()).isEqualByComparingTo(new BigDecimal("2510.0"));
//         assertThat(candles.get(0).low()).isEqualByComparingTo(new BigDecimal("2440.0"));
//         assertThat(candles.get(0).close()).isEqualByComparingTo(new BigDecimal("2500.0"));
//         assertThat(candles.get(0).volume()).isEqualTo(4800000L);
//         assertThat(candles.get(1).date()).isEqualTo(LocalDate.of(2024, 1, 15));
//     }
//
//     @Test
//     void fetchCandleReturnsSingleDayCandle() {
//         mockWebServer.enqueue(new MockResponse()
//             .setBody("""
//                 {"status":"success","data":{"candles":[
//                   ["2024-01-15T00:00:00+05:30",2500.0,2550.5,2490.0,2530.0,5000000,0]
//                 ]}}
//                 """)
//             .addHeader("Content-Type", "application/json")
//         );
//
//         CandleData candle = client.fetchCandle("RELIANCE", LocalDate.of(2024, 1, 15));
//
//         assertThat(candle).isNotNull();
//         assertThat(candle.date()).isEqualTo(LocalDate.of(2024, 1, 15));
//         assertThat(candle.symbol()).isEqualTo("RELIANCE");
//     }
//
//     @Test
//     void fetchCandleReturnsNullForUnknownSymbol() {
//         CandleData candle = client.fetchCandle("UNKNOWN", LocalDate.of(2024, 1, 15));
//         assertThat(candle).isNull();
//     }
//
//     @Test
//     void fetchCandleReturnsNullWhenApiReturnsEmptyCandles() {
//         mockWebServer.enqueue(new MockResponse()
//             .setBody("""
//                 {"status":"success","data":{"candles":[]}}
//                 """)
//             .addHeader("Content-Type", "application/json")
//         );
//
//         CandleData candle = client.fetchCandle("RELIANCE", LocalDate.of(2024, 1, 15));
//         assertThat(candle).isNull();
//     }
//
//     @Test
//     void requestIncludesBearerAuthHeader() throws InterruptedException {
//         mockWebServer.enqueue(new MockResponse()
//             .setBody("""
//                 {"status":"success","data":{"candles":[
//                   ["2024-01-15T00:00:00+05:30",100.0,110.0,90.0,105.0,1000,0]
//                 ]}}
//                 """)
//             .addHeader("Content-Type", "application/json")
//         );
//
//         client.fetchCandle("RELIANCE", LocalDate.of(2024, 1, 15));
//
//         var recordedRequest = mockWebServer.takeRequest();
//         assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-bearer-token");
//     }
// }
