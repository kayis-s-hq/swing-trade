package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.PaperTradingClosedPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperTradingClosedPositionRepository extends JpaRepository<PaperTradingClosedPositionEntity, Long> {
    List<PaperTradingClosedPositionEntity> findAllByOrderByExitTimeDesc();
}