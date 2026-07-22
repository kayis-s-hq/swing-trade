package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.PaperTradingOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperTradingOrderRepository extends JpaRepository<PaperTradingOrderEntity, Long> {
    Optional<PaperTradingOrderEntity> findByOrderId(String orderId);
    List<PaperTradingOrderEntity> findAllByOrderByCreatedAtDesc();
}