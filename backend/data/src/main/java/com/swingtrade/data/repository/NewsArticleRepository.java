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

    @Query("SELECT n FROM NewsArticleEntity n WHERE n.symbol = :symbol "
            + "AND n.publishedAt >= :from AND n.publishedAt <= :through "
            + "ORDER BY n.publishedAt ASC, n.id ASC")
    List<NewsArticleEntity> findBySymbolAndPublishedAtBetween(@Param("symbol") String symbol,
                                                               @Param("from") OffsetDateTime from,
                                                               @Param("through") OffsetDateTime through);

    List<NewsArticleEntity> findBySymbolOrderByPublishedAtDesc(String symbol);

    long countBySymbol(String symbol);
}
