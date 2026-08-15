package com.swingtrade.gpuhub.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.gpuhub.dto.CreateDeploymentRequest;
import com.swingtrade.gpuhub.dto.CreateDeploymentResponse;
import com.swingtrade.gpuhub.dto.DeploymentInfo;
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
 * Base: https://www.gpuhub.com/api/v1/dev/deployment
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
        } catch (JsonProcessingException e) {
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

    // ==================== Create ====================

    public Mono<CreateDeploymentResponse> createDeployment(CreateDeploymentRequest request) {
        logger.info("Creating GPUHUB deployment: name={}, type={}, dc={}, gpu={}, model={}",
                request.getName(), request.getDeploymentType(),
                request.getContainerTemplate().getDcList(),
                request.getContainerTemplate().getGpuNameSet(),
                request.getContainerTemplate().getModel());

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

    // ==================== List ====================

    public Mono<List<DeploymentInfo>> listDeployments() {
        return webClient
                .get()
                .uri("/api/v1/dev/deployment")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    JsonNode data = checkSuccess(node);
                    if (data == null || !data.isArray()) return List.of();
                    return mapper.convertValue(data, new TypeReference<List<DeploymentInfo>>() {});
                });
    }

    // ==================== Status ====================

    public Mono<DeploymentInfo> getDeploymentStatus(String deploymentUuid) {
        return webClient
                .get()
                .uri("/api/v1/dev/deployment/{uuid}", deploymentUuid)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    JsonNode data = checkSuccess(node);
                    return toValue(data, DeploymentInfo.class);
                });
    }

    // ==================== Stop ====================

    public Mono<Void> stopDeployment(String deploymentUuid) {
        logger.info("Stopping GPUHUB deployment: {}", deploymentUuid);

        return webClient
                .post()
                .uri("/api/v1/dev/deployment/{uuid}/stop", deploymentUuid)
                .bodyValue(Map.of())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    checkSuccess(node);
                    return (Void) null;
                })
                .doOnSuccess(r -> logger.info("GPUHUB deployment stopped: {}", deploymentUuid))
                .doOnError(error -> logger.error("GPUHUB deployment stop failed: {}", error.getMessage()));
    }

    // ==================== Delete ====================

    public Mono<Void> deleteDeployment(String deploymentUuid) {
        logger.info("Deleting GPUHUB deployment: {}", deploymentUuid);

        return webClient
                .delete()
                .uri("/api/v1/dev/deployment/{uuid}", deploymentUuid)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    checkSuccess(node);
                    return (Void) null;
                })
                .doOnSuccess(r -> logger.info("GPUHUB deployment deleted: {}", deploymentUuid))
                .doOnError(error -> logger.error("GPUHUB deployment delete failed: {}", error.getMessage()));
    }

    // ==================== Custom exception ====================

    public static class GpuHubApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public GpuHubApiException(String message) {
            super(message);
        }
    }
}