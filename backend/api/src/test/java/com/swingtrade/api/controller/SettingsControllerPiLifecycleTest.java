package com.swingtrade.api.controller;

import com.swingtrade.api.config.SettingsTestConfiguration;
import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.data.service.AppSettingsService;
import com.swingtrade.data.service.MarketDataClientProvider;
import com.swingtrade.llm.service.LlamaCppServerManager;
import com.swingtrade.llm.service.LlmBackendSelector;
import com.swingtrade.llm.service.LlmClientProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for Pi server lifecycle endpoints in SettingsController.
 * Tests POST /settings/pi/start, POST /settings/pi/stop, GET /settings/pi/status.
 */
@WebMvcTest(SettingsController.class)
@ContextConfiguration(classes = SettingsTestConfiguration.class)
class SettingsControllerPiLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataClientProvider marketDataClientProvider;

    @MockBean
    private AppSettingsService appSettingsService;

    @MockBean
    private DiscordNotificationService discordNotificationService;

    @MockBean
    private LlmBackendSelector llmBackendSelector;

    @MockBean
    private LlmClientProvider llmClientProvider;

    @MockBean
    private LlamaCppServerManager localServerManager;

    @MockBean
    private com.swingtrade.llm.service.PiLlamaServerManager piServerManager;

    @Nested
    @DisplayName("POST /api/settings/pi/start")
    class StartEndpoint {

        @Test
        @DisplayName("should return 200 with success when server starts")
        void shouldReturn200WithSuccessWhenServerStarts() throws Exception {
            // Arrange
            when(piServerManager.isRunning()).thenReturn(true);

            // Act + Assert
            mockMvc.perform(post("/api/settings/pi/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.running").value(true))
                .andExpect(jsonPath("$.data.message").value("Pi llama-server started and healthy"));
        }

        @Test
        @DisplayName("should return 200 with error when start fails")
        void shouldReturn200WithErrorWhenStartFails() throws Exception {
            // Arrange
            when(piServerManager.isRunning()).thenReturn(false);
            doThrow(new IllegalStateException("SSH failed")).when(piServerManager).ensureRunning();

            // Act + Assert
            mockMvc.perform(post("/api/settings/pi/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.running").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/settings/pi/stop")
    class StopEndpoint {

        @Test
        @DisplayName("should return 200 with stopped status")
        void shouldReturn200WithStoppedStatus() throws Exception {
            // Arrange
            doNothing().when(piServerManager).stop();

            // Act + Assert
            mockMvc.perform(post("/api/settings/pi/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.running").value(false))
                .andExpect(jsonPath("$.data.message").value("Pi llama-server stopped"));
        }
    }

    @Nested
    @DisplayName("GET /api/settings/pi/status")
    class StatusEndpoint {

        @Test
        @DisplayName("should return running status")
        void shouldReturnRunningStatus() throws Exception {
            // Arrange
            when(piServerManager.isRunning()).thenReturn(true);

            // Act + Assert
            mockMvc.perform(get("/api/settings/pi/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.running").value(true));
        }

        @Test
        @DisplayName("should return stopped status")
        void shouldReturnStoppedStatus() throws Exception {
            // Arrange
            when(piServerManager.isRunning()).thenReturn(false);

            // Act + Assert
            mockMvc.perform(get("/api/settings/pi/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.running").value(false));
        }
    }
}