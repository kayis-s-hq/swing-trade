package com.swingtrade.llm.service;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EconomicTimesNewsSourceTest {
    @Test
    void parsesMatchingRssItemsAndStripsDescriptionMarkup() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("<rss><channel><item><title>TCS revenue rises</title><link>https://et.example/tcs</link><description>&lt;b&gt;Growth&lt;/b&gt; continues</description><pubDate>Tue, 15 Sep 2026 10:00:00 +0000</pubDate></item><item><title>Another company update</title></item></channel></rss>");
        doAnswer(invocation -> response).when(client).send(any(), any());

        var articles = new EconomicTimesNewsSource(10, client).fetch("TCS");
        assertThat(articles).singleElement().satisfies(article -> {
            assertThat(article.title()).isEqualTo("TCS revenue rises");
            assertThat(article.description()).isEqualTo("Growth continues");
            assertThat(article.source()).isEqualTo("economic_times");
        });
    }
}
