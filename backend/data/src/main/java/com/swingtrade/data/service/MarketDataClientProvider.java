package com.swingtrade.data.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the active MarketDataClient at runtime based on user-selected broker.
 * Default is Yahoo Finance (no auth required). Users can switch to fyers/upstox
 * via the /api/settings endpoint.
 */
@Service
public class MarketDataClientProvider {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataClientProvider.class);
    private static final String FYERS = "fyers";
    private static final String SELECTED_BROKER_SETTING = "selectedBroker";

    private final Map<String, MarketDataClient> clients;
    private final AppSettingsService appSettingsService;
    private final AtomicReference<String> activeBroker;

    @Autowired
    public MarketDataClientProvider(Map<String, MarketDataClient> clients, AppSettingsService appSettingsService) {
        this.clients = normalizeClientNames(clients);
        this.appSettingsService = appSettingsService;
        String configuredBroker = appSettingsService.get(SELECTED_BROKER_SETTING, FYERS);
        this.activeBroker = new AtomicReference<>(this.clients.containsKey(configuredBroker) ? configuredBroker : FYERS);
        logger.info("MarketDataClientProvider initialized with: {}", this.clients.keySet());
        logger.info("Active market data provider: {}", activeBroker.get());
    }

    /** Compatibility constructor for lightweight tests. */
    public MarketDataClientProvider(Map<String, MarketDataClient> clients) {
        this.clients = normalizeClientNames(clients);
        this.appSettingsService = null;
        this.activeBroker = new AtomicReference<>(this.clients.containsKey(FYERS) ? FYERS : "yahoo");
    }

    private static Map<String, MarketDataClient> normalizeClientNames(Map<String, MarketDataClient> clients) {
        Map<String, MarketDataClient> normalized = new HashMap<>(clients);
        MarketDataClient fyersClient = normalized.get("fyersServiceClient");
        if (fyersClient != null) {
            normalized.putIfAbsent(FYERS, fyersClient);
        }
        return normalized;
    }

    /**
     * Rehydrates the active broker from the persisted setting at startup. Without
     * this, a restart always resets to "yahoo" in memory while the DB still says
     * (e.g.) "fyers" - GET /settings then reports the wrong broker, and the
     * dashboard's next save writes "yahoo" back over the user's real selection.
     */
    @PostConstruct
    void restorePersistedBroker() {
        if (appSettingsService == null) {
            return;
        }
        String persisted = appSettingsService.get(SELECTED_BROKER_SETTING, null);
        if (persisted != null && !persisted.equals(activeBroker.get())) {
            setActiveBroker(persisted);
        }
    }

    public MarketDataClient getClient() {
        String broker = activeBroker.get();
        MarketDataClient client = clients.get(broker);
        if (client == null) {
            throw new IllegalStateException("""
                Market data provider '%s' is unavailable; authentication/configuration is
                required and no fallback provider is enabled
                """.formatted(broker));
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
        if (appSettingsService != null) {
            appSettingsService.set(SELECTED_BROKER_SETTING, broker);
        }
    }
}
