package com.swingtrade.api.controller;

import com.swingtrade.api.config.TestConfiguration;
import com.swingtrade.data.service.UpstoxAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UpstoxAuthController.class)
@ContextConfiguration(classes = TestConfiguration.class)
class UpstoxAuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UpstoxAuthService upstoxAuthService;

    @Test
    void loginUrlEndpointReturnsUrl() throws Exception {
        when(upstoxAuthService.generateLoginUrl())
            .thenReturn("https://api.upstox.com/v2/login/authorization/dialog?client_id=test");

        mockMvc.perform(get("/api/auth/upstox/login-url"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loginUrl").value(
                "https://api.upstox.com/v2/login/authorization/dialog?client_id=test"));
    }

    @Test
    void callbackEndpointExchangesCode() throws Exception {
        when(upstoxAuthService.exchangeCodeForToken("auth-code-abc"))
            .thenReturn("access-token-xyz");

        mockMvc.perform(get("/api/auth/upstox/callback").param("code", "auth-code-abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));

        verify(upstoxAuthService).exchangeCodeForToken("auth-code-abc");
    }

    @Test
    void callbackEndpointReturnsBadRequestOnFailure() throws Exception {
        when(upstoxAuthService.exchangeCodeForToken("bad-code"))
            .thenThrow(new RuntimeException("Token exchange failed"));

        mockMvc.perform(get("/api/auth/upstox/callback").param("code", "bad-code"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void validateEndpointReturnsTokenStatus() throws Exception {
        when(upstoxAuthService.validateToken()).thenReturn(true);

        mockMvc.perform(get("/api/auth/upstox/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenValid").value(true));
    }

    @Test
    void setTokenEndpointUpdatesToken() throws Exception {
        mockMvc.perform(post("/api/auth/upstox/token")
                .contentType("application/json")
                .content("{\"accessToken\":\"new-token-abc\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));

        verify(upstoxAuthService).setAccessToken("new-token-abc");
    }
}
