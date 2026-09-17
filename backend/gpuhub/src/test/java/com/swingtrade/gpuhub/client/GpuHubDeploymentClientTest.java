package com.swingtrade.gpuhub.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.swingtrade.gpuhub.dto.CreateDeploymentResponse;
import com.swingtrade.gpuhub.dto.DeploymentInfo;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpuHubDeploymentClientTest {

    private ObjectMapper mapper;
    private MockWebServer server;
    private GpuHubDeploymentClient client;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        try {
            server = new MockWebServer();
            server.start();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        client = new GpuHubDeploymentClient(WebClient.builder(), mapper,
            server.url("/").toString().replaceAll("/$", ""), "test-key");
    }

    @AfterEach
    void tearDown() throws java.io.IOException {
        server.shutdown();
    }

    @Test
    void parseCreateDeploymentResponse() throws Exception {
        String json = """
            {
              "code": "Success",
              "data": { "deployment_uuid": "e13256b6d2" },
              "msg": "",
              "request_id": "2211c626bcc25c629b72de8583493083"
            }
            """;

        JsonNode node = mapper.readTree(json);
        JsonNode data = node.get("data");
        CreateDeploymentResponse resp = mapper.treeToValue(data, CreateDeploymentResponse.class);

        assertThat(resp).isNotNull();
        assertThat(resp.getDeploymentUuid()).isEqualTo("e13256b6d2");
    }

    @Test
    void parseListDeploymentsResponse() throws Exception {
        String json = """
            {
              "code": "Success",
              "data": [
                {
                  "deployment_uuid": "uuid-1",
                  "name": "deploy-1",
                  "status": "Running",
                  "deployment_type": "ReplicaSet",
                  "created_at": "2026-08-15T10:00:00Z"
                },
                {
                  "deployment_uuid": "uuid-2",
                  "name": "deploy-2",
                  "status": "Stopped",
                  "deployment_type": "ReplicaSet",
                  "stopped_at": "2026-08-15T12:00:00Z"
                }
              ],
              "msg": "",
              "request_id": "req-1"
            }
            """;

        JsonNode node = mapper.readTree(json);
        JsonNode data = node.get("data");

        assertThat(node.get("code").asText()).isEqualTo("Success");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isEqualTo(2);

        DeploymentInfo info = mapper.treeToValue(data.get(0), DeploymentInfo.class);
        assertThat(info.getDeploymentUuid()).isEqualTo("uuid-1");
        assertThat(info.getStatus()).isEqualTo("Running");
    }

    @Test
    void parseErrorResponse() throws Exception {
        String json = """
            {
              "code": "AuthorizeFailed",
              "data": null,
              "msg": "Login timed out, please log in again.",
              "request_id": "8f9dd237885268055cabbf662745fe16"
            }
            """;

        JsonNode node = mapper.readTree(json);
        assertThat(node.get("code").asText()).isEqualTo("AuthorizeFailed");
        assertThat(node.get("msg").asText()).isEqualTo("Login timed out, please log in again.");
    }

    @Test
    void apiException_serializable() {
        GpuHubDeploymentClient.GpuHubApiException ex =
                new GpuHubDeploymentClient.GpuHubApiException("test error");

        assertThat(ex.getMessage()).isEqualTo("test error");
        assertThat(ex.getStackTrace().length).isGreaterThan(0);
    }

    @Test
    void clientCallsAllDeploymentOperationsAndMapsResponses() throws Exception {
        server.enqueue(json("{\"code\":\"Success\",\"data\":{\"list\":[{\"image_uuid\":\"img-1\",\"image_name\":\"Qwen\",\"id\":3}]}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{\"list\":[{\"deployment_uuid\":\"d-1\",\"status\":\"Running\"}]}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{\"list\":[{\"deployment_uuid\":\"d-1\",\"status\":\"Running\"}]}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{\"list\":[{\"deployment_uuid\":\"d-1\",\"status\":\"Running\"}]}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{\"deployment_uuid\":\"d-1\"}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{\"list\":[{\"uuid\":\"c-1\",\"status\":\"Running\",\"price\":1.5}]}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{}}"));

        assertThat(client.listPrivateImages(1, 10).block()).hasSize(1);
        assertThat(client.listDeployments(1, 10).block()).hasSize(1);
        assertThat(client.listDeploymentsByUuid("d-1").block()).hasSize(1);
        assertThat(client.getDeploymentStatus("d-1").block().getDeploymentUuid()).isEqualTo("d-1");
        assertThat(client.createDeployment(com.swingtrade.gpuhub.dto.CreateDeploymentRequest.builder()
            .name("demo").deploymentType("ReplicaSet").replicaNum(1)
            .reuseContainer(true).reuseContainerScope("all")
            .containerTemplate(com.swingtrade.gpuhub.dto.ContainerTemplate.defaultTemplate()).build())
            .block().getDeploymentUuid()).isEqualTo("d-1");
        assertThat(client.listContainers("d-1").block()).hasSize(1);
        client.stopDeployment("d-1").block();
        client.deleteDeployment("d-1").block();
        assertThat(server.getRequestCount()).isEqualTo(8);
    }

    @Test
    void clientHandlesEmptyListsAndApiErrors() {
        server.enqueue(json("{\"code\":\"Success\",\"data\":{}}"));
        server.enqueue(json("{\"code\":\"Success\",\"data\":{}}"));
        server.enqueue(json("{\"code\":\"Denied\",\"msg\":\"no access\",\"data\":null}"));
        server.enqueue(json("{\"data\":{}}"));

        assertThat(client.listPrivateImages(1, 1).block()).isEmpty();
        assertThat(client.listDeployments(1, 1).block()).isEmpty();
        assertThatThrownBy(() -> client.listDeploymentsByUuid("bad").block())
            .isInstanceOf(GpuHubDeploymentClient.GpuHubApiException.class);
        assertThat(client.listContainers("bad").block()).isEmpty();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
