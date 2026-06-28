package com.swingtrade.data.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class OhlcvCandlePK implements Serializable {

    private String symbol;
    private LocalDate date;

    public OhlcvCandlePK() {}

    public OhlcvCandlePK(String symbol, LocalDate date) {
        this.symbol = symbol;
        this.date = date;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OhlcvCandlePK pk)) return false;
        return Objects.equals(symbol, pk.symbol) && Objects.equals(date, pk.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, date);
    }
}
