package com.swingtrade.data.repository;

import com.swingtrade.data.entity.PdfExtractionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PdfExtractionRepository extends JpaRepository<PdfExtractionEntity, Long> {

    @Query("SELECT p FROM PdfExtractionEntity p WHERE p.symbol = :symbol ORDER BY p.extractionDate DESC LIMIT 1")
    Optional<PdfExtractionEntity> findLatestBySymbol(@Param("symbol") String symbol);
}