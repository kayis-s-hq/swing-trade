package com.swingtrade.service;

import com.swingtrade.model.Stock;
import com.swingtrade.model.TradeSignal;
import com.swingtrade.repository.StockRepository;
import com.swingtrade.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeSignalService {

    private final TradeSignalRepository tradeSignalRepository;
    private final StockRepository stockRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SIGNAL_CACHE_KEY = "signal:";
    private static final long CACHE_TTL_MINUTES = 15;

    @Transactional(readOnly = true)
    public List<TradeSignal> getAllSignals() {
        return tradeSignalRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<TradeSignal> getSignalsByStockId(Long stockId) {
        return tradeSignalRepository.findByStockId(stockId);
    }

    @Transactional(readOnly = true)
    public List<TradeSignal> getSignalsByStatus(TradeSignal.SignalStatus status) {
        return tradeSignalRepository.findByStatus(status);
    }

    @Transactional
    public TradeSignal createSignal(TradeSignal signal) {
        // Verify stock exists
        Stock stock = stockRepository.findById(signal.getStock().getId())
                .orElseThrow(() -> new RuntimeException("Stock not found with id: " + signal.getStock().getId()));
        
        signal.setStock(stock);
        TradeSignal saved = tradeSignalRepository.save(signal);
        
        // Cache the signal
        String cacheKey = SIGNAL_CACHE_KEY + saved.getId();
        redisTemplate.opsForValue().set(cacheKey, saved, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        
        log.info("Created trade signal: {} for stock: {}", signal.getType(), stock.getSymbol());
        return saved;
    }

    @Transactional
    public TradeSignal updateSignalStatus(Long id, TradeSignal.SignalStatus status) {
        return tradeSignalRepository.findById(id)
                .map(signal -> {
                    signal.setStatus(status);
                    signal.setUpdatedAt(java.time.LocalDateTime.now());
                    TradeSignal updated = tradeSignalRepository.save(signal);
                    
                    // Invalidate cache
                    String cacheKey = SIGNAL_CACHE_KEY + updated.getId();
                    redisTemplate.delete(cacheKey);
                    
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Signal not found with id: " + id));
    }

    @Transactional
    public void deleteSignal(Long id) {
        tradeSignalRepository.findById(id).ifPresent(signal -> {
            String cacheKey = SIGNAL_CACHE_KEY + signal.getId();
            redisTemplate.delete(cacheKey);
            tradeSignalRepository.delete(signal);
            log.info("Deleted trade signal: {}", id);
        });
    }
}
