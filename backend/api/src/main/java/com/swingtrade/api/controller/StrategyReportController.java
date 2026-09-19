package com.swingtrade.api.controller;

import com.swingtrade.api.service.StrategyAttributionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Strategy attribution report: which variant wins the signal tournament and how its picks perform. */
@RestController
@RequestMapping("/api/strategy-report")
public class StrategyReportController {

    private final StrategyAttributionService service;

    public StrategyReportController(StrategyAttributionService service) {
        this.service = service;
    }

    /** Defaults to the last 30 days. */
    @GetMapping
    public StrategyAttributionService.Report report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        return service.report(start, end);
    }
}
