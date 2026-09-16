package com.swingtrade.data.repository;

import com.swingtrade.data.entity.NewsArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, Long> {

    @Query("SELECT n FROM NewsArticleEntity n WHERE n.symbol = :symbol AND n.publishedAt > :since ORDER BY n.publishedAt DESC")
    List<NewsArticleEntity> findRecentBySymbol(@Param("symbol") String symbol, @Param("since") OffsetDateTime since);

    java.util.Optional<NewsArticleEntity> findByArticleKey(String articleKey);

    @Query("SELECT n FROM NewsArticleEntity n WHERE n.symbol = :symbol "
            + "AND n.publishedAt >= :from AND n.publishedAt <= :through "
            + "ORDER BY n.publishedAt ASC, n.id ASC")
    List<NewsArticleEntity> findBySymbolAndPublishedAtBetween(@Param("symbol") String symbol,
                                                               @Param("from") OffsetDateTime from,
                                                               @Param("through") OffsetDateTime through);

    @Query("SELECT n FROM NewsArticleEntity n WHERE n.symbol = :symbol "
            + "AND n.publishedAt >= :from AND n.publishedAt <= :through "
            + "AND n.firstSeenAt IS NOT NULL AND n.firstSeenAt <= :firstSeenCutoff "
            + "ORDER BY n.publishedAt ASC, n.id ASC")
    List<NewsArticleEntity> findPersistedForDecisionWindow(@Param("symbol") String symbol,
                                                            @Param("from") OffsetDateTime from,
                                                            @Param("through") OffsetDateTime through,
                                                            @Param("firstSeenCutoff") OffsetDateTime firstSeenCutoff);

    List<NewsArticleEntity> findBySymbolOrderByPublishedAtDesc(String symbol);

    long countBySymbol(String symbol);
}
