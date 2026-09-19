package com.swingtrade.api.controller;

import com.swingtrade.api.service.SignalArbiter;
import com.swingtrade.data.entity.SignalSelectionEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read API for the per-symbol signal tournament results (which variant's BUY won each day). */
@RestController
@RequestMapping("/api/signal-selections")
public class SignalSelectionController {

    private final SignalArbiter arbiter;

    public SignalSelectionController(SignalArbiter arbiter) {
        this.arbiter = arbiter;
    }

    /** Selections between {@code from} and {@code to} (inclusive); defaults to the last 30 days. */
    @GetMapping
    public List<SelectionResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        return arbiter.findBetween(start, end).stream().map(SelectionResponse::from).toList();
    }

    public record SelectionResponse(Long id, String symbol, LocalDate selectionDate, String winnerVariantId,
                                    int winnerVersion, BigDecimal winnerConfidence,
                                    List<Map<String, Object>> candidates, String reason, String status,
                                    String statusDetail) {
        static SelectionResponse from(SignalSelectionEntity e) {
            return new SelectionResponse(e.getId(), e.getSymbol(), e.getSelectionDate(), e.getWinnerVariantId(),
                e.getWinnerVersion(), e.getWinnerConfidence(), e.getCandidates(), e.getReason(), e.getStatus(),
                e.getStatusDetail());
        }
    }
}
