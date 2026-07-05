package com.swingtrade.data.repository;

import com.swingtrade.data.entity.SentimentAccuracyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SentimentAccuracyRepository extends JpaRepository<SentimentAccuracyEntity, Long> {

    Optional<SentimentAccuracyEntity> findBySymbolAndSignalDate(String symbol, LocalDate signalDate);

    @Query("SELECT s FROM SentimentAccuracyEntity s WHERE s.symbol = :symbol ORDER BY s.recordedAt DESC")
    List<SentimentAccuracyEntity> findBySymbolOrderByRecordedAtDesc(@Param("symbol") String symbol);

    @Query("SELECT COUNT(s) FROM SentimentAccuracyEntity s WHERE s.wasCorrect = true")
    long countCorrect();

    @Query("SELECT COUNT(s) FROM SentimentAccuracyEntity s")
    long countAll();
}