package com.swingtrade.api.controller;

import com.swingtrade.api.dto.StrategyConfigResponse;
import com.swingtrade.api.service.StrategyConfigService;
import com.swingtrade.domain.StrategyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyConfigControllerTest {
    private final StrategyConfigService service = mock(StrategyConfigService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StrategyConfigController(service)).build();

    @Test
    void listsConfigsByMode() throws Exception {
        when(service.list(null, StrategyConfig.Mode.SHADOW)).thenReturn(List.of(response("A", 1, StrategyConfig.Mode.SHADOW)));

        mvc.perform(get("/api/strategy-configs").param("mode", "SHADOW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].variantId").value("A"));
    }

    @Test
    void createsConfigAndValidatesRequiredFields() throws Exception {
        when(service.create(any())).thenReturn(response("A", 1, StrategyConfig.Mode.SHADOW));
        String body = """
            {"variantId":"A","strategyType":"BREAKOUT","params":{"rsiMin":50},"mode":"SHADOW","paperCapital":500000}
            """;

        mvc.perform(post("/api/strategy-configs").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.paramsHash").isNotEmpty());

        mvc.perform(post("/api/strategy-configs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategyType\":\"BREAKOUT\"}"))
            .andExpect(status().isBadRequest());
    }

    private static StrategyConfigResponse response(String variantId, int version, StrategyConfig.Mode mode) {
        return StrategyConfigResponse.from(StrategyConfig.create(variantId, version, "BREAKOUT", Map.of(), Map.of(),
            mode, BigDecimal.valueOf(500_000), true, null, LocalDateTime.now()));
    }
}
