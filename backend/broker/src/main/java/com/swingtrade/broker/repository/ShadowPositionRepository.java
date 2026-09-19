package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.ShadowPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShadowPositionRepository extends JpaRepository<ShadowPositionEntity, Long> {

    Optional<ShadowPositionEntity> findByPortfolioIdAndSymbolAndStatus(
        String portfolioId, String symbol, String status);

    List<ShadowPositionEntity> findByPortfolioIdAndStatusOrderByExitDateDesc(
        String portfolioId, String status);

    List<ShadowPositionEntity> findByPortfolioIdOrderByEntryDateDescIdDesc(String portfolioId);

    List<ShadowPositionEntity> findBySymbolOrderByEntryDateDescIdDesc(String symbol);
}
