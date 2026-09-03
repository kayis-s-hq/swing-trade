package com.swingtrade.data.repository;

import com.swingtrade.data.entity.CandidateScanRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateScanRunRepository extends JpaRepository<CandidateScanRunEntity, Long> {
    Optional<CandidateScanRunEntity> findByRunId(UUID runId);
    List<CandidateScanRunEntity> findByStatus(String status);
    List<CandidateScanRunEntity> findTop20ByOrderByStartedAtDesc();
    boolean existsByStatus(String status);
}
