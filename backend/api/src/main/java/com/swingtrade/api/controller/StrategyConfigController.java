package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.api.dto.StrategyConfigRequest;
import com.swingtrade.api.dto.StrategyConfigResponse;
import com.swingtrade.api.dto.StrategyModeRequest;
import com.swingtrade.api.service.StrategyConfigService;
import com.swingtrade.domain.StrategyConfig;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/strategy-configs")
public class StrategyConfigController {
    private final StrategyConfigService service;

    public StrategyConfigController(StrategyConfigService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StrategyConfigResponse>>> list(
            @RequestParam(required = false) String variantId,
            @RequestParam(required = false) StrategyConfig.Mode mode) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(variantId, mode)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StrategyConfigResponse>> create(
            @Valid @RequestBody StrategyConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request)));
    }

    @GetMapping("/{variantId}")
    public ResponseEntity<ApiResponse<StrategyConfigResponse>> current(@PathVariable String variantId) {
        return ResponseEntity.ok(ApiResponse.ok(service.current(variantId)));
    }

    @GetMapping("/{variantId}/versions")
    public ResponseEntity<ApiResponse<List<StrategyConfigResponse>>> versions(@PathVariable String variantId) {
        return ResponseEntity.ok(ApiResponse.ok(service.versions(variantId)));
    }

    @GetMapping("/{variantId}/versions/{version}")
    public ResponseEntity<ApiResponse<StrategyConfigResponse>> version(
            @PathVariable String variantId, @PathVariable int version) {
        return ResponseEntity.ok(ApiResponse.ok(service.version(variantId, version)));
    }

    @PutMapping("/{variantId}")
    public ResponseEntity<ApiResponse<StrategyConfigResponse>> update(
            @PathVariable String variantId, @Valid @RequestBody StrategyConfigRequest request) {
        if (!variantId.equals(request.variantId())) {
            throw new IllegalArgumentException("Path variantId must match request variantId");
        }
        return ResponseEntity.ok(ApiResponse.ok(service.create(request)));
    }

    @PutMapping("/{variantId}/mode")
    public ResponseEntity<ApiResponse<StrategyConfigResponse>> mode(
            @PathVariable String variantId, @Valid @RequestBody StrategyModeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.changeMode(variantId, request)));
    }

    @DeleteMapping("/{variantId}")
    public ResponseEntity<ApiResponse<StrategyConfigResponse>> delete(@PathVariable String variantId) {
        return ResponseEntity.ok(ApiResponse.ok(service.delete(variantId)));
    }
}
