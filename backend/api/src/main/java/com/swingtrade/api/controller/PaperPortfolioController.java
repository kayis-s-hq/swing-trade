package com.swingtrade.api.controller;

import com.swingtrade.domain.PaperPortfolioSummary;
import com.swingtrade.domain.ShadowPositionView;
import com.swingtrade.domain.service.PortfolioQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read API over the paper portfolios: each variant's shadow book plus the "selected" book. */
@RestController
@RequestMapping("/api/paper-portfolios")
public class PaperPortfolioController {

    private final PortfolioQueryService service;

    public PaperPortfolioController(PortfolioQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<PaperPortfolioSummary> list() {
        return service.listPortfolios();
    }

    @GetMapping("/{portfolioId}/positions")
    public List<ShadowPositionView> positions(@PathVariable String portfolioId) {
        return service.listPositions(portfolioId);
    }
}
