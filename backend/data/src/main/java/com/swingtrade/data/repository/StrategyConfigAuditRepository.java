package com.swingtrade.data.repository;

import com.swingtrade.data.entity.StrategyConfigAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyConfigAuditRepository extends JpaRepository<StrategyConfigAuditEntity, Long> {
}
