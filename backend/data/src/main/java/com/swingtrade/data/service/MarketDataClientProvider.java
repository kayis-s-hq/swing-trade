package com.swingtrade.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the active MarketDataClient at runtime based on user-selected broker.
 * Default is Yahoo Finance (no auth required). Users can switch to fyers/upstox
 * via the /api/settings endpoint.
 */
@Service
public class MarketDataClientProvider {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataClientProvider.class);

    private final Map<String, MarketDataClient> clients;
    private final AtomicReference<String> activeBroker = new AtomicReference<>("yahoo");

    public MarketDataClientProvider(Map<String, MarketDataClient> clients) {
        this.clients = clients;
        logger.info("MarketDataClientProvider initialized with: {}", clients.keySet());
    }

    public MarketDataClient getClient() {
        String broker = activeBroker.get();
        MarketDataClient client = clients.get(broker);
        if (client == null) {
            logger.warn("No MarketDataClient for broker '{}', falling back to yahoo", broker);
            client = clients.get("yahoo");
        }
        if (client == null) {
            throw new IllegalStateException("No MarketDataClient available");
        }
        return client;
    }

    public String getActiveBroker() {
        return activeBroker.get();
    }

    public void setActiveBroker(String broker) {
        if (!clients.containsKey(broker)) {
            logger.warn("Cannot set broker to '{}', available: {}", broker, clients.keySet());
            return;
        }
        logger.info("Switched data source to '{}'", broker);
        activeBroker.set(broker);
    }
}
