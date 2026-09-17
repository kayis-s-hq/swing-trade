package com.swingtrade.data.store;

import com.swingtrade.data.entity.NewsArticleEntity;
import com.swingtrade.data.repository.NewsArticleRepository;
import com.swingtrade.domain.store.NewsArticleStore;
import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.PersistedNewsArticle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.HexFormat;

@Service
public class NewsArticleStoreImpl implements NewsArticleStore {

    private final NewsArticleRepository repository;

    public NewsArticleStoreImpl(NewsArticleRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public int saveAll(List<NewsArticle> articles) {
        return saveAllReturningPersisted(articles).size();
    }

    @Override
    @Transactional
    public List<PersistedNewsArticle> saveAllReturningPersisted(List<NewsArticle> articles) {
        return articles.stream().map(this::upsert).map(this::toPersisted).toList();
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
    public List<PersistedNewsArticle> findPersistedBySymbolAndPublishedAtBetween(
            String symbol, OffsetDateTime from, OffsetDateTime through) {
        return repository.findBySymbolAndPublishedAtBetween(symbol, from, through).stream()
                .map(this::toPersisted)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersistedNewsArticle> findPersistedBySymbolAndPublishedAtBetweenAndFirstSeenAtBeforeOrEqual(
            String symbol, OffsetDateTime from, OffsetDateTime through, OffsetDateTime firstSeenCutoff) {
        return repository.findPersistedForDecisionWindow(symbol, from, through, firstSeenCutoff).stream()
                .map(this::toPersisted)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NewsArticle> findBySymbolAndPublishedAtBetween(String symbol, OffsetDateTime from,
                                                               OffsetDateTime through) {
        return repository.findBySymbolAndPublishedAtBetween(symbol, from, through).stream()
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
        entity.setArticleKey(articleKey(article));
        entity.setFirstSeenAt(OffsetDateTime.now());
        return entity;
    }

    private NewsArticleEntity upsert(NewsArticle article) {
        String key = articleKey(article);
        NewsArticleEntity entity = repository.findByArticleKey(key).orElseGet(() -> toEntity(article));
        entity.setSymbol(article.symbol());
        entity.setSource(article.source());
        entity.setTitle(article.title());
        entity.setLink(article.link());
        entity.setSummary(article.description());
        entity.setPublishedAt(article.publishedDate() != null ? article.publishedDate().toOffsetDateTime() : null);
        entity.setRawContent(article.rawContent());
        entity.setArticleKey(key);
        if (entity.getFirstSeenAt() == null) entity.setFirstSeenAt(OffsetDateTime.now());
        if (entity.getCreatedAt() == null) entity.setCreatedAt(OffsetDateTime.now());
        return repository.save(entity);
    }

    private String articleKey(NewsArticle article) {
        String identity = nullToEmpty(article.symbol()) + "|" + (article.link() != null && !article.link().isBlank()
                ? article.link().trim()
                : String.join("|", nullToEmpty(article.source()), nullToEmpty(article.title()),
                    article.publishedDate() == null ? "" : article.publishedDate().toInstant().toString()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash news article identity", e);
        }
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value.trim(); }

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

    private PersistedNewsArticle toPersisted(NewsArticleEntity entity) {
        return new PersistedNewsArticle(entity.getId(), toArticle(entity), entity.getFirstSeenAt());
    }
}
