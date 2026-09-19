package com.swingtrade.broker.service;

import com.swingtrade.broker.config.BrokerProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordNotificationServiceTest {
    private HttpServer server;
    private int responseStatus;
    private final AtomicInteger requestCount = new AtomicInteger();

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void disabledNotificationsAreSkipped() {
        DiscordNotificationService service = new DiscordNotificationService(new BrokerProperties());
        assertThat(service.sendMessage("ignored")).isFalse();
        assertThat(service.sendEmbed("title", "description", DiscordNotificationService.COLOR_GREEN)).isFalse();
        assertThat(service.sendEmbed("title", "description", DiscordNotificationService.COLOR_RED,
            List.of(new DiscordNotificationService.EmbedField("name", "value", true)))).isFalse();
        assertThat(new DiscordNotificationService.EmbedField("n", "v", false).toMap())
            .containsEntry("name", "n").containsEntry("inline", false);
    }

    @Test
    void postsMessagesAndEmbeds() {
        startServer(204);
        BrokerProperties props = new BrokerProperties();
        props.getDiscord().setWebhookEnabled(true);
        props.getDiscord().setWebhookUrl("http://localhost:" + server.getAddress().getPort() + "/hook");
        DiscordNotificationService service = new DiscordNotificationService(props);

        assertThat(service.sendMessage("hello")).isTrue();
        assertThat(service.sendEmbed("title", "description", DiscordNotificationService.COLOR_BLURPLE)).isTrue();
        assertThat(service.sendEmbed("title", "description", DiscordNotificationService.COLOR_YELLOW,
            List.of(new DiscordNotificationService.EmbedField("name", "value", true)))).isTrue();
        assertThat(requestCount).hasValue(3);
    }

    @Test
    void webhookFailuresAreReportedAsFalse() {
        startServer(500);
        BrokerProperties props = new BrokerProperties();
        props.getDiscord().setWebhookEnabled(true);
        props.getDiscord().setWebhookUrl("http://localhost:" + server.getAddress().getPort() + "/hook");
        assertThat(new DiscordNotificationService(props).sendMessage("fails")).isFalse();
    }

    private void startServer(int status) {
        try {
            responseStatus = status;
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/hook", exchange -> {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(responseStatus, -1);
                exchange.close();
            });
            server.start();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
