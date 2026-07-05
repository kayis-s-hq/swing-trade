package com.swingtrade.data.repository;

import com.swingtrade.data.entity.NewsItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsItemRepository extends JpaRepository<NewsItemEntity, Long> {

    List<NewsItemEntity> findBySymbolOrderByPublishedAtDesc(String symbol);

    @Query("SELECT n FROM NewsItemEntity n WHERE n.symbol = :symbol AND n.publishedAt > :since ORDER BY n.publishedAt DESC")
    List<NewsItemEntity> findRecentBySymbol(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    long countBySymbol(String symbol);
}