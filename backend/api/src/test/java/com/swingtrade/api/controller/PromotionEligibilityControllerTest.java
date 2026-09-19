package com.swingtrade.api.controller;

import com.swingtrade.api.dto.PromotionEligibilityResponse;
import com.swingtrade.api.service.PromotionEligibilityService;
import com.swingtrade.strategy.PromotionEligibilityChecker;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromotionEligibilityControllerTest {
    private final PromotionEligibilityService service = mock(PromotionEligibilityService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PromotionEligibilityController(service)).build();

    @Test
    void returnsEligibilityResultForKnownChallenger() throws Exception {
        when(service.check("CHALLENGER")).thenReturn(new PromotionEligibilityResponse(
            "CHALLENGER", "CHAMPION", PromotionEligibilityChecker.Status.INSUFFICIENT_SAMPLE,
            List.of(), List.of(), List.of("degraded")));

        mvc.perform(get("/api/strategy-configs/CHALLENGER/promotion-eligibility"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.challengerVariantId").value("CHALLENGER"))
            .andExpect(jsonPath("$.data.championVariantId").value("CHAMPION"))
            .andExpect(jsonPath("$.data.status").value("INSUFFICIENT_SAMPLE"));
    }

    @Test
    void bubblesUpIllegalArgumentForUnknownVariant() {
        when(service.check("GHOST")).thenThrow(new IllegalArgumentException("Strategy variant not found: GHOST"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            mvc.perform(get("/api/strategy-configs/GHOST/promotion-eligibility")))
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
