package com.swingtrade.api.controller;

import com.swingtrade.api.service.GateEffectivenessAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GateEffectivenessSignalControllerTest {
    private final GateEffectivenessAuditService auditService = mock(GateEffectivenessAuditService.class);
    private final MockMvc mockMvc = controllerWith(auditService);

    @Test
    void acceptsOptionalStrategyAndRegimeDimensions() throws Exception {
        var from = LocalDate.of(2026, 8, 1);
        var to = LocalDate.of(2026, 8, 31);
        when(auditService.report(from, to, "TCS", "DEFAULT", "BULL"))
            .thenReturn(new GateEffectivenessAuditService.EffectivenessReport(
                from, to, "TCS", "DEFAULT", "BULL", 0, Map.of(), Map.of(), Map.of()));

        mockMvc.perform(get("/api/signals/gate-effectiveness")
                .param("from", from.toString()).param("to", to.toString())
                .param("symbol", " tcs ").param("strategy", " default ").param("regime", " bull "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategy").value("DEFAULT"))
            .andExpect(jsonPath("$.regime").value("BULL"));

        verify(auditService).report(eq(from), eq(to), eq("TCS"), eq("DEFAULT"), eq("BULL"));
    }

    @Test
    void rejectsReversedDateRange() throws Exception {
        mockMvc.perform(get("/api/signals/gate-effectiveness")
                .param("from", "2026-08-31").param("to", "2026-08-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("from must not be after to"));
    }

    private static MockMvc controllerWith(GateEffectivenessAuditService service) {
        var controller = new SignalController();
        ReflectionTestUtils.setField(controller, "gateEffectivenessAuditService", service);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }
}
