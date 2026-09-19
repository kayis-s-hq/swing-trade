package com.swingtrade.data.source;

import com.swingtrade.domain.FundamentalData;
import com.swingtrade.domain.Stock;
import com.swingtrade.domain.source.FundamentalDataSource;
import com.swingtrade.domain.store.StockStore;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/** Reads fundamentals maintained on the stock master; never infers them from candles. */
@Service
public class StockFundamentalDataSource implements FundamentalDataSource {

    private final StockStore stockStore;

    public StockFundamentalDataSource(StockStore stockStore) {
        this.stockStore = stockStore;
    }

    @Override
    public Optional<FundamentalData> findBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        return stockStore.findBySymbol(symbol.trim().toUpperCase(Locale.ROOT))
            .map(this::toFundamentalData)
            .filter(data -> data.marketCap() != null || data.peRatio() != null);
    }

    private FundamentalData toFundamentalData(Stock stock) {
        return new FundamentalData(stock.symbol(), stock.marketCap(), stock.peRatio());
    }
}
