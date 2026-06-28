package com.swingtrade.service;

import com.swingtrade.model.Stock;
import com.swingtrade.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    private static final String STOCK_CACHE_KEY = "stock:";
    private static final long CACHE_TTL_MINUTES = 30;

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Stock> getStockById(Long id) {
        return stockRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Stock> getStockBySymbol(String symbol) {
        String cacheKey = STOCK_CACHE_KEY + symbol.toUpperCase();
        
        // Check cache first
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for stock: {}", symbol);
            return Optional.of((Stock) cached);
        }
        
        log.debug("Cache miss for stock: {}", symbol);
        Optional<Stock> stock = stockRepository.findBySymbol(symbol);
        
        // Update cache
        stock.ifPresent(s -> {
            redisTemplate.opsForValue().set(cacheKey, s, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        });
        
        return stock;
    }

    @Transactional
    public Stock createStock(Stock stock) {
        Stock saved = stockRepository.save(stock);
        invalidateCache(stock.getSymbol());
        log.info("Created stock: {}", stock.getSymbol());
        return saved;
    }

    @Transactional
    public Stock updateStock(Long id, Stock stock) {
        return stockRepository.findById(id)
                .map(existing -> {
                    existing.setName(stock.getName());
                    existing.setCurrentPrice(stock.getCurrentPrice());
                    existing.setTargetPrice(stock.getTargetPrice());
                    existing.setStopLossPrice(stock.getStopLossPrice());
                    existing.setEntryPrice(stock.getEntryPrice());
                    existing.setRiskRewardRatio(stock.getRiskRewardRatio());
                    existing.setStatus(stock.getStatus());
                    existing.setAnalysisNotes(stock.getAnalysisNotes());
                    Stock updated = stockRepository.save(existing);
                    invalidateCache(updated.getSymbol());
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Stock not found with id: " + id));
    }

    @Transactional
    public void deleteStock(Long id) {
        stockRepository.findById(id).ifPresent(stock -> {
            invalidateCache(stock.getSymbol());
            stockRepository.delete(stock);
            log.info("Deleted stock: {}", stock.getSymbol());
        });
    }

    private void invalidateCache(String symbol) {
        String cacheKey = STOCK_CACHE_KEY + symbol.toUpperCase();
        redisTemplate.delete(cacheKey);
        log.debug("Invalidated cache for stock: {}", symbol);
    }
}
