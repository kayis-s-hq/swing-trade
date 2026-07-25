package com.swingtrade.broker.kite;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.model.*;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for KiteConnectClient.
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class KiteConnectClientTest {

    private KiteConnectClient client;
    private KiteConnectClient liveClient;
    private KiteConnectClient emptyClient;

    @BeforeEach
    void setUp() {
        // Create sandbox config
        BrokerProperties props = new BrokerProperties();
        props.getKite().setApiKey("test_api_key_12345");
        props.getKite().setAccessToken("test_access_token_67890");
        props.getKite().setEnvironment("sandbox");
        KiteConfig config = new KiteConfig(props);

        // Create the client
        client = new KiteConnectClient(config, null);

        // Create live config
        BrokerProperties liveProps = new BrokerProperties();
        liveProps.getKite().setApiKey("live_api_key_xyz");
        liveProps.getKite().setAccessToken("live_access_token_123");
        liveProps.getKite().setEnvironment("live");
        KiteConfig liveConfig = new KiteConfig(liveProps);

        liveClient = new KiteConnectClient(liveConfig, null);

        // Create empty config
        emptyClient = new KiteConnectClient(new KiteConfig(new BrokerProperties()), null);
    }

    @Test
    void testConstructor_WithConfig() {
        assertThat(client).isNotNull();
        assertThat(client.getAccessToken()).isEqualTo("test_access_token_67890");
    }

    @Test
    void testConstructor_WithEmptyApiKey() {
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
    void testPlaceOrder_configured() {
        OrderResponse order = new OrderResponse();
        order.setSymbol("RELIANCE-EQ");
        order.setExchange(Exchange.NSE);
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("100"));
        order.setType(OrderType.MARKET);

        OrderResponse response = client.placeOrder(order);

        // Should fail because no actual API call but not throw exception
        assertThat(response).isNotNull();
    }

    @Test
    void testPlaceMarketOrder() {
        String symbol = "TCS-EQ";
        BigDecimal quantity = new BigDecimal("50");

        OrderResponse response = client.placeMarketOrder(symbol, Exchange.NSE, TradeDirection.LONG, quantity);

        assertThat(response.getSymbol()).isEqualTo(symbol);
        assertThat(response.getType()).isEqualTo(OrderType.MARKET);
        assertThat(response.getQuantity()).isEqualTo(quantity);
    }

    @Test
    void testPlaceLimitOrder() {
        String symbol = "HDFC-EQ";
        BigDecimal quantity = new BigDecimal("25");
        BigDecimal limitPrice = new BigDecimal("1500");

        OrderResponse response = client.placeLimitOrder(symbol, Exchange.NSE, TradeDirection.LONG, quantity, limitPrice);

        assertThat(response.getSymbol()).isEqualTo(symbol);
        assertThat(response.getType()).isEqualTo(OrderType.LIMIT);
        assertThat(response.getQuantity()).isEqualTo(quantity);
        assertThat(response.getLimitPrice()).isEqualTo(limitPrice);
    }

    @Test
    void testPlaceStopLossMarketOrder() {
        String symbol = "INFY-EQ";
        BigDecimal quantity = new BigDecimal("75");
        BigDecimal stopPrice = new BigDecimal("1300");

        OrderResponse response = client.placeStopLossMarketOrder(symbol, Exchange.NSE, TradeDirection.LONG, quantity, stopPrice);

        assertThat(response.getSymbol()).isEqualTo(symbol);
        assertThat(response.getType()).isEqualTo(OrderType.STOP_LOSS);
        assertThat(response.getQuantity()).isEqualTo(quantity);
        assertThat(response.getStopPrice()).isEqualTo(stopPrice);
    }

    @Test
    void testCancelOrder() {
        // Should return false since no actual API
        boolean result = client.cancelOrder("ORD123456");
        assertThat(result).isFalse();
    }

    @Test
    void testModifyOrder() {
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
    void testGetOrderHistory_emptyList() {
        List<com.swingtrade.broker.model.OrderResponse> history = client.getOrderHistory();
        assertThat(history).isEmpty();
    }

    @Test
    void testTestConnection() {
        // Returns false since no actual API call
        boolean result = client.testConnection();
        assertThat(result).isFalse();
    }

    @Test
    void testDefaultKiteConfig() {
        BrokerProperties.Kite kite = new BrokerProperties().getKite();
        assertThat(kite.getApiKey() == null || kite.getApiKey().isEmpty()).isTrue();
        assertThat(kite.isConfigured()).isFalse();
        assertThat(kite.isSandbox()).isFalse();
        // Default environment is "live", so isLive() returns true
        assertThat(kite.isLive()).isTrue();
    }

    @Test
    void testSetAndGetEnvironment() {
        BrokerProperties.Kite kite = new BrokerProperties().getKite();
        kite.setEnvironment("live");
        assertThat(kite.getEnvironment()).isEqualTo("live");
        assertThat(kite.isLive()).isTrue();

        kite.setEnvironment("sandbox");
        assertThat(kite.getEnvironment()).isEqualTo("sandbox");
        assertThat(kite.isSandbox()).isTrue();
    }

    @Test
    void testSetAndGetProxyConfig() {
        BrokerProperties.Kite kite = new BrokerProperties().getKite();
        kite.setProxyHost("proxy.example.com");
        kite.setProxyPort(8080);

        assertThat(kite.getProxyHost()).isEqualTo("proxy.example.com");
        assertThat(kite.getProxyPort()).isEqualTo(8080);
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