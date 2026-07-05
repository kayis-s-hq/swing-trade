package com.swingtrade.data.repository;

import com.swingtrade.data.entity.SentimentResultEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for SentimentResultEntity operations.
 */
@Repository
public interface SentimentResultRepository extends JpaRepository<SentimentResultEntity, Long> {

    /**
     * Finds sentiment result by symbol and date.
     *
     * @param symbol the stock symbol
     * @param date the date of analysis
     * @return optional containing the sentiment result
     */
    Optional<SentimentResultEntity> findBySymbolAndDate(String symbol, LocalDate date);

    /**
     * Finds all sentiment results for a symbol.
     *
     * @param symbol the stock symbol
     * @param pageable pagination
     * @return list of sentiment results
     */
    List<SentimentResultEntity> findAllBySymbol(String symbol, Pageable pageable);

    /**
     * Finds all sentiment results within a date range.
     *
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of sentiment results
     */
    List<SentimentResultEntity> findAllByDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Counts sentiment results by date range and score.
     *
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param sentimentScore the sentiment score (POSITIVE, NEUTRAL, NEGATIVE)
     * @return count of sentiment results
     */
    long countByDateBetweenAndSentimentScore(LocalDate startDate, LocalDate endDate, String sentimentScore);

    /**
     * Counts sentiment by symbol and score within a date range.
     *
     * @param start the start date (inclusive)
     * @param end the end date (inclusive)
     * @return array of [symbol, count, sentimentScore]
     */
    @Query("SELECT s.symbol, COUNT(s), s.sentimentScore " +
           "FROM SentimentResultEntity s " +
           "WHERE s.date BETWEEN :start AND :end " +
           "GROUP BY s.symbol, s.sentimentScore")
    List<Object[]> countBySymbolAndSentimentBetween(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    /**
     * Finds all distinct symbols with sentiment results.
     *
     * @return list of distinct symbols
     */
    @Query("SELECT DISTINCT s.symbol FROM SentimentResultEntity s ORDER BY s.symbol")
    List<String> findAllDistinctSymbols();

    @Query("SELECT s FROM SentimentResultEntity s WHERE s.symbol = :symbol ORDER BY s.date DESC LIMIT 1")
    Optional<SentimentResultEntity> findLatestBySymbol(@Param("symbol") String symbol);
}
