package com.swingtrade.data.repository;

import com.swingtrade.data.entity.UniverseSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UniverseSnapshotRepository extends JpaRepository<UniverseSnapshotEntity, Long> {
    Optional<UniverseSnapshotEntity> findBySymbolAndSnapshotDate(String symbol, LocalDate snapshotDate);
    Optional<UniverseSnapshotEntity> findFirstBySymbolAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(
        String symbol, LocalDate snapshotDate);
    List<UniverseSnapshotEntity> findBySnapshotDateOrderBySymbol(LocalDate snapshotDate);
}
