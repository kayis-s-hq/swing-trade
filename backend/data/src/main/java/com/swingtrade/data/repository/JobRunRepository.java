package com.swingtrade.data.repository;

import com.swingtrade.data.entity.JobRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRunRepository extends JpaRepository<JobRunEntity, Long> {
    Optional<JobRunEntity> findByRunId(UUID runId);
    java.util.List<JobRunEntity> findAllByOrderByStartedAtDesc();
    java.util.List<JobRunEntity> findByStatusOrderByStartedAtDesc(String status);

    /**
     * Atomically increments completedCount by 1. Defense in depth against the
     * read-modify-write race condition in JobOrchestratorService.recordCompletion.
     */
    @Modifying
    @Query("UPDATE JobRunEntity j SET j.completedCount = j.completedCount + 1 WHERE j.runId = :runId")
    int incrementCompletedCount(@Param("runId") UUID runId);
}