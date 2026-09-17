package com.swingtrade.data.repository;

import com.swingtrade.data.entity.StrategyConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategyConfigRepository extends JpaRepository<StrategyConfigEntity, Long> {
    Optional<StrategyConfigEntity> findByVariantIdAndVersion(String variantId, int version);
    Optional<StrategyConfigEntity> findByVariantIdAndCurrentTrue(String variantId);
    List<StrategyConfigEntity> findByVariantIdOrderByVersionDesc(String variantId);
}
