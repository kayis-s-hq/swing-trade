package com.swingtrade.data.store;

import com.swingtrade.data.entity.WatchlistEntity;
import com.swingtrade.data.repository.WatchlistRepository;
import com.swingtrade.domain.Stock;
import com.swingtrade.domain.store.WatchlistStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WatchlistStoreImpl implements WatchlistStore {

    private final WatchlistRepository repository;

    public WatchlistStoreImpl(WatchlistRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Stock> getWatchlist() {
        return repository.findByIsActiveTrueOrderBySymbolAsc().stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void addToWatchlist(String symbol) {
        if (!existsBySymbol(symbol)) {
            WatchlistEntity entity = new WatchlistEntity();
            entity.setSymbol(symbol);
            entity.setIsActive(true);
            repository.save(entity);
        }
    }

    @Override
    public void removeFromWatchlist(String symbol) {
        repository.findBySymbol(symbol).ifPresent(e -> {
            e.setIsActive(false);
            repository.save(e);
        });
    }

    @Override
    public boolean existsBySymbol(String symbol) {
        return repository.existsBySymbol(symbol);
    }

    @Override
    public List<Stock> getWatchlistByExchange(String exchange) {
        return repository.findByIsActiveAndExchange(true, exchange).stream()
            .map(this::toDomain)
            .toList();
    }

    private Stock toDomain(WatchlistEntity entity) {
        return new Stock(
            entity.getSymbol(),
            Stock.Exchange.valueOf(entity.getExchange() != null ? entity.getExchange() : "NSE"),
            entity.getName(),
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}