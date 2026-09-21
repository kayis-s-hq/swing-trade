package com.swingtrade.api.controller;

import com.swingtrade.strategy.LegacyPriceActionAdapter;
import com.swingtrade.strategy.ParamSchemaValidator;
import com.swingtrade.strategy.PriceActionStrategy;
import com.swingtrade.strategy.PullbackStrategy;
import com.swingtrade.strategy.SqueezeStrategy;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GET /api/strategy-types")
class StrategyTypeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var registry = new StrategyTypeRegistry(List.of(
            new LegacyPriceActionAdapter(new PriceActionStrategy()), new PullbackStrategy(), new SqueezeStrategy()));
        mockMvc = MockMvcBuilders.standaloneSetup(new StrategyTypeController(registry, new ParamSchemaValidator()))
            .build();
    }

    @Test
    void listsEveryRegisteredTypeWithSchemaWarmupAndIndicators() throws Exception {
        mockMvc.perform(get("/api/strategy-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].type").value("BREAKOUT"))
            .andExpect(jsonPath("$.data[1].type").value("PULLBACK"))
            .andExpect(jsonPath("$.data[2].type").value("SQUEEZE"))
            .andExpect(jsonPath("$.data[1].paramSchema.params[0].name").isNotEmpty())
            .andExpect(jsonPath("$.data[1].paramSchema.params[0].type").isNotEmpty())
            .andExpect(jsonPath("$.data[1].warmupBars").isNumber())
            .andExpect(jsonPath("$.data[1].requiredIndicators").isNotEmpty());
    }
}
