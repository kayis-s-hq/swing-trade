package com.swingtrade.data.repository;

import com.swingtrade.data.entity.JobRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRunRepository extends JpaRepository<JobRunEntity, Long> {
    Optional<JobRunEntity> findByRunId(UUID runId);
    java.util.List<JobRunEntity> findAllByOrderByStartedAtDesc();
    java.util.List<JobRunEntity> findByStatusOrderByStartedAtDesc(String status);
}