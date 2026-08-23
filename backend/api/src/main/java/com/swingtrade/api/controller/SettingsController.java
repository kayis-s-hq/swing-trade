package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.data.service.AppSettingsService;
import com.swingtrade.data.service.MarketDataClientProvider;
import com.swingtrade.llm.client.LlmClient;
import com.swingtrade.llm.service.LlamaCppServerManager;
import com.swingtrade.llm.service.LlmBackendSelector;
import com.swingtrade.llm.service.MlxServerManager;
import com.swingtrade.llm.service.LlmClientProvider;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    private final MarketDataClientProvider marketDataClientProvider;
    private final AppSettingsService appSettingsService;
    private final DiscordNotificationService discordNotificationService;
    private final LlmBackendSelector selector;
    private final LlmClientProvider llmClientProvider;
    private final LlamaCppServerManager localServerManager;
    private final PiLlamaServerManager piServerManager;
    private final MlxServerManager mlxServerManager;

    @Autowired
    public SettingsController(MarketDataClientProvider marketDataClientProvider,
                              AppSettingsService appSettingsService,
                              DiscordNotificationService discordNotificationService,
                              LlmBackendSelector selector,
                              LlmClientProvider llmClientProvider,
                              LlamaCppServerManager localServerManager,
                              PiLlamaServerManager piServerManager,
                              MlxServerManager mlxServerManager) {
        this.marketDataClientProvider = marketDataClientProvider;
        this.appSettingsService = appSettingsService;
        this.discordNotificationService = discordNotificationService;
        this.selector = selector;
        this.llmClientProvider = llmClientProvider;
        this.localServerManager = localServerManager;
        this.piServerManager = piServerManager;
        this.mlxServerManager = mlxServerManager;
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
        settings.put(LlmSettingKeys.BASE_URL, appSettingsService.get(LlmSettingKeys.BASE_URL, LlmSettingKeys.DEFAULT_BASE_URL));
        settings.put(LlmSettingKeys.BACKEND, appSettingsService.get(LlmSettingKeys.BACKEND, LlmSettingKeys.DEFAULT_BACKEND));
        settings.put(LlmSettingKeys.OPENAI_BASE_URL, appSettingsService.get(LlmSettingKeys.OPENAI_BASE_URL, LlmSettingKeys.DEFAULT_OPENAI_BASE_URL));
        settings.put(LlmSettingKeys.OPENAI_MODEL, appSettingsService.get(LlmSettingKeys.OPENAI_MODEL, LlmSettingKeys.DEFAULT_OPENAI_MODEL));
        settings.put(LlmSettingKeys.OPENAI_API_KEY, appSettingsService.get(LlmSettingKeys.OPENAI_API_KEY, LlmSettingKeys.DEFAULT_API_KEY));
        settings.put(LlmSettingKeys.LLAMACPP_MODEL, appSettingsService.get(LlmSettingKeys.LLAMACPP_MODEL, LlmSettingKeys.DEFAULT_LLAMACPP_MODEL));
        settings.put(LlmSettingKeys.MLX_MODEL, appSettingsService.get(LlmSettingKeys.MLX_MODEL, LlmSettingKeys.DEFAULT_MLX_MODEL));
        settings.put(LlmSettingKeys.MLX_SERVER_URL, appSettingsService.get(LlmSettingKeys.MLX_SERVER_URL, LlmSettingKeys.DEFAULT_MLX_SERVER_URL));
        settings.put(LlmSettingKeys.PDF_BASE_URL, appSettingsService.get(LlmSettingKeys.PDF_BASE_URL, LlmSettingKeys.DEFAULT_PDF_BASE_URL));
        settings.put(LlmSettingKeys.PDF_MODEL, appSettingsService.get(LlmSettingKeys.PDF_MODEL, LlmSettingKeys.DEFAULT_PDF_MODEL));
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/settings/llm")
    public ResponseEntity<ApiResponse<Map<String, String>>> setLlmSettings(
            @RequestBody Map<String, String> body) {
        String oldBackend = appSettingsService.get(LlmSettingKeys.BACKEND, LlmSettingKeys.DEFAULT_BACKEND);
        body.forEach((key, value) -> appSettingsService.set(key, value));

        // Handle server restart when backend or model changes
        String newBackend = body.getOrDefault(LlmSettingKeys.BACKEND, oldBackend);
        boolean backendChanged = !oldBackend.equals(newBackend);
        boolean llamacppModelChanged = body.containsKey(LlmSettingKeys.LLAMACPP_MODEL)
                && !body.get(LlmSettingKeys.LLAMACPP_MODEL).equals(appSettingsService.get(LlmSettingKeys.LLAMACPP_MODEL, ""));
        boolean mlxModelChanged = body.containsKey(LlmSettingKeys.MLX_MODEL)
                && !body.get(LlmSettingKeys.MLX_MODEL).equals(appSettingsService.get(LlmSettingKeys.MLX_MODEL, ""));

        if (backendChanged || llamacppModelChanged || mlxModelChanged) {
            try {
                var backend = selector.resolve();
                LlmServerManager manager = switch (backend) {
                    case LOCAL -> localServerManager;
                    case PI_SSH -> piServerManager;
                    case OPENAI -> null; // no server to manage
                    case MLX -> mlxServerManager;
                };
                if (manager != null && manager.isRunning()) {
                    manager.restart();
                }
            } catch (Exception e) {
                logger.warn("LLM server restart failed: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/settings/openai")
    public ResponseEntity<ApiResponse<Map<String, String>>> getOpenAiSettings() {
        Map<String, String> settings = Map.of(
            LlmSettingKeys.OPENAI_API_KEY, appSettingsService.get(LlmSettingKeys.OPENAI_API_KEY, LlmSettingKeys.DEFAULT_API_KEY)
        );
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/settings/openai")
    public ResponseEntity<ApiResponse<Map<String, String>>> setOpenAiSettings(
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
            boolean serverRunning = piServerManager.isRunning();
            if (!serverRunning) {
                result.put("success", false);
                result.put("message", "SSH connected but llama-server failed to start");
                return ResponseEntity.ok(ApiResponse.ok(result));
            }

            // Test actual LLM inference with a temporary client — does NOT affect the active backend.
            String piBaseUrl = "http://piworm.local:8090";
            boolean inferenceOk = testInference(piBaseUrl);
            result.put("success", inferenceOk);
            result.put("message", inferenceOk
                ? "Pi SSH connection successful, llama-server started and responded to inference"
                : "Pi connected and server started, but inference failed");

            if (!inferenceOk) {
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

    private boolean testInference(String baseUrl) {
        try {
            String payload = """
                {"model":"qwen3-4b","messages":[{"role":"user","content":"Reply with exactly: OK"}],"max_tokens":8,"temperature":0.2}""";
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                .build();
            java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            // Parse "choices[0].message.content" from the JSON response
            var mapper = new tools.jackson.databind.ObjectMapper();
            var node = mapper.readTree(response.body());
            var content = node.path("choices").path(0).path("message").path("content").asText(null);
            return content != null && content.trim().equalsIgnoreCase("OK");
        } catch (Exception e) {
            logger.debug("Inference test failed: {}", e.getMessage());
            return false;
        }
    }

    @PostMapping("/settings/test/openai")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testOpenAiConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            LlmClient client = llmClientProvider.getClient();
            if (client == null) {
                result.put("success", false);
                result.put("message", "No LLM client configured");
                return ResponseEntity.ok(ApiResponse.ok(result));
            }
            // Send a minimal test prompt to verify connectivity
            var messages = List.of(
                Map.of("role", "system", "content", "Respond with a single word."),
                Map.of("role", "user", "content", "Respond with a single word.")
            );
            String response = client.generateChatCompletion(messages, 16, 0.0)
                .block(java.time.Duration.ofSeconds(30));
            boolean ok = response != null && !response.isBlank();
            result.put("success", ok);
            result.put("message", ok ? "OpenAI-compatible LLM responded successfully" : "LLM returned empty response");
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            logger.warn("OpenAI test failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(result));
        }
    }

    // -----------------------------------------------------------------------
    // Pi SSH LLM Server Lifecycle
    // -----------------------------------------------------------------------

    @PostMapping("/settings/pi/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startPiServer() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            logger.info("Starting Pi llama-server via SSH...");
            piServerManager.ensureRunning();
            boolean running = piServerManager.isRunning();
            result.put("success", running);
            result.put("running", running);
            result.put("message", running
                ? "Pi llama-server started and healthy"
                : "Pi llama-server failed to start");
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            logger.warn("Pi start failed: {}", e.getMessage());
            result.put("success", false);
            result.put("running", false);
            result.put("message", "Failed to start: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(result));
        }
    }

    @PostMapping("/settings/pi/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopPiServer() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            logger.info("Stopping Pi llama-server via SSH...");
            piServerManager.stop();
            result.put("success", true);
            result.put("running", false);
            result.put("message", "Pi llama-server stopped");
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            logger.warn("Pi stop failed: {}", e.getMessage());
            result.put("success", false);
            result.put("running", true);
            result.put("message", "Failed to stop: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(result));
        }
    }

    @GetMapping("/settings/pi/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPiServerStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean running = piServerManager.isRunning();
        result.put("running", running);
        result.put("success", true);
        result.put("message", running
            ? "Pi llama-server is running"
            : "Pi llama-server is not running");
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // -----------------------------------------------------------------------
    // MLX Server Lifecycle
    // -----------------------------------------------------------------------

    @PostMapping("/settings/mlx/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startMlxServer() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            logger.info("Starting MLX server...");
            mlxServerManager.ensureRunning();
            boolean running = mlxServerManager.isRunning();
            result.put("success", running);
            result.put("running", running);
            result.put("message", running
                ? "MLX server started and healthy"
                : "MLX server failed to start");
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            logger.warn("MLX start failed: {}", e.getMessage());
            result.put("success", false);
            result.put("running", false);
            result.put("message", "Failed to start: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(result));
        }
    }

    @PostMapping("/settings/mlx/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopMlxServer() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            logger.info("Stopping MLX server...");
            mlxServerManager.stop();
            result.put("success", true);
            result.put("running", false);
            result.put("message", "MLX server stopped");
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            logger.warn("MLX stop failed: {}", e.getMessage());
            result.put("success", false);
            result.put("running", true);
            result.put("message", "Failed to stop: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(result));
        }
    }

    @GetMapping("/settings/mlx/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMlxServerStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean running = mlxServerManager.isRunning();
        result.put("running", running);
        result.put("success", true);
        result.put("message", running
            ? "MLX server is running"
            : "MLX server is not running");
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/settings/test/mlx")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testMlxConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            logger.info("Testing MLX connection and lazy start...");
            mlxServerManager.ensureRunning();
            boolean serverRunning = mlxServerManager.isRunning();
            if (!serverRunning) {
                result.put("success", false);
                result.put("message", "MLX server failed to start");
                return ResponseEntity.ok(ApiResponse.ok(result));
            }

            // Test actual LLM inference with a temporary client — does NOT affect the active backend.
            String mlxBaseUrl = appSettingsService.get(LlmSettingKeys.MLX_SERVER_URL, LlmSettingKeys.DEFAULT_MLX_SERVER_URL);
            boolean inferenceOk = testInference(mlxBaseUrl);
            result.put("success", inferenceOk);
            result.put("message", inferenceOk
                ? "MLX connection successful, server started and responded to inference"
                : "MLX connected and server started, but inference failed");

            if (!inferenceOk) {
                mlxServerManager.stop();
            }
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            logger.warn("MLX test failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
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
        // Accept nested objects: { broker, llm, discord, trading, openai }
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
        if (body.containsKey("openai") && body.get("openai") instanceof Map<?, ?>) {
            ((Map<?, ?>) body.get("openai")).forEach((key, value) -> appSettingsService.set(String.valueOf(key), String.valueOf(value)));
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
        result.putAll(getOpenAiSettings().getBody().data());
        result.putAll(getDiscordSettings().getBody().data());
        result.putAll(getTradingSettings().getBody().data());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
