package com.swingtrade.data.repository;

import com.swingtrade.data.entity.CandidateScanResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CandidateScanResultRepository extends JpaRepository<CandidateScanResultEntity, Long> {
    List<CandidateScanResultEntity> findByRunIdOrderBySymbolAsc(UUID runId);
}
