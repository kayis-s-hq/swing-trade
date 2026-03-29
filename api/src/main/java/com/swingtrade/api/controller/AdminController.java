package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.api.dto.KillSwitchRequest;
import com.swingtrade.broker.risk.KillSwitchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin controller for system management operations.
 * Provides endpoints for kill switch and other administrative functions.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final KillSwitchService killSwitchService;

    @Autowired
    public AdminController(KillSwitchService killSwitchService) {
        this.killSwitchService = killSwitchService;
        logger.info("AdminController initialized with kill switch service");
    }

    /**
     * Enable or disable the kill switch.
     * When active, all new trades are blocked and existing positions are closed.
     *
     * @param request kill switch request with enabled flag and optional reason
     * @return API response with status
     */
    @PostMapping("/kill-switch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> killSwitch(@Valid @RequestBody KillSwitchRequest request) {
        logger.info("Kill switch {} requested. Reason: {}",
                request.enabled() ? "enable" : "disable", request.getReason());

        try {
            if (request.enabled()) {
                killSwitchService.enableKillSwitch(request.getReason());
                logger.warn("Kill switch ENABLED by admin request. Reason: {}", request.getReason());
            } else {
                killSwitchService.disableKillSwitch();
                logger.info("Kill switch DISABLED by admin request");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("active", killSwitchService.isActive());
            data.put("enabledAt", killSwitchService.getEnabledAt());
            data.put("reason", request.getReason());

            return ResponseEntity.ok(ApiResponse.ok(data, null));

        } catch (Exception e) {
            logger.error("Error toggling kill switch: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to toggle kill switch: " + e.getMessage()));
        }
    }

    /**
     * Get current kill switch status.
     *
     * @return kill switch status response
     */
    @GetMapping("/kill-switch/status")
    public ResponseEntity<ApiResponse<KillSwitchService.KillSwitchState>> getKillSwitchStatus() {
        logger.debug("Fetching kill switch status");

        try {
            KillSwitchService.KillSwitchState status = killSwitchService.getKillSwitchState();
            return ResponseEntity.ok(ApiResponse.ok(status));

        } catch (Exception e) {
            logger.error("Error fetching kill switch status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get kill switch status: " + e.getMessage()));
        }
    }

    /**
     * Toggle kill switch (alternative endpoint for quick toggle).
     *
     * @param enable true to enable, false to disable
     * @return API response with status
     */
    @PostMapping("/kill-switch/toggle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleKillSwitch(@RequestParam boolean enable) {
        logger.info("Toggle kill switch to: {}", enable);

        try {
            killSwitchService.toggle(enable);
            Map<String, Object> data = new HashMap<>();
            data.put("active", killSwitchService.isActive());
            data.put("enabledAt", killSwitchService.getEnabledAt());
            data.put("reason", killSwitchService.getReason());

            return ResponseEntity.ok(ApiResponse.ok(data, null));

        } catch (Exception e) {
            logger.error("Error toggling kill switch: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to toggle kill switch: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint for admin services.
     *
     * @return API response with health status
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminHealth() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "admin");
        data.put("status", "healthy");
        data.put("killSwitchActive", killSwitchService.isActive());
        data.put("killSwitchEnabled", killSwitchService.isKillSwitchEnabled());
        data.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
