package com.swingtrade.data.repository;

import com.swingtrade.data.entity.FyersSymbolEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FyersSymbolRepository extends JpaRepository<FyersSymbolEntity, Long> {

    Optional<FyersSymbolEntity> findByTradingSymbolIgnoreCase(String tradingSymbol);

    @Query("select f from FyersSymbolEntity f where upper(f.tradingSymbol) like upper(concat(:q,'%')) "
        + "or upper(f.name) like upper(concat('%',:q,'%')) order by f.tradingSymbol")
    List<FyersSymbolEntity> search(@Param("q") String q, Pageable page);
}
