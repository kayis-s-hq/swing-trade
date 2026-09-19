package com.swingtrade.broker.service;

import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.PaperPortfolioSummary;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.ShadowPositionView;
import com.swingtrade.domain.service.PortfolioQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Read-only portfolio queries over {@code paper_trading_portfolio} and portfolio-tagged positions. */
@Service
@Transactional(readOnly = true)
public class PortfolioQueryServiceImpl implements PortfolioQueryService {

    private static final String STATUS_OPEN = "OPEN";

    private final PaperTradingPortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;

    public PortfolioQueryServiceImpl(PaperTradingPortfolioRepository portfolioRepository,
                                     PositionRepository positionRepository) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
    }

    @Override
    public List<PaperPortfolioSummary> listPortfolios() {
        return portfolioRepository.findAll().stream()
            .sorted(Comparator.comparing(p -> p.getPortfolioId() == null ? "" : p.getPortfolioId()))
            .map(p -> new PaperPortfolioSummary(p.getPortfolioId(), p.getInitialCapital(),
                p.getCurrentCapital(), p.getTotalRealizedPnl(), p.getOpenPositionCount()))
            .toList();
    }

    @Override
    public List<ShadowPositionView> listPositions(String portfolioId) {
        if (portfolioId == null) {
            return List.of();
        }
        return positionRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(portfolioId).stream()
            .map(PortfolioQueryServiceImpl::toView).toList();
    }

    @Override
    public List<ShadowPositionView> listPositionsForSymbol(String symbol) {
        if (symbol == null) {
            return List.of();
        }
        return positionRepository.findBySymbolAndPortfolioIdIsNotNullOrderByEntryDateDescIdDesc(symbol).stream()
            .map(PortfolioQueryServiceImpl::toView).toList();
    }

    @Override
    public List<ShadowClosedTrade> findClosedTrades(String portfolioId) {
        return listPositions(portfolioId).stream()
            .filter(v -> !STATUS_OPEN.equals(v.status()))
            .sorted(Comparator.comparing(ShadowPositionView::exitDate,
                Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())))
            .map(v -> new ShadowClosedTrade(v.portfolioId(), v.symbol(), v.entryDate(), v.exitDate(),
                v.entryPrice(), v.exitPrice(), v.stopLoss(), v.target(), v.quantity(), v.exitReason(), v.pnl()))
            .toList();
    }

    /** Position rows carry OPEN, CLOSED, STOPPED or TARGET_HIT; the view collapses all non-OPEN to CLOSED. */
    private static ShadowPositionView toView(PositionEntity e) {
        boolean open = STATUS_OPEN.equals(e.getStatus());
        return new ShadowPositionView(e.getPortfolioId(), e.getSymbol(), e.getEntryDate(), e.getEntryPrice(),
            e.getStopLoss(), e.getTarget(), e.getQuantity(), open ? "OPEN" : "CLOSED",
            open || e.getExitTime() == null ? null : e.getExitTime().toLocalDate(),
            open ? null : e.getCurrentPrice(), open ? null : e.getExitReason(),
            open ? null : e.getRealizedPnL());
    }
}
