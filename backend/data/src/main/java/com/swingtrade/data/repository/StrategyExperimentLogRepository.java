package com.swingtrade.data.repository;

import com.swingtrade.data.entity.StrategyExperimentLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StrategyExperimentLogRepository extends JpaRepository<StrategyExperimentLogEntity, Long> {

    List<StrategyExperimentLogEntity> findByStrategyType(String strategyType);

    /** N = distinct params_hash values tested for a strategy type (plan §6.4, DeflatedSharpeRatio's trial count). */
    @Query("select count(distinct e.paramsHash) from StrategyExperimentLogEntity e where e.strategyType = :strategyType")
    long countDistinctParamsHashByStrategyType(@Param("strategyType") String strategyType);
}
