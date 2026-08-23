package com.swingtrade.gpuhub.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.swingtrade.gpuhub.dto.CreateDeploymentResponse;
import com.swingtrade.gpuhub.dto.DeploymentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GpuHubDeploymentClientTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
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
}