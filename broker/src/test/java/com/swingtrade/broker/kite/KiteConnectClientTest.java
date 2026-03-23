package com.swingtrade.broker.kite;

import com.swingtrade.broker.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for KiteConnectClient.
 * Tests the stub implementation that is used when Kite SDK is not available.
 */
@ExtendWith(MockitoExtension.class)
class KiteConnectClientTest {

    private KiteConnectClient client;
    private KiteConnectClient liveClient;
    private KiteConnectClient emptyClient;

    @BeforeEach
    void setUp() {
        // Create sandbox config
        KiteConfig config = new KiteConfig();
        config.setApiKey("test_api_key_12345");
        config.setAccessToken("test_access_token_67890");
        config.setEnvironment("sandbox");

        // Create the client
        client = new KiteConnectClient(config);

        // Create live config
        KiteConfig liveConfig = new KiteConfig();
        liveConfig.setApiKey("live_api_key_xyz");
        liveConfig.setAccessToken("live_access_token_123");
        liveConfig.setEnvironment("live");

        liveClient = new KiteConnectClient(liveConfig);

        // Create empty config
        KiteConfig emptyConfig = new KiteConfig();

        emptyClient = new KiteConnectClient(emptyConfig);
    }

    @Test
    void testConstructor_WithConfig() {
        assertThat(client).isNotNull();
        assertThat(client.getAccessToken()).isEqualTo("test_access_token_67890");
    }

    @Test
    void testConstructor_WithEmptyApiKey() {
        KiteConfig emptyConfig = new KiteConfig();
        KiteConnectClient emptyClient = new KiteConnectClient(emptyConfig);
        assertThat(emptyClient).isNotNull();
        assertThat(emptyClient.isConfigured()).isFalse();
    }

    @Test
    void testSetAccessToken_success() {
        String newAccessToken = "new_access_token_xyz";
        client.setAccessToken(newAccessToken);
        assertThat(client.getAccessToken()).isEqualTo("new_access_token_xyz");
    }

    @Test
    void testGetAccessToken_initial() {
        assertThat(client.getAccessToken()).isEqualTo("test_access_token_67890");
    }

    @Test
    void testIsConfigured_true() {
        assertThat(client.isConfigured()).isTrue();
        assertThat(liveClient.isConfigured()).isTrue();
    }

    @Test
    void testIsConfigured_false() {
        KiteConfig emptyConfig = new KiteConfig();
        KiteConnectClient emptyClient = new KiteConnectClient(emptyConfig);
        assertThat(emptyClient.isConfigured()).isFalse();
    }

    @Test
    void testGenerateLoginUrl() {
        String loginUrl = client.generateLoginUrl();
        assertThat(loginUrl).contains("https://kite.zerodha.com/connect/login?api_key=test_api_key_12345");
    }

    @Test
    void testGenerateLoginUrl_WithLiveClient() {
        String loginUrl = liveClient.generateLoginUrl();
        assertThat(loginUrl).contains("https://kite.zerodha.com/connect/login?api_key=live_api_key_xyz");
    }

    @Test
    void testPlaceOrder_stubMode() {
        OrderResponse order = new OrderResponse();
        order.setSymbol("RELIANCE-EQ");
        order.setExchange(Exchange.NSE);
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("100"));
        order.setType(OrderType.MARKET);

