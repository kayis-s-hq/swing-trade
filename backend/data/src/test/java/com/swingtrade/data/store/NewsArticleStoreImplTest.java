package com.swingtrade.data.store;

import com.swingtrade.data.entity.NewsArticleEntity;
import com.swingtrade.data.repository.NewsArticleRepository;
import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.PersistedNewsArticle;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsArticleStoreImplTest {

    @Test
    void saveAllReturningPersistedPreservesIdAndFirstSeen() {
        NewsArticleRepository repository = mock(NewsArticleRepository.class);
        NewsArticleStoreImpl store = new NewsArticleStoreImpl(repository);
        NewsArticle article = article("https://example.test/story");
        NewsArticleEntity persisted = entity(article, 42L, "2026-09-16T10:00:00Z");

        when(repository.findByArticleKey(any())).thenReturn(Optional.empty());
        when(repository.save(any(NewsArticleEntity.class))).thenReturn(persisted);

        List<PersistedNewsArticle> result = store.saveAllReturningPersisted(List.of(article));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(42L);
        assertThat(result.getFirst().firstSeenAt()).isEqualTo(persisted.getFirstSeenAt());
        assertThat(result.getFirst().article().link()).isEqualTo(article.link());
    }

    @Test
    void findPersistedByWindowReturnsEvidenceIdentity() {
        NewsArticleRepository repository = mock(NewsArticleRepository.class);
        NewsArticleStoreImpl store = new NewsArticleStoreImpl(repository);
        NewsArticleEntity persisted = entity(article("https://example.test/story"), 7L, "2026-09-16T10:00:00Z");
        when(repository.findBySymbolAndPublishedAtBetween(any(), any(), any())).thenReturn(List.of(persisted));

        List<PersistedNewsArticle> result = store.findPersistedBySymbolAndPublishedAtBetween(
            "TCS", persisted.getPublishedAt().minusDays(1), persisted.getPublishedAt().plusDays(1));

        assertThat(result).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo(7L);
            assertThat(e.firstSeenAt()).isEqualTo(persisted.getFirstSeenAt());
        });
    }

    private static NewsArticle article(String link) {
        return new NewsArticle("TCS", "Headline", link, "Summary",
            ZonedDateTime.of(2026, 9, 16, 12, 0, 0, 0, ZoneId.of("Asia/Kolkata")),
            "Test", "Content");
    }

    private static NewsArticleEntity entity(NewsArticle article, long id, String firstSeenAt) {
        NewsArticleEntity entity = new NewsArticleEntity();
        entity.setId(id);
        entity.setSymbol(article.symbol());
        entity.setSource(article.source());
        entity.setTitle(article.title());
        entity.setLink(article.link());
        entity.setSummary(article.description());
        entity.setPublishedAt(article.publishedDate().toOffsetDateTime());
        entity.setRawContent(article.rawContent());
        entity.setArticleKey("key");
        entity.setFirstSeenAt(java.time.OffsetDateTime.parse(firstSeenAt));
        return entity;
    }
}
