package com.swingtrade.data.store;

import com.swingtrade.data.entity.UniverseSnapshotEntity;
import com.swingtrade.data.repository.UniverseSnapshotRepository;
import com.swingtrade.domain.UniverseSnapshot;
import com.swingtrade.domain.store.UniverseSnapshotStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class UniverseSnapshotStoreImpl implements UniverseSnapshotStore {
    private final UniverseSnapshotRepository repository;
    public UniverseSnapshotStoreImpl(UniverseSnapshotRepository repository) { this.repository = repository; }
    public Optional<UniverseSnapshot> findBySymbolAndDate(String symbol, LocalDate date) {
        return repository.findBySymbolAndSnapshotDate(symbol, date).map(UniverseSnapshotEntity::toDomain);
    }
    public Optional<UniverseSnapshot> findLatestBySymbolAndDateOnOrBefore(String symbol, LocalDate date) {
        return repository.findFirstBySymbolAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(symbol, date)
            .map(UniverseSnapshotEntity::toDomain);
    }
    public List<UniverseSnapshot> findByDate(LocalDate date) {
        return repository.findBySnapshotDateOrderBySymbol(date).stream().map(UniverseSnapshotEntity::toDomain).toList();
    }
    public void save(UniverseSnapshot snapshot) {
        UniverseSnapshotEntity entity = repository.findBySymbolAndSnapshotDate(snapshot.symbol(), snapshot.snapshotDate())
            .orElseGet(UniverseSnapshotEntity::new);
        entity.setSymbol(snapshot.symbol()); entity.setSnapshotDate(snapshot.snapshotDate()); entity.setExchange(snapshot.exchange());
        entity.setIsin(snapshot.isin()); entity.setIncluded(snapshot.included()); entity.setSource(snapshot.source());
        entity.setCapturedAt(snapshot.capturedAt()); repository.save(entity);
    }
}
