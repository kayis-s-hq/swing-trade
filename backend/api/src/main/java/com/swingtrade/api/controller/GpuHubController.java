package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.gpuhub.dto.DeploymentInfo;
import com.swingtrade.gpuhub.dto.CreateDeploymentResponse;
import com.swingtrade.gpuhub.service.GpuHubDeploymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gpuhub")
public class GpuHubController {

    private static final Logger logger = LoggerFactory.getLogger(GpuHubController.class);

    private final GpuHubDeploymentService deploymentService;

    public GpuHubController(GpuHubDeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    /**
     * Create a new GPU deployment.
     * Body: { name, dc, gpuSet, model, replicas, reuseContainer }
     */
    @PostMapping("/deployments")
    public ResponseEntity<ApiResponse<CreateDeploymentResponse>> createDeployment(
            @RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.get("name"));
        String dc = String.valueOf(body.get("dc"));
        String gpuSet = String.valueOf(body.get("gpuSet"));
        String model = String.valueOf(body.get("model"));
        int replicas = Integer.parseInt(String.valueOf(body.getOrDefault("replicas", 1)));
        boolean reuse = Boolean.parseBoolean(String.valueOf(body.getOrDefault("reuseContainer", true)));

        CreateDeploymentResponse response = deploymentService.create(name, dc, gpuSet, model, replicas, reuse);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * List all GPU deployments.
     */
    @GetMapping("/deployments")
    public ResponseEntity<ApiResponse<List<DeploymentInfo>>> listDeployments() {
        List<DeploymentInfo> deployments = deploymentService.list();
        return ResponseEntity.ok(ApiResponse.ok(deployments));
    }

    /**
     * Get status of a specific deployment.
     */
    @GetMapping("/deployments/{uuid}")
    public ResponseEntity<ApiResponse<DeploymentInfo>> getDeploymentStatus(
            @PathVariable String uuid) {
        DeploymentInfo info = deploymentService.status(uuid);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    /**
     * Stop a deployment.
     */
    @PostMapping("/deployments/{uuid}/stop")
    public ResponseEntity<ApiResponse<Map<String, String>>> stopDeployment(
            @PathVariable String uuid) {
        try {
            deploymentService.stop(uuid);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "stopped")));
        } catch (Exception e) {
            logger.error("Failed to stop deployment {}: {}", uuid, e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete a deployment.
     */
    @DeleteMapping("/deployments/{uuid}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteDeployment(
            @PathVariable String uuid) {
        try {
            deploymentService.delete(uuid);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "deleted")));
        } catch (Exception e) {
            logger.error("Failed to delete deployment {}: {}", uuid, e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }
}