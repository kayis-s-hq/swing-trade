package com.swingtrade.data.repository;

import com.swingtrade.data.entity.StrategyConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategyConfigRepository extends JpaRepository<StrategyConfigEntity, Long> {

    Optional<StrategyConfigEntity> findByVariantIdAndIsCurrentTrue(String variantId);

    List<StrategyConfigEntity> findByIsCurrentTrue();

    List<StrategyConfigEntity> findByVariantIdOrderByVersionAsc(String variantId);

    Optional<StrategyConfigEntity> findByVariantIdAndVersion(String variantId, int version);

    Optional<StrategyConfigEntity> findByVariantIdAndParamsHash(String variantId, String paramsHash);

    long countByIsCurrentTrueAndModeIn(List<String> modes);

    Optional<StrategyConfigEntity> findByIsCurrentTrueAndMode(String mode);
}
