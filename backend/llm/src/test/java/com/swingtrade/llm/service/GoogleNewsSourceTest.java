package com.swingtrade.llm.service;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class GoogleNewsSourceTest {
    private static final String RSS = "<rss><channel><item><title>TCS earnings beat</title><link>https://news.example/tcs</link><description>&lt;p&gt;Revenue grew&lt;/p&gt;</description><pubDate>Tue, 15 Sep 2026 10:00:00 GMT</pubDate></item></channel></rss>";

    @Test
    void parsesRssItemAndDecodesDescriptionAndDate() throws Exception {
        Method parseXml = GoogleNewsSource.class.getDeclaredMethod("parseXml", String.class);
        parseXml.setAccessible(true);
        List<?> items = (List<?>) parseXml.invoke(new GoogleNewsSource(5, null), RSS);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.toString()).contains("TCS earnings beat", "https://news.example/tcs", "Revenue grew");
            assertThat(item.toString()).contains("2026-09-15T10:00Z[GMT]");
        });
    }
}
