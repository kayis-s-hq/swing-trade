package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperTradingSnapshotRepository extends JpaRepository<PaperTradingSnapshotEntity, Long> {
    List<PaperTradingSnapshotEntity> findAllByOrderBySnapshotTimeDesc();
}