package com.swingtrade.data.entity;

import com.swingtrade.domain.PriceBand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "price_band", uniqueConstraints =
    @UniqueConstraint(name = "uq_price_band_symbol_date", columnNames = {"symbol", "band_date"}))
public class PriceBandEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "band_date", nullable = false)
    private LocalDate date;

    @Column(name = "lower_limit", nullable = false, precision = 15, scale = 4)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", nullable = false, precision = 15, scale = 4)
    private BigDecimal upperLimit;

    public PriceBandEntity() {}

    public static PriceBandEntity fromDomain(PriceBand band) {
        PriceBandEntity entity = new PriceBandEntity();
        entity.symbol = band.symbol();
        entity.date = band.date();
        entity.lowerLimit = band.lowerLimit();
        entity.upperLimit = band.upperLimit();
        return entity;
    }

    public PriceBand toDomain() {
        return new PriceBand(symbol, date, lowerLimit, upperLimit);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public BigDecimal getLowerLimit() { return lowerLimit; }
    public void setLowerLimit(BigDecimal lowerLimit) { this.lowerLimit = lowerLimit; }
    public BigDecimal getUpperLimit() { return upperLimit; }
    public void setUpperLimit(BigDecimal upperLimit) { this.upperLimit = upperLimit; }
}
