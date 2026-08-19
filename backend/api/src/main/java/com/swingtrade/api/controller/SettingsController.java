package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.data.service.AppSettingsService;
import com.swingtrade.data.service.MarketDataClientProvider;
import com.swingtrade.llm.service.LlamaCppServerManager;
import com.swingtrade.llm.service.LlmBackendSelector;
import com.swingtrade.llm.service.LlmServerManager;
import com.swingtrade.llm.service.PiLlamaServerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    private final MarketDataClientProvider marketDataClientProvider;
    private final AppSettingsService appSettingsService;
    private final DiscordNotificationService discordNotificationService;
    private final LlmBackendSelector selector;
    private final LlamaCppServerManager localServerManager;
    private final PiLlamaServerManager piServerManager;

    @Autowired
    public SettingsController(MarketDataClientProvider marketDataClientProvider,
                              AppSettingsService appSettingsService,
                              DiscordNotificationService discordNotificationService,
                              LlmBackendSelector selector,
                              LlamaCppServerManager localServerManager,
                              PiLlamaServerManager piServerManager) {
        this.marketDataClientProvider = marketDataClientProvider;
        this.appSettingsService = appSettingsService;
        this.discordNotificationService = discordNotificationService;
        this.selector = selector;
        this.localServerManager = localServerManager;
        this.piServerManager = piServerManager;
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSettings() {
        Map<String, String> settings = Map.of(
            "selectedBroker", marketDataClientProvider.getActiveBroker()
        );
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PostMapping("/settings/broker")
    public ResponseEntity<ApiResponse<Map<String, String>>> setBroker(
            @RequestBody Map<String, String> body
    ) {
        String broker = body.get("broker");
        if (broker == null || broker.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("broker is required"));
        }
        marketDataClientProvider.setActiveBroker(broker);
        Map<String, String> settings = Map.of("selectedBroker", broker);
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @GetMapping("/settings/llm")
    public ResponseEntity<ApiResponse<Map<String, String>>> getLlmSettings() {
        Map<String, String> settings = new java.util.LinkedHashMap<>();
        settings.put("llm.base_url", appSettingsService.get("llm.base_url", ""));
        settings.put("llm.backend", appSettingsService.get("llm.backend", "local"));
        settings.put("openai.base_url", appSettingsService.get("openai.base_url", "https://api.openai.com/v1"));
        settings.put("openai.model", appSettingsService.get("openai.model", "gpt-4o"));
        settings.put("openai.api_key", appSettingsService.get("openai.api_key", ""));
        settings.put("llamacpp.model", appSettingsService.get("llamacpp.model", "/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf"));
        settings.put("llm.pdf.base_url", appSettingsService.get("llm.pdf.base_url", ""));
        settings.put("llm.pdf.model", appSettingsService.get("llm.pdf.model", ""));
        settings.put("gpuhub.api_key", appSettingsService.get("gpuhub.api_key", ""));
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/settings/llm")
    public ResponseEntity<ApiResponse<Map<String, String>>> setLlmSettings(
            @RequestBody Map<String, String> body) {
        String oldBackend = appSettingsService.get("llm.backend", "local");
        body.forEach((key, value) -> appSettingsService.set(key, value));

        // Handle server restart when backend or model changes
        String newBackend = body.getOrDefault("llm.backend", oldBackend);
        boolean backendChanged = !oldBackend.equals(newBackend);
        boolean modelChanged = body.containsKey("llamacpp.model")
                && !body.get("llamacpp.model").equals(appSettingsService.get("llamacpp.model", ""));

        if (backendChanged || modelChanged) {
            try {
                var backend = selector.resolve();
                LlmServerManager manager = switch (backend) {
                    case LOCAL -> localServerManager;
                    case PI_SSH -> piServerManager;
                    case GPUHUB -> null; // no server to manage
                };
                if (manager != null && manager.isRunning()) {
                    manager.restart();
                }
            } catch (Exception e) {
                logger.warn("llama-server restart failed: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/settings/gpuhub")
    public ResponseEntity<ApiResponse<Map<String, String>>> getGpuHubSettings() {
        Map<String, String> settings = Map.of(
            "gpuhub.api_key", appSettingsService.get("gpuhub.api_key", "")
        );
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/settings/gpuhub")
    public ResponseEntity<ApiResponse<Map<String, String>>> setGpuHubSettings(
            @RequestBody Map<String, String> body) {
        body.forEach((key, value) -> appSettingsService.set(key, value));
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/settings/discord")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDiscordSettings() {
        Map<String, String> settings = Map.of(
            "discord.webhook.url", appSettingsService.get("discord.webhook.url", ""),
            "discord.webhook.enabled", appSettingsService.get("discord.webhook.enabled", "false")
        );
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/settings/discord")
    public ResponseEntity<ApiResponse<Map<String, String>>> setDiscordSettings(
            @RequestBody Map<String, String> body) {
        body.forEach((key, value) -> appSettingsService.set(key, value));
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @PostMapping("/settings/test/discord")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> testDiscord() {
        boolean success = discordNotificationService.sendMessage("SwingTrade: webhook test successful");
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", success)));
    }

    @PostMapping("/settings/test/pi")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testPiConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            logger.info("Testing Pi SSH connection and lazy start...");
            piServerManager.ensureRunning();
            boolean running = piServerManager.isRunning();
            result.put("success", running);
            if (running) {
                result.put("message", "Pi SSH connection successful, llama-server started");
                appSettingsService.set("llm.backend", "pi_ssh");
                logger.info("Pi SSH test passed — backend switched to pi_ssh");
            } else {
                result.put("message", "SSH connected but llama-server failed to start");
                appSettingsService.set("llm.backend", "local");
            }
            if (!running) {
                piServerManager.stop();
            }
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            logger.warn("Pi SSH test failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "SSH connection failed: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(result));
        }
    }

    // -----------------------------------------------------------------------
    // Trading Configuration
    // -----------------------------------------------------------------------

    @GetMapping("/settings/trading")
    public ResponseEntity<ApiResponse<Map<String, String>>> getTradingSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("trading.mode", appSettingsService.get("trading.mode", "paper"));
        settings.put("trading.max_position_size", appSettingsService.get("trading.max_position_size", "10"));
        settings.put("trading.stop_loss", appSettingsService.get("trading.stop_loss", "5"));
        settings.put("trading.take_profit", appSettingsService.get("trading.take_profit", "15"));
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/settings/trading")
    public ResponseEntity<ApiResponse<Map<String, String>>> setTradingSettings(
            @RequestBody Map<String, String> body) {
        body.forEach((key, value) -> appSettingsService.set(key, value));
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    // -----------------------------------------------------------------------
    // Unified Save — persist all settings in one call
    // -----------------------------------------------------------------------

    @PostMapping("/settings/save")
    public ResponseEntity<ApiResponse<Map<String, String>>> saveAllSettings(
            @RequestBody Map<String, Object> body) {
        // Accept nested objects: { broker, llm, discord, trading, gpuhub }
        if (body.containsKey("broker")) {
            String broker = String.valueOf(body.get("broker"));
            if (broker != null && !broker.isBlank() && !"null".equals(broker)) {
                marketDataClientProvider.setActiveBroker(broker);
                appSettingsService.set("selectedBroker", broker);
            }
        }
        if (body.containsKey("llm") && body.get("llm") instanceof Map<?, ?>) {
            ((Map<?, ?>) body.get("llm")).forEach((key, value) -> appSettingsService.set(String.valueOf(key), String.valueOf(value)));
        }
        if (body.containsKey("gpuhub") && body.get("gpuhub") instanceof Map<?, ?>) {
            ((Map<?, ?>) body.get("gpuhub")).forEach((key, value) -> appSettingsService.set(String.valueOf(key), String.valueOf(value)));
        }
        if (body.containsKey("discord") && body.get("discord") instanceof Map<?, ?>) {
            ((Map<?, ?>) body.get("discord")).forEach((key, value) -> appSettingsService.set(String.valueOf(key), String.valueOf(value)));
        }
        if (body.containsKey("trading") && body.get("trading") instanceof Map<?, ?>) {
            ((Map<?, ?>) body.get("trading")).forEach((key, value) -> appSettingsService.set(String.valueOf(key), String.valueOf(value)));
        }

        // Return consolidated settings
        Map<String, String> result = new LinkedHashMap<>();
        result.put("selectedBroker", marketDataClientProvider.getActiveBroker());
        result.putAll(getLlmSettings().getBody().data());
        result.putAll(getGpuHubSettings().getBody().data());
        result.putAll(getDiscordSettings().getBody().data());
        result.putAll(getTradingSettings().getBody().data());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
