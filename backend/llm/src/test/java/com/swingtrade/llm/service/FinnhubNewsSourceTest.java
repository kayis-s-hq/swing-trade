package com.swingtrade.llm.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FinnhubNewsSourceTest {
    @Test
    void parsesDeduplicatedNewsAndTruncatesLongSummary() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient client = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec get = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec request = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec response = mock(WebClient.ResponseSpec.class);
        String summary = "x".repeat(1001);
        String json = "[{\"headline\":\"TCS launches platform\",\"url\":\"https://news.example/tcs\",\"summary\":\"" + summary + "\",\"datetime\":1726394400},{\"headline\":\"TCS launches platform\"}]";
        when(builder.build()).thenReturn(client);
        when(client.get()).thenReturn(get);
        when(get.uri(anyString())).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.bodyToMono(String.class)).thenReturn(Mono.just(json));

        var articles = new FinnhubNewsSource("test-key", 10, builder).fetch("TCS");
        assertThat(articles).singleElement().satisfies(article -> {
            assertThat(article.title()).isEqualTo("TCS launches platform");
            assertThat(article.description()).hasSize(1000);
            assertThat(article.source()).isEqualTo("finnhub");
        });
    }
}
