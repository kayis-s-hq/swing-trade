package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.PaperTradingPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperTradingPositionRepository extends JpaRepository<PaperTradingPositionEntity, Long> {
    Optional<PaperTradingPositionEntity> findByPositionId(String positionId);
    List<PaperTradingPositionEntity> findAllByStatus(String status);
    default List<PaperTradingPositionEntity> findAllByStatusOpen() {
        return findAllByStatus("OPEN");
    }
    void deleteByPositionId(String positionId);
}