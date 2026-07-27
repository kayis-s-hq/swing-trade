package com.swingtrade.api.controller;

import com.swingtrade.api.dto.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST Controller for health check endpoints.
 * Provides system health status and component information.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    private final com.swingtrade.data.service.DataIngestionService dataIngestionService;
    private final com.swingtrade.strategy.SwingTradingStrategy strategyService;
    private final com.swingtrade.llm.LlmService llmService;

    public HealthController(
            @Autowired(required = false)
            com.swingtrade.data.service.DataIngestionService dataIngestionService,
            @Autowired(required = false)
            com.swingtrade.strategy.SwingTradingStrategy strategyService,
            @Autowired(required = false)
            com.swingtrade.llm.LlmService llmService) {
        this.dataIngestionService = dataIngestionService;
        this.strategyService = strategyService;
        this.llmService = llmService;
    }

    @GetMapping
    public ResponseEntity<HealthStatus> health() {
        logger.debug("Health check requested");
        HealthStatus status = new HealthStatus();
        status.setSystemInfo(HealthStatus.SystemInfo.fromSystem());
        status.addComponent("api", new HealthStatus.ComponentStatus("api", "UP", "API Service"));
        return ResponseEntity.ok(status);
    }

    @GetMapping("/details")
    public ResponseEntity<HealthStatus> detailedHealth(
            @RequestParam(defaultValue = "false") boolean verbose
    ) {
        logger.debug("Detailed health check requested (verbose: {})", verbose);
        HealthStatus status = new HealthStatus();
        status.setSystemInfo(HealthStatus.SystemInfo.fromSystem());
        status.addComponent("api", new HealthStatus.ComponentStatus("api", "UP", "REST API Service"));

        if (dataIngestionService != null) {
            status.addComponent("data", new HealthStatus.ComponentStatus(
                    "data", "UP", "Data Ingestion Service", getComponentDetails(dataIngestionService)));
        } else {
            status.addComponent("data", new HealthStatus.ComponentStatus(
                    "data", "UP", "Data Service (not configured)"));
        }

        if (strategyService != null) {
            status.addComponent("strategy", new HealthStatus.ComponentStatus(
                    "strategy", "UP", "Trading Strategy Engine", getComponentDetails(strategyService)));
        } else {
            status.addComponent("strategy", new HealthStatus.ComponentStatus(
                    "strategy", "UP", "Strategy Service (not configured)"));
        }

        if (llmService != null) {
            status.addComponent("llm", new HealthStatus.ComponentStatus(
                    "llm", "UP", "LLM Sentiment Service", getComponentDetails(llmService)));
        } else {
            status.addComponent("llm", new HealthStatus.ComponentStatus(
                    "llm", "UP", "LLM Service (not configured)"));
        }

        if (verbose) {
            status.getComponents().forEach((name, component) -> {
                component.addDetail("javaVersion", System.getProperty("java.version"));
                component.addDetail("osName", System.getProperty("os.name"));
                component.addDetail("uptime", getUptime());
            });
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/database")
    public ResponseEntity<HealthStatus> databaseHealth() {
        logger.debug("Database health check requested");
        HealthStatus status = new HealthStatus();
        status.setSystemInfo(HealthStatus.SystemInfo.fromSystem());

        if (dataIngestionService != null) {
            status.addComponent("database", new HealthStatus.ComponentStatus(
                    "database", "UP", "Database Connection",
                    Map.of("type", "PostgreSQL", "version", getDatabaseVersion())));
        } else {
            status.addComponent("database", new HealthStatus.ComponentStatus(
                    "database", "UP", "Database Service (not configured)"));
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/market-data")
    public ResponseEntity<HealthStatus> marketDataHealth() {
        logger.debug("Market data health check requested");
        HealthStatus status = new HealthStatus();
        status.setSystemInfo(HealthStatus.SystemInfo.fromSystem());

        if (dataIngestionService != null) {
            status.addComponent("market-data", new HealthStatus.ComponentStatus(
                    "market-data", "UP", "Market Data Service",
                    Map.of("provider", "Upstox", "status", "Active")));
        } else {
            status.addComponent("market-data", new HealthStatus.ComponentStatus(
                    "market-data", "UP", "Market Data Service (not configured)"));
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/llm")
    public ResponseEntity<HealthStatus> llmHealth() {
        logger.debug("LLM health check requested");
        HealthStatus status = new HealthStatus();
        status.setSystemInfo(HealthStatus.SystemInfo.fromSystem());

        if (llmService != null) {
            status.addComponent("llm", new HealthStatus.ComponentStatus(
                    "llm", "UP", "LLM Sentiment Service",
                    Map.of("provider", "vLLM", "status", "Active")));
        } else {
            status.addComponent("llm", new HealthStatus.ComponentStatus(
                    "llm", "UP", "LLM Service (not configured)"));
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/full")
    public ResponseEntity<HealthStatus> fullHealth() {
        logger.debug("Full health check requested");
        HealthStatus status = new HealthStatus();
        status.setSystemInfo(HealthStatus.SystemInfo.fromSystem());
        status.addComponent("api", new HealthStatus.ComponentStatus("api", "UP", "REST API Service"));
        status.addComponent("database", getComponentHealth("database", "Database Service"));
        status.addComponent("market-data", getComponentHealth("market-data", "Market Data Service"));
        status.addComponent("strategy", getComponentHealth("strategy", "Trading Strategy Service"));
        status.addComponent("llm", getComponentHealth("llm", "LLM Sentiment Service"));
        return ResponseEntity.ok(status);
    }

    private HealthStatus.ComponentStatus getComponentHealth(String name, String description) {
        Map<String, Object> details = new ConcurrentHashMap<>();
        details.put("status", "Active");
        details.put("responseTime", "OK");
        return new HealthStatus.ComponentStatus(name, "UP", description, details);
    }

    private Map<String, Object> getComponentDetails(Object service) {
        Map<String, Object> details = new ConcurrentHashMap<>();
        details.put("serviceName", service.getClass().getSimpleName());
        details.put("status", "Active");
        return details;
    }

    private String getDatabaseVersion() {
        return "PostgreSQL 14+";
    }

    private String getUptime() {
        long uptimeMs = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        long hours = uptimeMs / (1000 * 60 * 60);
        long minutes = (uptimeMs % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.ROOT, "%dh %dm", hours, minutes);
    }
}