package com.swingtrade.data.store;

import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.data.repository.PositionSummaryProjection;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.PositionSummary;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.store.PositionStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PositionStoreImpl implements PositionStore {

    private final PositionRepository repository;

    public PositionStoreImpl(PositionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Position> findAllOpen() {
        return repository.findAllOpenPositions().stream()
            .map(PositionEntity::toDomain)
            .toList();
    }

    @Override
    public List<PositionSummary> findOpenSummaries() {
        return repository.findOpenSummaries().stream()
            .map(PositionStoreImpl::toSummary)
            .toList();
    }

    private static PositionSummary toSummary(PositionSummaryProjection row) {
        return new PositionSummary(
            row.getId(),
            row.getSymbol(),
            row.getStatus() != null ? PositionStatus.valueOf(row.getStatus()) : null,
            row.getDirection() != null ? TradeDirection.valueOf(row.getDirection()) : null,
            row.getEntryPrice(),
            row.getQuantity(),
            row.getCurrentPrice(),
            row.getUnrealizedPnL(),
            row.getStopLoss(),
            row.getTarget(),
            row.getBrokerType());
    }

    @Override
    public Optional<Position> findById(Long id) {
        return repository.findById(id).map(PositionEntity::toDomain);
    }

    @Override
    public Optional<Position> findBySymbol(String symbol) {
        return repository.findOpenBySymbol(symbol).map(PositionEntity::toDomain);
    }

    @Override
    public List<Position> findByStatus(com.swingtrade.domain.PositionStatus status) {
        return repository.findByStatus(status.name()).stream()
            .map(PositionEntity::toDomain)
            .toList();
    }

    @Override
    public Position save(Position position) {
        return repository.save(PositionEntity.fromDomain(position)).toDomain();
    }

    @Override
    public boolean existsOpenBySymbol(String symbol) {
        return repository.existsOpenBySymbol(symbol);
    }

    @Override
    public List<Position> findAll() {
        return repository.findAll().stream()
            .map(PositionEntity::toDomain)
            .toList();
    }

    @Override
    public List<Position> findBySymbolOrderByEntryDateDesc(String symbol) {
        return repository.findBySymbolOrderByEntryDateDesc(symbol).stream()
            .map(PositionEntity::toDomain)
            .toList();
    }

    @Override
    public List<Position> findByBrokerType(String brokerType) {
        return repository.findByBrokerType(brokerType).stream()
            .map(PositionEntity::toDomain)
            .toList();
    }
}