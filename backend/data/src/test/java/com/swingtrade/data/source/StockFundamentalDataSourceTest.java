package com.swingtrade.data.source;

import com.swingtrade.domain.Stock;
import com.swingtrade.domain.store.StockStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockFundamentalDataSourceTest {

    @Test
    void findBySymbol_normalizes_symbol_and_maps_stock_fundamentals() {
        StockStore stockStore = mock(StockStore.class);
        Stock stock = new Stock("TEST", Stock.Exchange.NSE, "Test", Stock.Sector.IT,
            "Software", 1_000L, BigDecimal.valueOf(12.5), null, null, null);
        when(stockStore.findBySymbol("TEST")).thenReturn(Optional.of(stock));

        var result = new StockFundamentalDataSource(stockStore).findBySymbol(" test ");

        assertTrue(result.isPresent());
        assertEquals(1_000L, result.get().marketCap());
        assertEquals(BigDecimal.valueOf(12.5), result.get().peRatio());
    }

    @Test
    void findBySymbol_returns_empty_when_stock_has_no_fundamentals() {
        StockStore stockStore = mock(StockStore.class);
        Stock stock = new Stock("TEST", Stock.Exchange.NSE, "Test", null,
            null, null, null, null, null, null);
        when(stockStore.findBySymbol("TEST")).thenReturn(Optional.of(stock));

        assertTrue(new StockFundamentalDataSource(stockStore).findBySymbol("TEST").isEmpty());
    }
}
