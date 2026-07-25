package com.swingtrade.data.store;

import com.swingtrade.data.entity.NewsArticleEntity;
import com.swingtrade.data.repository.NewsArticleRepository;
import com.swingtrade.domain.store.NewsArticleStore;
import com.swingtrade.domain.NewsArticle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class NewsArticleStoreImpl implements NewsArticleStore {

    private final NewsArticleRepository repository;

    public NewsArticleStoreImpl(NewsArticleRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public int saveAll(List<NewsArticle> articles) {
        if (articles.isEmpty()) return 0;

        List<NewsArticleEntity> entities = articles.stream()
                .map(this::toEntity)
                .toList();
        repository.saveAll(entities);
        return entities.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NewsArticle> findRecentBySymbol(String symbol, OffsetDateTime since) {
        return repository.findRecentBySymbol(symbol, since).stream()
                .map(this::toArticle)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countBySymbol(String symbol) {
        return repository.countBySymbol(symbol);
    }

    private NewsArticleEntity toEntity(NewsArticle article) {
        NewsArticleEntity entity = new NewsArticleEntity();
        entity.setSymbol(article.symbol());
        entity.setSource(article.source());
        entity.setTitle(article.title());
        entity.setLink(article.link());
        entity.setSummary(article.description());
        entity.setPublishedAt(article.publishedDate() != null ? article.publishedDate().toOffsetDateTime() : null);
        entity.setRawContent(article.rawContent());
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }

    private NewsArticle toArticle(NewsArticleEntity entity) {
        return new NewsArticle(
                entity.getSymbol(),
                entity.getTitle(),
                entity.getLink(),
                entity.getSummary(),
                entity.getPublishedAt() != null ? entity.getPublishedAt().atZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata")) : null,
                entity.getSource(),
                entity.getRawContent()
        );
    }
}