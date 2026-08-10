package com.swingtrade.data.repository;

import com.swingtrade.data.entity.DailyLossCircuitBreakerStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DailyLossCircuitBreakerStateRepository extends JpaRepository<DailyLossCircuitBreakerStateEntity, Long> {

    Optional<DailyLossCircuitBreakerStateEntity> findById(Long id);

    Optional<DailyLossCircuitBreakerStateEntity> findFirstByOrderByUpdatedAtDesc();
}
