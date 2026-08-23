package com.swingtrade.api.test.integration;

import tools.jackson.databind.ObjectMapper;
import com.swingtrade.api.app.SwingTradeApiApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base integration test class for API endpoints.
 * Uses TestContainers for PostgreSQL database.
 *
 * Usage: Extend this class and add @Test methods
 *
 * Example:
 * <pre>
 * {@literal @}TestInstance
 * {@literal @}ActiveProfiles("test")
 * class SignalControllerIntegrationTest extends ApiIntegrationTest {
 *     {@literal @}Autowired
 *     private MockMvc mockMvc;
 *
 *     {@literal @}Test
 *     void testGetLatestSignals() throws Exception {
 *         mockMvc.perform(get("/api/signals/latest"))
 *             .andExpect(status().isOk());
 *     }
 * }
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SwingTradeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API Integration Tests")
public abstract class ApiIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Start TestContainer before all tests in the test class.
     */
    @BeforeAll
    static void beforeAll() {
        DatabaseTestContainer.start();
    }

    /**
     * Stop TestContainer after all tests in the test class complete.
     */
    @AfterAll
    static void afterAll() {
        DatabaseTestContainer.stop();
    }

    /**
     * Verify TestContainer is running.
     */
    protected void verifyTestContainerRunning() {
        assertTrue(DatabaseTestContainer.isRunning(),
                "TestContainer should be running for integration tests");
    }

    /**
     * Get the test database URL.
     */
    protected String getTestDatabaseUrl() {
        return DatabaseTestContainer.getJdbcUrl();
    }

    /**
     * Perform a GET request with JSON content type.
     */
    protected RequestBuilder getJsonRequest() {
        return get("/api/health")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
    }

    /**
     * Perform a GET request without JSON content type.
     */
    protected RequestBuilder getRequest(String url) {
        return get(url);
    }

    /**
     * Verify health endpoint returns OK.
     */
    @Test
    @Tag("integration")
    @DisplayName("Health endpoint returns OK")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(getJsonRequest())
                .andExpect(status().isOk());
    }
}
