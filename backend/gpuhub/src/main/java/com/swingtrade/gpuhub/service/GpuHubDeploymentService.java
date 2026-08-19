package com.swingtrade.gpuhub.service;

import com.swingtrade.gpuhub.client.GpuHubDeploymentClient;
import com.swingtrade.gpuhub.client.GpuHubDeploymentClient.GpuHubApiException;
import com.swingtrade.gpuhub.dto.ContainerInfo;
import com.swingtrade.gpuhub.dto.ContainerTemplate;
import com.swingtrade.gpuhub.dto.CreateDeploymentRequest;
import com.swingtrade.gpuhub.dto.CreateDeploymentResponse;
import com.swingtrade.gpuhub.dto.DeploymentInfo;
import com.swingtrade.gpuhub.dto.PrivateImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Service for managing GPUHUB elastic deployments.
 * Wraps the client with blocking calls for synchronous use cases.
 */
@Service
public class GpuHubDeploymentService {

    private static final Logger logger = LoggerFactory.getLogger(GpuHubDeploymentService.class);
    private static final long TIMEOUT_SECONDS = 120;

    private final GpuHubDeploymentClient client;

    public GpuHubDeploymentService(GpuHubDeploymentClient client) {
        this.client = client;
    }

    /**
     * Create a new GPU deployment and block until it returns.
     */
    public CreateDeploymentResponse create(String name, String dc, String gpuSet,
                                           String imageUuid, int replicas,
                                           boolean reuseContainer, String cmd) {
        ContainerTemplate template = ContainerTemplate.defaultTemplate();
        template.setDcList(List.of(dc));
        template.setGpuNameSet(List.of(gpuSet));
        template.setImageUuid(imageUuid);
        template.setCmd(cmd);

        CreateDeploymentRequest request = CreateDeploymentRequest.builder()
                .name(name)
                .deploymentType("ReplicaSet")
                .replicaNum(replicas)
                .reuseContainer(reuseContainer)
                .reuseContainerScope("all")
                .containerTemplate(template)
                .build();

        return client.createDeployment(request).block(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    /**
     * List deployments and block.
     */
    public List<DeploymentInfo> list(int page, int pageSize) {
        return client.listDeployments(page, pageSize).block(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    /**
     * Get status of a specific deployment and block.
     */
    public DeploymentInfo status(String deploymentUuid) {
        List<DeploymentInfo> deployments = client.listDeploymentsByUuid(deploymentUuid)
                .block(Duration.ofSeconds(TIMEOUT_SECONDS));
        return deployments != null && !deployments.isEmpty() ? deployments.get(0) : null;
    }

    /**
     * Stop a deployment and block.
     */
    public void stop(String deploymentUuid) {
        client.stopDeployment(deploymentUuid).block(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    /**
     * Delete a deployment and block.
     */
    public void delete(String deploymentUuid) {
        client.deleteDeployment(deploymentUuid).block(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    /**
     * List containers for a deployment and block.
     */
    public List<ContainerInfo> listContainers(String deploymentUuid) {
        return client.listContainers(deploymentUuid).block(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    /**
     * Stop a specific container and block.
     */
    public void stopContainer(String deploymentContainerUuid) {
        client.stopContainer(deploymentContainerUuid).block(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    /**
     * List private images and block.
     */
    public List<PrivateImage> listPrivateImages(int page, int pageSize) {
        return client.listPrivateImages(page, pageSize).block(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    /**
     * Check if a deployment is running (status = "Running" or similar).
     */
    public boolean isRunning(String deploymentUuid) {
        DeploymentInfo info = status(deploymentUuid);
        if (info == null) return false;
        String s = info.getStatus();
        return s != null && (s.contains("Run") || s.contains("Active") || s.contains("Start"));
    }
}