package com.swingtrade.data.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MarketDataClientProviderTest {

    private MarketDataClient yahoo;
    private MarketDataClient fyers;
    private MarketDataClientProvider provider;

    @BeforeEach
    void setUp() {
        yahoo = mock(MarketDataClient.class);
        fyers = mock(MarketDataClient.class);
        provider = new MarketDataClientProvider(Map.of("yahoo", yahoo, "fyers", fyers));
    }

    @Test
    void defaultsToFyers() {
        assertThat(provider.getActiveBroker()).isEqualTo("fyers");
        assertThat(provider.getClient()).isSameAs(fyers);
    }

    @Test
    void switchesActiveBroker() {
        provider.setActiveBroker("fyers");

        assertThat(provider.getActiveBroker()).isEqualTo("fyers");
        assertThat(provider.getClient()).isSameAs(fyers);
    }

    @Test
    void unknownBrokerIsRejectedAndKeepsPreviousActive() {
        provider.setActiveBroker("fyers");
        provider.setActiveBroker("upstox"); // not registered in this map

        assertThat(provider.getActiveBroker()).isEqualTo("fyers");
        assertThat(provider.getClient()).isSameAs(fyers);
    }
}
