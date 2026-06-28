package com.swingtrade.data.repository;

import com.swingtrade.data.entity.WatchlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistEntity, Long> {

    List<WatchlistEntity> findByIsActiveTrueOrderBySymbolAsc();

    Optional<WatchlistEntity> findBySymbol(String symbol);

    boolean existsBySymbol(String symbol);

    List<WatchlistEntity> findByIsActiveAndExchange(Boolean isActive, String exchange);
}
