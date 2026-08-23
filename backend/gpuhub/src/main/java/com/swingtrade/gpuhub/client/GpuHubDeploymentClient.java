package com.swingtrade.gpuhub.client;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.swingtrade.gpuhub.dto.ContainerInfo;
import com.swingtrade.gpuhub.dto.CreateDeploymentRequest;
import com.swingtrade.gpuhub.dto.CreateDeploymentResponse;
import com.swingtrade.gpuhub.dto.DeploymentInfo;
import com.swingtrade.gpuhub.dto.PrivateImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * REST client for GPUHUB Elastic Deployment API.
 * Base: https://www.gpuhub.com/api/v1/dev
 */
@Component
public class GpuHubDeploymentClient {

    private static final Logger logger = LoggerFactory.getLogger(GpuHubDeploymentClient.class);
    private static final String FIELD_CODE = "code";
    private static final String FIELD_MSG = "msg";
    private static final String FIELD_DATA = "data";
    private static final String STATUS_SUCCESS = "Success";
    private static final String STATUS_UNKNOWN = "Unknown";

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final String apiKey;

    public GpuHubDeploymentClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper mapper,
            @Value("${gpuhub.api.base-url:https://www.gpuhub.com}") String baseUrl,
            @Value("${gpuhub.api.api-key:}") String apiKey) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", apiKey)
                .build();
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    private <T> T toValue(JsonNode node, Class<T> type) {
        try {
            return mapper.treeToValue(node, type);
        } catch (JacksonException e) {
            throw new GpuHubApiException("deserialization failed: " + e.getMessage());
        }
    }

    private JsonNode checkSuccess(JsonNode node) {
        JsonNode codeNode = node.get(FIELD_CODE);
        if (codeNode == null || !STATUS_SUCCESS.equals(codeNode.asText())) {
            String msg = node.has(FIELD_MSG) ? node.get(FIELD_MSG).asText() : "";
            throw new GpuHubApiException(
                    (codeNode != null ? codeNode.asText() : STATUS_UNKNOWN) + ": " + msg);
        }
        return node.get(FIELD_DATA);
    }

    // ==================== Private Images ====================

    public Mono<List<PrivateImage>> listPrivateImages(int page, int pageSize) {
        return webClient
                .post()
                .uri("/api/v1/dev/image/private/list")
                .bodyValue(Map.of("page_index", page, "page_size", pageSize))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    JsonNode data = checkSuccess(node);
                    if (data == null || !data.has("list")) return List.of();
                    return mapper.convertValue(data.get("list"),
                            new TypeReference<List<PrivateImage>>() {});
                });
    }

    // ==================== Create ====================

    public Mono<CreateDeploymentResponse> createDeployment(CreateDeploymentRequest request) {
        logger.info("Creating GPUHUB deployment: name={}, type={}, dc={}, gpu={}, gpuNum={}",
                request.getName(), request.getDeploymentType(),
                request.getContainerTemplate().getDcList(),
                request.getContainerTemplate().getGpuNameSet(),
                request.getContainerTemplate().getGpuNum());

        return webClient
                .post()
                .uri("/api/v1/dev/deployment")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    JsonNode data = checkSuccess(node);
                    return toValue(data, CreateDeploymentResponse.class);
                })
                .doOnSuccess(r -> logger.info("GPUHUB deployment created: uuid={}",
                        r != null ? r.getDeploymentUuid() : "null"))
                .doOnError(error -> logger.error("GPUHUB deployment creation failed: {}", error.getMessage()));
    }

    // ==================== List Deployments ====================

    public Mono<List<DeploymentInfo>> listDeployments(int page, int pageSize) {
        return webClient
                .post()
                .uri("/api/v1/dev/deployment/list")
                .bodyValue(Map.of("page_index", page, "page_size", pageSize))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    JsonNode data = checkSuccess(node);
                    if (data == null || !data.has("list")) return List.of();
                    return mapper.convertValue(data.get("list"),
                            new TypeReference<List<DeploymentInfo>>() {});
                });
    }

    public Mono<List<DeploymentInfo>> listDeploymentsByUuid(String deploymentUuid) {
        return webClient
                .post()
                .uri("/api/v1/dev/deployment/list")
                .bodyValue(Map.of("page_index", 1, "page_size", 1, "deployment_uuid", deploymentUuid))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    JsonNode data = checkSuccess(node);
                    if (data == null || !data.has("list")) return List.of();
                    return mapper.convertValue(data.get("list"),
                            new TypeReference<List<DeploymentInfo>>() {});
                });
    }

    // ==================== Status ====================

    public Mono<DeploymentInfo> getDeploymentStatus(String deploymentUuid) {
        List<DeploymentInfo> deployments = listDeploymentsByUuid(deploymentUuid).block(Duration.ofSeconds(30));
        return Mono.just(deployments != null && !deployments.isEmpty() ? deployments.get(0) : null);
    }

    // ==================== Stop Deployment ====================

    public Mono<Void> stopDeployment(String deploymentUuid) {
        logger.info("Stopping GPUHUB deployment: {}", deploymentUuid);

        return webClient
                .put()
                .uri("/api/v1/dev/deployment/operate")
                .bodyValue(Map.of("deployment_uuid", deploymentUuid, "operation", "stop"))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    checkSuccess(node);
                    return (Void) null;
                })
                .doOnSuccess(r -> logger.info("GPUHUB deployment stopped: {}", deploymentUuid))
                .doOnError(error -> logger.error("GPUHUB deployment stop failed: {}", error.getMessage()));
    }

    // ==================== Delete Deployment ====================

    public Mono<Void> deleteDeployment(String deploymentUuid) {
        logger.info("Deleting GPUHUB deployment: {}", deploymentUuid);

        return webClient
                .method(org.springframework.http.HttpMethod.DELETE)
                .uri("/api/v1/dev/deployment")
                .bodyValue(Map.of("deployment_uuid", deploymentUuid))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    checkSuccess(node);
                    return (Void) null;
                })
                .doOnSuccess(r -> logger.info("GPUHUB deployment deleted: {}", deploymentUuid))
                .doOnError(error -> logger.error("GPUHUB deployment delete failed: {}", error.getMessage()));
    }

    // ==================== List Containers ====================

    public Mono<List<ContainerInfo>> listContainers(String deploymentUuid) {
        return webClient
                .post()
                .uri("/api/v1/dev/deployment/container/list")
                .bodyValue(Map.of("deployment_uuid", deploymentUuid, "page_index", 1, "page_size", 100))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (!node.has(FIELD_CODE)) return List.of();
                    JsonNode codeNode = node.get(FIELD_CODE);
                    if (codeNode != null && STATUS_SUCCESS.equals(codeNode.asText())) {
                        JsonNode data = node.get(FIELD_DATA);
                        if (data != null && data.has("list")) {
                            return mapper.convertValue(data.get("list"),
                                    new TypeReference<List<ContainerInfo>>() {});
                        }
                    }
                    return List.of();
                });
    }

    // ==================== Stop Container ====================

    public Mono<Void> stopContainer(String deploymentContainerUuid) {
        logger.info("Stopping GPUHUB container: {}", deploymentContainerUuid);

        return webClient
                .put()
                .uri("/api/v1/dev/deployment/container/stop")
                .bodyValue(Map.of("deployment_container_uuid", deploymentContainerUuid))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    checkSuccess(node);
                    return (Void) null;
                })
                .doOnSuccess(r -> logger.info("GPUHUB container stopped: {}", deploymentContainerUuid))
                .doOnError(error -> logger.error("GPUHUB container stop failed: {}", error.getMessage()));
    }

    // ==================== Custom exception ====================

    public static class GpuHubApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public GpuHubApiException(String message) {
            super(message);
        }
    }
}