        OrderResponse response = client.placeOrder(order);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getMessage()).contains("Kite Connect SDK not available");
    }

    @Test
    void testPlaceMarketOrder_stubMode() {
        String symbol = "TCS-EQ";
        BigDecimal quantity = new BigDecimal("50");

        OrderResponse response = client.placeMarketOrder(symbol, Exchange.NSE, TradeDirection.LONG, quantity);

        assertThat(response.getSymbol()).isEqualTo(symbol);
        assertThat(response.getType()).isEqualTo(OrderType.MARKET);
        assertThat(response.getQuantity()).isEqualTo(quantity);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void testPlaceLimitOrder_stubMode() {
        String symbol = "HDFC-EQ";
        BigDecimal quantity = new BigDecimal("25");
        BigDecimal limitPrice = new BigDecimal("1500");

        OrderResponse response = client.placeLimitOrder(symbol, Exchange.NSE, TradeDirection.LONG, quantity, limitPrice);

        assertThat(response.getSymbol()).isEqualTo(symbol);
        assertThat(response.getType()).isEqualTo(OrderType.LIMIT);
        assertThat(response.getQuantity()).isEqualTo(quantity);
        assertThat(response.getLimitPrice()).isEqualTo(limitPrice);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void testPlaceStopLossMarketOrder_stubMode() {
        String symbol = "INFY-EQ";
        BigDecimal quantity = new BigDecimal("75");
        BigDecimal stopPrice = new BigDecimal("1300");

        OrderResponse response = client.placeStopLossMarketOrder(symbol, Exchange.NSE, TradeDirection.LONG, quantity, stopPrice);

        assertThat(response.getSymbol()).isEqualTo(symbol);
        assertThat(response.getType()).isEqualTo(OrderType.STOP_LOSS);
        assertThat(response.getQuantity()).isEqualTo(quantity);
        assertThat(response.getStopPrice()).isEqualTo(stopPrice);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void testCancelOrder_stubMode() {
        boolean result = client.cancelOrder("ORD123456");
        assertThat(result).isFalse();
    }

    @Test
    void testModifyOrder_stubMode() {
        boolean result = client.modifyOrder("ORD123456", Exchange.NSE, "RELIANCE-EQ",
                new BigDecimal("150"), new BigDecimal("2600"));
        assertThat(result).isFalse();
    }

    @Test
    void testGetPositions_emptyList() {
        List<Position> positions = client.getPositions();
        assertThat(positions).isEmpty();
    }

    @Test
    void testGetPosition_notFound() {
        var position = client.getPosition("RELIANCE-EQ");
        assertThat(position).isEmpty();
    }

    @Test
    void testGetPortfolio_null() {
        Portfolio portfolio = client.getPortfolio();
        assertThat(portfolio).isNull();
    }

    @Test
    void testGetOrderHistory_emptyList() {
        List<OrderResponse> history = client.getOrderHistory();
        assertThat(history).isEmpty();
    }

    @Test
    void testGetMarketPrice_null() {
        BigDecimal price = client.getMarketPrice("RELIANCE-EQ", Exchange.NSE);
        assertThat(price).isNull();
    }

    @Test
    void testGetMarketPrices_emptyMap() {
        List<String> symbols = List.of("TCS-EQ", "HDFC-EQ", "INFY-EQ");
        Map<String, BigDecimal> prices = client.getMarketPrices(symbols, Exchange.NSE);
        assertThat(prices).isEmpty();
    }

    @Test
    void testTestConnection_stubMode() {
        boolean result = client.testConnection();
        assertThat(result).isFalse();
    }

    @Test
    void testKiteConfigIsSandbox_true() {
        KiteConfig config = client.getKiteConfig();
        assertThat(config.isSandbox()).isTrue();
        assertThat(config.isLive()).isFalse();
    }

    @Test
    void testKiteConfigIsLive_true() {
        KiteConfig config = liveClient.getKiteConfig();
        assertThat(config.isLive()).isTrue();
        assertThat(config.isSandbox()).isFalse();
    }

    @Test
    void testKiteConfigIsLive_false() {
        KiteConfig config = client.getKiteConfig();
        assertThat(config.isLive()).isFalse();
    }

    @Test
    void testKiteConfigIsSandbox_false() {
        KiteConfig config = liveClient.getKiteConfig();
        assertThat(config.isSandbox()).isFalse();
    }

    @Test
    void testGetApiKeyPrefix() {
        KiteConfig config = client.getKiteConfig();
        String apiKey = config.getApiKey();
        String prefix = apiKey.substring(0, Math.min(8, apiKey.length()));
        assertThat(prefix).isEqualTo("test_api");
    }

    @Test
    void testDefaultKiteConfig() {
        KiteConfig config = new KiteConfig();
        assertThat(config.getApiKey()).isEmpty();
        assertThat(config.isConfigured()).isFalse();
        assertThat(config.isSandbox()).isFalse();
        // Default environment is "live", so isLive() returns true
        assertThat(config.isLive()).isTrue();
    }

    @Test
    void testSetAndGetEnvironment() {
        KiteConfig config = new KiteConfig();
        config.setEnvironment("live");
        assertThat(config.getEnvironment()).isEqualTo("live");
        assertThat(config.isLive()).isTrue();

        config.setEnvironment("sandbox");
        assertThat(config.getEnvironment()).isEqualTo("sandbox");
        assertThat(config.isSandbox()).isTrue();
    }

    @Test
    void testSetAndGetProxyConfig() {
        KiteConfig config = new KiteConfig();
        config.setProxyHost("proxy.example.com");
        config.setProxyPort(8080);

        assertThat(config.getProxyHost()).isEqualTo("proxy.example.com");
        assertThat(config.getProxyPort()).isEqualTo(8080);
    }

    @Test
    void testBrokerClientIsConfigured() {
        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    void testBrokerClientGetAccessToken() {
        assertThat(client.getAccessToken()).isEqualTo("test_access_token_67890");
    }
}
