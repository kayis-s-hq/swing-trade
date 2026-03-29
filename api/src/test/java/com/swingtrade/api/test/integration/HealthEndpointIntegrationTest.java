package com.swingtrade.api.test.integration;

import com.swingtrade.api.app.SwingTradeApiApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for health endpoint.
 * Extends ApiIntegrationTest to use TestContainer PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SwingTradeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Health Endpoint Integration Tests")
class HealthEndpointIntegrationTest extends ApiIntegrationTest {

    @Test
    @Tag("integration")
    @DisplayName("Health endpoint returns OK")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
