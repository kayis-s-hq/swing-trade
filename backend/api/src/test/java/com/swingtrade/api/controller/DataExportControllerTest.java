package com.swingtrade.api.controller;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataExportControllerTest {

    private final OhlcvCandleRepository repository = mock(OhlcvCandleRepository.class);
    private final DataExportController controller = new DataExportController(repository);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void streamsCsvInStableShapeWithAttachmentHeader() throws Exception {
        OhlcvCandleEntity candle = candle("TCS", LocalDate.of(2026, 1, 2));
        when(repository.findBySymbolsAndDateRange(eq(List.of("TCS")), eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31)), any())).thenReturn(List.of(candle), List.of());

        var response = controller.export(List.of("tcs"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), "csv");
        var body = (org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody) response.getBody();
        var output = new java.io.ByteArrayOutputStream();
        body.writeTo(output);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("ohlcv-export.csv");
        org.assertj.core.api.Assertions.assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("symbol,date,open,high,low,close,volume\n\"TCS\",2026-01-02,\"100.00\",\"105.00\",\"99.00\",\"104.00\",\"5000\"\n");
    }

    @Test
    void rejectsUnsupportedFormatAndReversedRange() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("fromDate", "2026-02-01").param("toDate", "2026-01-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/data/export")
                        .param("fromDate", "2026-01-01").param("toDate", "2026-01-31")
                        .param("format", "xml"))
                .andExpect(status().isBadRequest());
    }

    private static OhlcvCandleEntity candle(String symbol, LocalDate date) {
        OhlcvCandleEntity entity = new OhlcvCandleEntity();
        entity.setSymbol(symbol); entity.setDate(date);
        entity.setOpenPrice(new BigDecimal("100.00")); entity.setHighPrice(new BigDecimal("105.00"));
        entity.setLowPrice(new BigDecimal("99.00")); entity.setClosePrice(new BigDecimal("104.00"));
        entity.setVolume(5000L);
        return entity;
    }
}
