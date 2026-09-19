package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NseAnnouncementsSourceTest {
    @Test
    void parsesApiAnnouncementsAndLimitsArticles() throws Exception {
        Connection connection = mock(Connection.class);
        Document document = Jsoup.parse("[{\"sm_name\":\"Tata Consultancy Services\",\"desc\":\"Board Meeting for quarterly results\",\"an_dt\":\"15-Sep-2026\",\"attchmntFile\":\"https://nse.example/1\"},{\"sm_name\":\"TCS\",\"desc\":\"Dividend declared\",\"an_dt\":\"14-Sep-2026\",\"attchmntFile\":\"https://nse.example/2\"}]");
        stubConnection(connection);
        when(connection.get()).thenReturn(document);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(connection);
            List<NewsArticle> articles = new NseAnnouncementsSource(1).fetch("TCS");

            assertThat(articles).hasSize(1);
            assertThat(articles.get(0).title()).isEqualTo("Tata Consultancy Services - Board Meeting for quarterly results");
            assertThat(articles.get(0).source()).isEqualTo("nse");
            assertThat(articles.get(0).publishedDate().toLocalDate().toString()).isEqualTo("2026-09-15");
        }
    }

    private void stubConnection(Connection connection) {
        when(connection.userAgent(anyString())).thenReturn(connection);
        when(connection.header(anyString(), anyString())).thenReturn(connection);
        when(connection.timeout(anyInt())).thenReturn(connection);
        when(connection.ignoreContentType(anyBoolean())).thenReturn(connection);
        when(connection.followRedirects(anyBoolean())).thenReturn(connection);
    }
}
