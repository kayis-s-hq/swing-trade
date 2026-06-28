package com.swingtrade.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for operations requiring a stock symbol.
 * Used for retrieving signal, position, and trading data for specific symbols.
 */
public class SymbolRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    public SymbolRequest() {
    }

    public SymbolRequest(String symbol) {
        this.symbol = symbol.toUpperCase();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol.toUpperCase();
    }

    /**
     * Validates that the symbol is a valid stock symbol (alphanumeric, 1-10 characters).
     * @return true if valid
     */
    public boolean isValid() {
        return symbol != null && symbol.matches("[A-Za-z0-9]{1,10}");
    }
}
