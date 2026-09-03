package com.swingtrade.data.repository;

import com.swingtrade.data.entity.CandidateScanResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CandidateScanResultRepository extends JpaRepository<CandidateScanResultEntity, Long> {
    List<CandidateScanResultEntity> findByRunIdOrderBySymbolAsc(UUID runId);

    @Query("""
        select r from CandidateScanResultEntity r
        where r.runId = :runId
          and (:symbol = '' or upper(r.symbol) like upper(concat('%', :symbol, '%')))
          and (:signalType = '' or r.signalType = :signalType)
        order by r.symbol asc
        """)
    Page<CandidateScanResultEntity> search(
        @Param("runId") UUID runId,
        @Param("symbol") String symbol,
        @Param("signalType") String signalType,
        Pageable pageable);
}
