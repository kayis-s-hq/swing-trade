package com.swingtrade.broker.factory;

import com.swingtrade.broker.config.BrokerMode;
import com.swingtrade.broker.kite.KiteConnectClient;
import com.swingtrade.broker.risk.RiskControlsService;
import com.swingtrade.broker.service.BrokerService;
import com.swingtrade.broker.service.PaperTradingServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Factory for creating the appropriate BrokerService implementation
 * based on the configured broker mode.
 */
@Component
public class BrokerServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(BrokerServiceFactory.class);

    private final PaperTradingServiceImpl paperTradingService;
    private final KiteConnectClient kiteConnectClient;
    private final RiskControlsService riskControlsService;

    @Value("${broker.mode:paper}")
    private String brokerModeString;

    private BrokerMode currentMode;
    private BrokerService currentService;

    @Autowired
    public BrokerServiceFactory(PaperTradingServiceImpl paperTradingService,
                                KiteConnectClient kiteConnectClient,
                                RiskControlsService riskControlsService) {
        this.paperTradingService = paperTradingService;
        this.kiteConnectClient = kiteConnectClient;
        this.riskControlsService = riskControlsService;

        initialize();
    }

    /**
     * Initialize the broker service based on configuration.
     */
    public void initialize() {
        currentMode = BrokerMode.fromString(brokerModeString);
        currentService = createService(currentMode);

        logger.info("BrokerServiceFactory initialized with mode: {} ({})",
                currentMode, currentMode.getDescription());
    }

    /**
     * Create the appropriate broker service for the given mode.
     */
    private BrokerService createService(BrokerMode mode) {
        logger.info("Creating BrokerService for mode: {}", mode);

        switch (mode) {
            case PAPER:
                logger.info("Using Paper Trading Service");
                return paperTradingService;

            case LIVE:
                logger.info("Using Kite Connect Live Trading Service");
                return createLiveTradingService();

            case DRY_RUN:
                logger.info("Using Dry-Run Service (logging only)");
                return createDryRunService();

            default:
                logger.warn("Unknown mode {}, defaulting to PAPER", mode);
                return paperTradingService;
        }
    }

    /**
     * Create live trading service wrapper.
     */
    private BrokerService createLiveTradingService() {
        // Validate Kite Connect configuration
        if (!kiteConnectClient.isConfigured()) {
            logger.error("Kite Connect not configured. Cannot create live trading service.");
            throw new IllegalStateException("Kite Connect API key not configured. " +
                    "Set kite.api-key in application properties.");
        }

        if (kiteConnectClient.getAccessToken() == null || kiteConnectClient.getAccessToken().isEmpty()) {
            logger.warn("Kite Connect access token not set. Live trading will fail until authorized.");
        }

        return new LiveTradingService(kiteConnectClient, riskControlsService);
    }

    /**
     * Create dry-run service wrapper.
     */
    private BrokerService createDryRunService() {
        return new DryRunService(kiteConnectClient, riskControlsService);
    }

    /**
     * Get the current broker service.
     */
    public BrokerService getService() {
        return currentService;
    }

    /**
     * Get the current broker mode.
     */
    public BrokerMode getMode() {
        return currentMode;
    }

    /**
     * Switch to a different broker mode.
     *
     * @param newMode the new mode to switch to
     */
    public void switchMode(BrokerMode newMode) {
        logger.info("Switching broker mode from {} to {}", currentMode, newMode);

        currentMode = newMode;
        currentService = createService(newMode);

        logger.info("Broker mode switched to: {} ({})",
                currentMode, currentMode.getDescription());
    }

    /**
     * Switch to a different broker mode by string.
     *
     * @param modeString the new mode string (paper, live, dry_run)
     */
    public void switchMode(String modeString) {
        switchMode(BrokerMode.fromString(modeString));
    }

    /**
     * Check if the current mode allows actual order execution.
     */
    public boolean allowsExecution() {
        return currentMode.allowsExecution();
    }

    /**
     * Check if the current mode is safe for testing.
     */
    public boolean isSafeMode() {
        return currentMode.isSafeMode();
    }

    /**
     * Get current mode configuration property.
     */
    public String getModeString() {
        return brokerModeString;
    }

    /**
     * Update the mode configuration property.
     */
    public void setModeString(String modeString) {
        this.brokerModeString = modeString;
    }
}
