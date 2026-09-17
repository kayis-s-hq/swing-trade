package com.swingtrade.api.controller;

import com.swingtrade.llm.SynthesisEvaluationSummary;
import com.swingtrade.llm.service.SynthesisEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SynthesisEvaluationControllerTest {
    private final SynthesisEvaluationService service = mock(SynthesisEvaluationService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SynthesisEvaluationController(service)).build();

    @Test
    void returnsDurableEvaluationSummary() throws Exception {
        when(service.summary()).thenReturn(new SynthesisEvaluationSummary(4, 2, 1, 50.0,
                Map.of("BUY", 2L)));

        mockMvc.perform(get("/api/synthesis/evaluations/summary"))
                .andExpect(status().isOk());

        verify(service).summary();
    }
}
