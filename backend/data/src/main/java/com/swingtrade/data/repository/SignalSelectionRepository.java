package com.swingtrade.data.repository;

import com.swingtrade.data.entity.SignalSelectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SignalSelectionRepository extends JpaRepository<SignalSelectionEntity, Long> {

    Optional<SignalSelectionEntity> findBySymbolAndSelectionDate(String symbol, LocalDate selectionDate);

    List<SignalSelectionEntity> findBySelectionDateOrderBySymbolAsc(LocalDate selectionDate);

    List<SignalSelectionEntity> findBySymbolAndStatusOrderBySelectionDateDesc(String symbol, String status);

    List<SignalSelectionEntity> findBySelectionDateBetweenOrderBySelectionDateDescSymbolAsc(
        LocalDate from, LocalDate to);
}
