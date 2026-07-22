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

    boolean existsBySymbolAndAnalysisDate(String symbol, LocalDate analysisDate);

    Optional<SentimentAccuracyEntity> findBySymbolAndAnalysisDate(String symbol, LocalDate analysisDate);

    List<SentimentAccuracyEntity> findBySymbolOrderByAnalysisDateDesc(String symbol);

    @Query("SELECT COUNT(s) FROM SentimentAccuracyEntity s")
    long countAll();

    @Query("SELECT COUNT(s) FROM SentimentAccuracyEntity s WHERE s.wasCorrect = true")
    long countCorrect();

    @Query("""
        SELECT COUNT(s) FROM SentimentAccuracyEntity s
        WHERE s.wasCorrect = true AND s.ground_truth_label IN ('UP', 'DOWN')
          AND s.evaluatedAt IS NOT NULL
          AND s.evaluatedAt >= :since
        """)
    long countCorrectDirectionalSince(@Param("since") LocalDate since);

    @Query("""
        SELECT COUNT(s) FROM SentimentAccuracyEntity s
        WHERE s.ground_truth_label IN ('UP', 'DOWN')
          AND s.evaluatedAt IS NOT NULL
          AND s.evaluatedAt >= :since
        """)
    long countDirectionalSince(@Param("since") LocalDate since);

    @Query("SELECT s FROM SentimentAccuracyEntity s " +
           "WHERE s.evaluatedAt IS NOT NULL " +
           "AND s.ground_truth_label IN ('UP', 'DOWN') " +
           "AND s.evaluatedAt >= :since " +
           "ORDER BY s.evaluatedAt DESC")
    List<SentimentAccuracyEntity> findDirectionalSince(@Param("since") LocalDate since);

    @Query("""
        SELECT s.analysisDate, s.llmScore, COUNT(s),
               COUNT(s) FILTER (WHERE s.wasCorrect = true) AS correct,
               AVG(s.llmConfidence), AVG(s.actualReturn5d)
        FROM SentimentAccuracyEntity s
        WHERE s.evaluatedAt IS NOT NULL
        GROUP BY s.analysisDate, s.llmScore
        ORDER BY s.analysisDate DESC
        """)
    List<Object[]> dailyAccuracySummary();

    @Query("""
        SELECT s.marketRegime,
               COUNT(s) AS total,
               AVG(CASE WHEN s.wasCorrect THEN 1.0 ELSE 0.0 END) AS accuracy,
               AVG(s.llmConfidence) AS avgConfidence
        FROM SentimentAccuracyEntity s
        WHERE s.evaluatedAt IS NOT NULL
        GROUP BY s.marketRegime
        ORDER BY accuracy DESC
        """)
    List<Object[]> accuracyByRegime();

    @Query("""
        SELECT s.symbol,
               COUNT(s) AS total,
               AVG(CASE WHEN s.wasCorrect THEN 1.0 ELSE 0.0 END) AS accuracy,
               AVG(s.llmConfidence) AS avgConfidence
        FROM SentimentAccuracyEntity s
        WHERE s.evaluatedAt IS NOT NULL
        GROUP BY s.symbol
        ORDER BY total DESC
        """)
    List<Object[]> accuracyBySymbol();

    @Query("""
        SELECT
            FLOOR(s.llmConfidence * 10) / 10 AS confidenceBin,
            AVG(CASE WHEN s.wasCorrect THEN 1.0 ELSE 0.0 END) AS actualAccuracy,
            AVG(s.llmConfidence) AS predictedConfidence,
            COUNT(*) AS count
        FROM SentimentAccuracyEntity s
        WHERE s.evaluatedAt IS NOT NULL
          AND s.llmConfidence IS NOT NULL
        GROUP BY confidenceBin
        ORDER BY confidenceBin
        """)
    List<Object[]> calibrationData();

    @Query("""
        SELECT s.numericScore, s.actualReturn5d
        FROM SentimentAccuracyEntity s
        WHERE s.evaluatedAt IS NOT NULL
          AND s.actualReturn5d IS NOT NULL
          AND s.evaluatedAt >= :since
        ORDER BY s.evaluatedAt DESC
        """)
    List<Object[]> scoreReturnPairsSince(@Param("since") LocalDate since);

    @Query("""
        SELECT s.groundTruthLabel, COUNT(s)
        FROM SentimentAccuracyEntity s
        WHERE s.evaluatedAt IS NOT NULL
        GROUP BY s.groundTruthLabel
        ORDER BY s.groundTruthLabel
        """)
    List<Object[]> groundTruthDistribution();

    @Query("""
        SELECT COUNT(s) FROM SentimentAccuracyEntity s
        WHERE s.evaluatedAt >= :since
        """)
    long countSince(@Param("since") LocalDate since);

    @Query("""
        SELECT AVG(CASE WHEN s.wasCorrect THEN 1.0 ELSE 0.0 END)
        FROM SentimentAccuracyEntity s
        WHERE s.evaluatedAt IS NOT NULL
          AND s.evaluatedAt >= :since
        """)
    Double overallAccuracySince(@Param("since") LocalDate since);

    record AccuracySummary(
        long total,
        long correct,
        double accuracyPct,
        double directionalAccuracy,
        double avgConfidence
    ) {}
}