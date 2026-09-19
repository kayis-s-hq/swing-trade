package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaperTradingPortfolioRepository extends JpaRepository<PaperTradingPortfolioEntity, Long> {
    Optional<PaperTradingPortfolioEntity> findByPortfolioId(String portfolioId);
}