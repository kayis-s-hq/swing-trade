package com.swingtrade.data.repository;

import com.swingtrade.data.entity.StrategyBacktestResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StrategyBacktestResultRepository extends JpaRepository<StrategyBacktestResultEntity, Long> {

    List<StrategyBacktestResultEntity> findByVariantIdAndVersionOrderByFoldAsc(String variantId, int version);
}
