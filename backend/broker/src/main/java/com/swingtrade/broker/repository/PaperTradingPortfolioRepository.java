package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaperTradingPortfolioRepository extends JpaRepository<PaperTradingPortfolioEntity, Long> {
    Optional<PaperTradingPortfolioEntity> findByPortfolioId(String portfolioId);

    /**
     * The highest currently-assigned {@code id}, or 0 if the table is empty. Used by
     * {@code PaperPortfolioServiceImpl.ensurePortfolio()} to pick a fresh surrogate id for a
     * new portfolio row, since this entity deliberately has no {@code @GeneratedValue} (see its
     * class comment).
     */
    @Query("SELECT COALESCE(MAX(p.id), 0) FROM PaperTradingPortfolioEntity p")
    Long findMaxId();

    List<PaperTradingPortfolioEntity> findAllByOrderByPortfolioIdAsc();
}