package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.data.service.MarketDataClientProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SettingsController {

    private final MarketDataClientProvider marketDataClientProvider;

    public SettingsController(MarketDataClientProvider marketDataClientProvider) {
        this.marketDataClientProvider = marketDataClientProvider;
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
}
