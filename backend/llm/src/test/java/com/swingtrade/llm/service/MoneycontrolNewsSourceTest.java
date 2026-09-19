package com.swingtrade.llm.service;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MoneycontrolNewsSourceTest {
    @Test
    void parsesMatchingRssItemsAndAppliesArticleLimit() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("<rss><channel><item><title>TCS wins new contract</title><link></link><description>&lt;p&gt;Order inflow&lt;/p&gt;</description><pubDate>Tue, 15 Sep 2026 10:00:00 +0000</pubDate></item><item><title>TCS wins new contract</title></item></channel></rss>");
        doAnswer(invocation -> response).when(client).send(any(), any());

        var articles = new MoneycontrolNewsSource(1, client).fetch("TCS");
        assertThat(articles).singleElement().satisfies(article -> {
            assertThat(article.link()).isEqualTo("https://www.moneycontrol.com");
            assertThat(article.description()).isEqualTo("Order inflow");
            assertThat(article.source()).isEqualTo("moneycontrol");
        });
    }
}
