package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fyers_symbol_master", indexes = {
    @Index(name = "ix_fyers_symbol_master_trading", columnList = "trading_symbol"),
    @Index(name = "ux_fyers_symbol_master_symbol", columnList = "fyers_symbol", unique = true)
})
public class FyersSymbolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    protected Integer version = 0;

    @Column(name = "fy_token", nullable = false, unique = true, length = 32)
    private String fyToken;

    @Column(name = "fyers_symbol", nullable = false, length = 64)
    private String fyersSymbol;

    @Column(name = "trading_symbol", nullable = false, length = 32)
    private String tradingSymbol;

    @Column(columnDefinition = "TEXT")
    private String name;

    @Column(nullable = false, length = 16)
    private String exchange = "NSE";

    @Column(length = 16)
    private String segment;

    @Column(name = "lot_size")
    private Integer lotSize;

    @Column(name = "tick_size", precision = 10, scale = 4)
    private BigDecimal tickSize;

    @Column(length = 13)
    private String isin;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public FyersSymbolEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFyToken() { return fyToken; }
    public void setFyToken(String fyToken) { this.fyToken = fyToken; }

    public String getFyersSymbol() { return fyersSymbol; }
    public void setFyersSymbol(String fyersSymbol) { this.fyersSymbol = fyersSymbol; }

    public String getTradingSymbol() { return tradingSymbol; }
    public void setTradingSymbol(String tradingSymbol) { this.tradingSymbol = tradingSymbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }

    public Integer getLotSize() { return lotSize; }
    public void setLotSize(Integer lotSize) { this.lotSize = lotSize; }

    public BigDecimal getTickSize() { return tickSize; }
    public void setTickSize(BigDecimal tickSize) { this.tickSize = tickSize; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
