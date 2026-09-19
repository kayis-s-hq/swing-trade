package com.swingtrade.domain.service;

import com.swingtrade.domain.PaperPortfolioSummary;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.ShadowPositionView;

import java.util.List;

/**
 * Read-only view over the paper portfolios (each strategy variant's book plus the "selected"
 * tournament book). Implemented in the broker module over {@code paper_trading_portfolio} and the
 * portfolio-tagged {@code positions} rows written by {@link VariantTradingService}, so the api
 * module can list books and score their trades without depending on broker internals.
 */
public interface PortfolioQueryService {

    /** Every known portfolio with its capital and realized P&amp;L, ordered by portfolio id. */
    List<PaperPortfolioSummary> listPortfolios();

    /** All positions (open and closed) in {@code portfolioId}, most recently entered first. */
    List<ShadowPositionView> listPositions(String portfolioId);

    /** All portfolio-tagged positions on {@code symbol} across every portfolio, most recent first. */
    List<ShadowPositionView> listPositionsForSymbol(String symbol);

    /** Closed round-trip trades in {@code portfolioId}, most recently exited first. */
    List<ShadowClosedTrade> findClosedTrades(String portfolioId);
}
