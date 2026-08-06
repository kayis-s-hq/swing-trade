package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist", indexes = {
    @Index(name = "idx_watchlist_symbol", columnList = "symbol", unique = true),
    @Index(name = "idx_watchlist_active", columnList = "is_active")
})
public class WatchlistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(length = 255)
    private String name;

    @Column(length = 5)
    private String exchange = "NSE";

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "candle_count")
    private Integer candleCount = 0;

    public WatchlistEntity() {}

    public WatchlistEntity(String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public Integer getCandleCount() { return candleCount; }
    public void setCandleCount(Integer candleCount) { this.candleCount = candleCount; }
}
