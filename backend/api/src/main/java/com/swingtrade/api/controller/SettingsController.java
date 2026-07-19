package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.data.service.AppSettingsService;
import com.swingtrade.data.service.MarketDataClientProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SettingsController {

    private final MarketDataClientProvider marketDataClientProvider;
    private final AppSettingsService appSettingsService;
    private final DiscordNotificationService discordNotificationService;

    public SettingsController(MarketDataClientProvider marketDataClientProvider,
                              AppSettingsService appSettingsService,
                              DiscordNotificationService discordNotificationService) {
        this.marketDataClientProvider = marketDataClientProvider;
        this.appSettingsService = appSettingsService;
        this.discordNotificationService = discordNotificationService;
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
        Map<String, String> settings = Map.of(
            "llm.vllm.base_url", appSettingsService.get("llm.vllm.base_url", ""),
            "llm.vllm.model", appSettingsService.get("llm.vllm.model", ""),
            "llm.pdf.base_url", appSettingsService.get("llm.pdf.base_url", ""),
            "llm.pdf.model", appSettingsService.get("llm.pdf.model", "")
        );
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/settings/llm")
    public ResponseEntity<ApiResponse<Map<String, String>>> setLlmSettings(
            @RequestBody Map<String, String> body) {
        body.forEach((key, value) -> appSettingsService.set(key, value));
        return getLlmSettings();
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
        return getDiscordSettings();
    }

    @PostMapping("/settings/test/discord")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> testDiscord() {
        boolean success = discordNotificationService.sendMessage("SwingTrade: webhook test successful");
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", success)));
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
        return getTradingSettings();
    }

    // -----------------------------------------------------------------------
    // Unified Save — persist all settings in one call
    // -----------------------------------------------------------------------

    @PostMapping("/settings/save")
    public ResponseEntity<ApiResponse<Map<String, String>>> saveAllSettings(
            @RequestBody Map<String, Object> body) {
        // Accept nested objects: { broker, llm, discord, trading }
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
        result.putAll(getDiscordSettings().getBody().data());
        result.putAll(getTradingSettings().getBody().data());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
