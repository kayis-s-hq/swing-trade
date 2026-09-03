package com.swingtrade.data.repository;

import com.swingtrade.data.entity.LlmAnalysisAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LlmAnalysisAuditRepository extends JpaRepository<LlmAnalysisAuditEntity, Long> {
}
