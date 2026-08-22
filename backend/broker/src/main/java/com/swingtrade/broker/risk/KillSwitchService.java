package com.swingtrade.broker.risk;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.core.metrics.KillSwitchMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kill switch service for live trading.
 * When active, it halts all new order placement and closes existing positions.
 * Provides admin API for enabling/disabling the kill switch.
 */
@Component
public class KillSwitchService {

    private static final Logger logger = LoggerFactory.getLogger(KillSwitchService.class);

    private boolean active;
    private LocalDateTime enabledAt;
    private String reason;

    private final JdbcTemplate jdbcTemplate;
    private final BrokerProperties props;
    private final KillSwitchMetrics killSwitchMetrics;

    public KillSwitchService(BrokerProperties props) {
        this(null, props, null);
    }

    /**
     * Constructor with JdbcTemplate for persistence.
     */
    @Autowired
    public KillSwitchService(JdbcTemplate jdbcTemplate, BrokerProperties props, KillSwitchMetrics killSwitchMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
        this.killSwitchMetrics = killSwitchMetrics;
        this.active = props.isKillSwitchActive();
        this.enabledAt = null;
        this.reason = null;

        // Try to load from database if table exists
        if (jdbcTemplate != null) {
            try {
                loadFromDatabase();
            } catch (Exception e) {
                logger.warn("Failed to load kill switch from database: {}", e.getMessage());
            }
        }

        // Initialize metrics state
        if (killSwitchMetrics != null) {
            killSwitchMetrics.setActive(this.active);
        }

        logger.info("KillSwitchService initialized (active: {})", active);
    }

    /**
     * Load kill switch state from database.
     */
    private void loadFromDatabase() {
        if (jdbcTemplate == null) return;

        try {
            String sql = "SELECT active, enabled_at, reason FROM kill_switch ORDER BY id DESC LIMIT 1";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);

            this.active = (Boolean) result.get("active");
            this.enabledAt = result.get("enabled_at") != null
                    ? LocalDateTime.parse(result.get("enabled_at").toString())
                    : null;
            this.reason = result.get("reason") != null
                    ? (String) result.get("reason")
                    : null;

            logger.info("Loaded kill switch state from database: active={}, enabledAt={}",
                    active, enabledAt);
        } catch (Exception e) {
            logger.warn("Database kill switch table not found, using in-memory state: {}",
                    e.getMessage());
        }
    }

    /**
     * Persist kill switch state to database.
     */
    private void persistToDatabase() {
        if (jdbcTemplate == null) return;

        try {
            // Try to update existing record
            int updated = jdbcTemplate.update(
                    "UPDATE kill_switch SET active = ?, enabled_at = ?, reason = ?, updated_at = ? WHERE id = 1",
                    active, enabledAt, reason, LocalDateTime.now()
            );

            // If no update, insert new record
            if (updated == 0) {
                jdbcTemplate.update(
                        "INSERT INTO kill_switch (id, active, enabled_at, reason, created_at, updated_at) " +
                                "VALUES (1, ?, ?, ?, NOW(), NOW())",
                        active, enabledAt, reason
                );
                logger.info("Persisted kill switch state to database");
            }
        } catch (Exception e) {
            logger.warn("Failed to persist kill switch to database: {}", e.getMessage());
        }
    }

    /**
     * Enable the kill switch.
     * All trading will be halted until explicitly disabled.
     *
     * @param reason optional reason for enabling
     */
    public void enableKillSwitch(String reason) {
        this.active = true;
        this.enabledAt = LocalDateTime.now();
        this.reason = reason;

        logger.warn("KILL SWITCH ENABLED! Reason: {}", reason != null ? reason : "No reason provided");

        persistToDatabase();
        if (killSwitchMetrics != null) {
            killSwitchMetrics.setActive(true);
        }
    }

    /**
     * Enable the kill switch without reason.
     */
    public void enableKillSwitch() {
        enableKillSwitch(null);
    }

    /**
     * Disable the kill switch.
     * Trading will resume after disabling.
     */
    public void disableKillSwitch() {
        logger.info("Kill switch disabled, trading will resume");

        this.active = false;
        this.enabledAt = null;
        this.reason = null;

        persistToDatabase();
        if (killSwitchMetrics != null) {
            killSwitchMetrics.setActive(false);
        }
    }

    /**
     * Check if kill switch is currently active.
     *
     * @return true if kill switch is enabled
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Get the kill switch state.
     *
     * @return KillSwitchState with current status
     */
    public KillSwitchState getKillSwitchState() {
        return new KillSwitchState(active, enabledAt, reason);
    }

    /**
     * Toggle the kill switch state.
     *
     * @param enable true to enable, false to disable
     */
    public void toggle(boolean enable) {
        if (enable) {
            enableKillSwitch(null);
        } else {
            disableKillSwitch();
        }
    }

    /**
     * Get the reason why kill switch was enabled.
     *
     * @return the reason string, or null if never enabled
     */
    public String getReason() {
        return reason;
    }

    /**
     * Get when the kill switch was enabled.
     *
     * @return the timestamp, or null if never enabled
     */
    public LocalDateTime getEnabledAt() {
        return enabledAt;
    }

    /**
     * Get whether the kill switch functionality is enabled in config.
     *
     * @return true if kill switch is enabled in configuration
     */
    public boolean isKillSwitchEnabled() {
        return props.isKillSwitchEnabled();
    }

    /**
     * Simple data class for kill switch state.
     */
    public static class KillSwitchState {
        private final boolean active;
        private final LocalDateTime enabledAt;
        private final String reason;

        public KillSwitchState(boolean active, LocalDateTime enabledAt, String reason) {
            this.active = active;
            this.enabledAt = enabledAt;
            this.reason = reason;
        }

        public boolean isActive() {
            return active;
        }

        public LocalDateTime getEnabledAt() {
            return enabledAt;
        }

        public String getReason() {
            return reason;
        }
    }
}
