package com.swingtrade.broker.factory;

import com.swingtrade.broker.config.BrokerMode;
import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.kite.BrokerClient;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.risk.KillSwitchService;
import com.swingtrade.broker.risk.RiskControls;
import com.swingtrade.broker.service.BrokerService;
import com.swingtrade.broker.service.PaperTradingServiceImpl;
import com.swingtrade.broker.service.PaperTradingStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory for creating the appropriate BrokerService implementation
 * based on the configured broker mode.
 * Integrates KillSwitchService to prevent LIVE mode when kill switch is active.
 */
@Component
public class BrokerServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(BrokerServiceFactory.class);

    private final PaperTradingEngine paperTradingEngine;
    private final OrderManager orderManager;
    private final BrokerClient brokerClient;
    private final RiskControls riskControlsService;
    private final KillSwitchService killSwitchService;
    private final PaperTradingStateService stateService;
    private final BrokerProperties props;

    private BrokerMode currentMode;
    private BrokerService currentService;

    @Autowired
    public BrokerServiceFactory(PaperTradingEngine paperTradingEngine,
                                OrderManager orderManager,
                                org.springframework.beans.factory.ObjectProvider<BrokerClient> brokerClientProvider,
                                RiskControls riskControlsService,
                                KillSwitchService killSwitchService,
                                PaperTradingStateService stateService,
                                BrokerProperties props) {
        this.paperTradingEngine = paperTradingEngine;
        this.orderManager = orderManager;
        this.brokerClient = brokerClientProvider.getIfAvailable();
        this.riskControlsService = riskControlsService;
        this.killSwitchService = killSwitchService;
        this.stateService = stateService;
        this.props = props;

        if (this.brokerClient == null) {
            logger.info("No BrokerClient bean found. Live trading will not be available.");
        }

        initialize();
    }

    /**
     * Initialize the broker service based on configuration.
     */
    private void initialize() {
        currentMode = BrokerMode.fromString(props.getMode());
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
                return new PaperTradingServiceImpl(paperTradingEngine, orderManager, stateService);

            case LIVE:
                logger.info("Using Kite Connect Live Trading Service");
                return createLiveTradingService();

            case DRY_RUN:
                logger.info("Using Dry-Run Service (logging only)");
                return createDryRunService();

            default:
                logger.warn("Unknown mode {}, defaulting to PAPER", mode);
                return new PaperTradingServiceImpl(paperTradingEngine, orderManager, stateService);
        }
    }

    /**
     * Create live trading service wrapper.
     * Validates kill switch before allowing live mode switch.
     */
    private BrokerService createLiveTradingService() {
        // Validate kill switch first
        if (killSwitchService.isActive()) {
            logger.error("Cannot switch to LIVE mode: Kill switch is active!");
            throw new IllegalStateException("Cannot switch to LIVE mode: Kill switch is active. " +
                    "Disable kill switch first. Reason: " + killSwitchService.getReason());
        }

        // Validate broker client exists
        if (brokerClient == null) {
            logger.error("Broker client not configured. Cannot create live trading service.");
            throw new IllegalStateException("Broker client not configured. " +
                    "Set up broker API credentials in application properties.");
        }

        // Validate broker client configuration
        if (!brokerClient.isConfigured()) {
            logger.error("Broker client not configured. Cannot create live trading service.");
            throw new IllegalStateException("Broker API key not configured. " +
                    "Set kite.api-key in application properties.");
        }

        if (brokerClient.getAccessToken() == null || brokerClient.getAccessToken().isEmpty()) {
            logger.warn("Broker access token not set. Live trading will fail until authorized.");
        }

        return new LiveTradingService(brokerClient, riskControlsService);
    }

    /**
     * Create dry-run service wrapper.
     */
    private BrokerService createDryRunService() {
        if (brokerClient == null) {
            logger.warn("Broker client not configured. Dry-run will operate without broker connection.");
        }
        return new DryRunService(brokerClient, riskControlsService);
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
        return props.getMode();
    }

    /**
     * Update the mode configuration property.
     */
    public void setModeString(String modeString) {
        props.setMode(modeString);
    }
}
