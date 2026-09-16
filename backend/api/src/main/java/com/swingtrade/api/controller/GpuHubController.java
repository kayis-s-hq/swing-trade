package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.gpuhub.dto.ContainerInfo;
import com.swingtrade.gpuhub.dto.DeploymentInfo;
import com.swingtrade.gpuhub.dto.CreateDeploymentResponse;
import com.swingtrade.gpuhub.dto.PrivateImage;
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

    @PostMapping("/deployments")
    public ResponseEntity<ApiResponse<CreateDeploymentResponse>> createDeployment(
            @RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.get("name"));
        String dc = String.valueOf(body.get("dc"));
        String gpuSet = String.valueOf(body.get("gpuSet"));
        String imageUuid = String.valueOf(body.getOrDefault("imageUuid", ""));
        int replicas = Integer.parseInt(String.valueOf(body.getOrDefault("replicas", 1)));
        boolean reuse = Boolean.parseBoolean(String.valueOf(body.getOrDefault("reuseContainer", true)));
        String cmd = String.valueOf(body.getOrDefault("cmd", "python main.py"));
        CreateDeploymentResponse response = deploymentService.create(name, dc, gpuSet, imageUuid, replicas, reuse, cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/deployments")
    public ResponseEntity<ApiResponse<List<DeploymentInfo>>> listDeployments() {
        List<DeploymentInfo> deployments = deploymentService.list(1, 20);
        return ResponseEntity.ok(ApiResponse.ok(deployments));
    }

    @GetMapping("/deployments/{uuid}")
    public ResponseEntity<ApiResponse<DeploymentInfo>> getDeploymentStatus(
            @PathVariable String uuid) {
        DeploymentInfo info = deploymentService.status(uuid);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

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

    @PostMapping("/deployments/{uuid}/containers")
    public ResponseEntity<ApiResponse<List<ContainerInfo>>> listContainers(
            @PathVariable String uuid) {
        List<ContainerInfo> containers = deploymentService.listContainers(uuid);
        return ResponseEntity.ok(ApiResponse.ok(containers));
    }

    @PostMapping("/containers/{containerUuid}/stop")
    public ResponseEntity<ApiResponse<Map<String, String>>> stopContainer(
            @PathVariable String containerUuid) {
        try {
            deploymentService.stopContainer(containerUuid);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "stopped")));
        } catch (Exception e) {
            logger.error("Failed to stop container {}: {}", containerUuid, e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/images")
    public ResponseEntity<ApiResponse<List<PrivateImage>>> listPrivateImages(
            @RequestBody(required = false) Map<String, Object> body) {
        int page = body != null ? Integer.parseInt(String.valueOf(body.getOrDefault("page_index", 1))) : 1;
        int pageSize = body != null ? Integer.parseInt(String.valueOf(body.getOrDefault("page_size", 100))) : 100;
        List<PrivateImage> images = deploymentService.listPrivateImages(page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok(images));
    }
}
