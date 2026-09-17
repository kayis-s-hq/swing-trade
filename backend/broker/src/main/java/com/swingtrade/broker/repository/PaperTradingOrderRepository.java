package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.PaperTradingOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperTradingOrderRepository extends JpaRepository<PaperTradingOrderEntity, Long> {
    Optional<PaperTradingOrderEntity> findByOrderId(String orderId);
    List<PaperTradingOrderEntity> findAllByOrderByCreatedAtDesc();

    /**
     * All orders raised for a specific portfolio (variant), newest first. Lets a caller compute
     * that portfolio's closed round-trip trades (a FILLED BUY paired with a later FILLED SELL for
     * the same symbol) without needing the shared "default"-engine position book — see
     * {@code PortfolioTrade} (strategy module, Phase 4 backtest engine) for the shape a future
     * caller wants; this query is the raw data source for building that per-portfolio.
     */
    List<PaperTradingOrderEntity> findByPortfolioIdOrderByCreatedAtDesc(String portfolioId);
}