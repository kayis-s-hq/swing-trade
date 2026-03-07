package com.swingtrade.data.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the OhlcvCandle domain model.
 * This is stored as a TimescaleDB hypertable for time-series optimization.
 */
@Entity
@Table(name = "ohlcv_candles", indexes = {
    @Index(name = "idx_ohlcv_symbol_date", columnList = "symbol, date", unique = false),
    @Index(name = "idx_ohlcv_date", columnList = "date", unique = false)
})
public class OhlcvCandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "open_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal closePrice;

    private Long volume;

    @Column(name = "adj_close_price", precision = 15, scale = 4)
    private BigDecimal adjClosePrice;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public OhlcvCandleEntity() {
    }

    public OhlcvCandleEntity(com.swingtrade.domain.OhlcvCandle candle) {
        this.symbol = candle.symbol();
        this.date = candle.date();
        this.openPrice = candle.open();
        this.highPrice = candle.high();
        this.lowPrice = candle.low();
        this.closePrice = candle.close();
        this.volume = candle.volume();
        this.adjClosePrice = candle.adjClose();
    }

    public static OhlcvCandleEntity fromDomain(com.swingtrade.domain.OhlcvCandle candle) {
        OhlcvCandleEntity entity = new OhlcvCandleEntity();
        entity.setSymbol(candle.symbol());
        entity.setDate(candle.date());
        entity.setOpenPrice(candle.open());
        entity.setHighPrice(candle.high());
        entity.setLowPrice(candle.low());
        entity.setClosePrice(candle.close());
        entity.setVolume(candle.volume());
        entity.setAdjClosePrice(candle.adjClose());
        return entity;
    }

    public com.swingtrade.domain.OhlcvCandle toDomain() {
        return new com.swingtrade.domain.OhlcvCandle(
            symbol,
            date,
            openPrice,
            highPrice,
            lowPrice,
            closePrice,
            volume,
            adjClosePrice
        );
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public BigDecimal getAdjClosePrice() {
        return adjClosePrice;
    }

    public void setAdjClosePrice(BigDecimal adjClosePrice) {
        this.adjClosePrice = adjClosePrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
