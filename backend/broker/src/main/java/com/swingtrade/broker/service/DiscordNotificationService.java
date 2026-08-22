package com.swingtrade.broker.service;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.domain.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Discord webhook notification service.
 */
@Service
public class DiscordNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotificationService.class);

    static final Duration DISCORD_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String webhookUrl;
    private final boolean enabled;

    // Color constants
    public static final int COLOR_GREEN = 11625876;
    public static final int COLOR_YELLOW = 1554693;
    public static final int COLOR_RED = 15158332;
    public static final int COLOR_BLURPLE = 0x5865F2;

    public DiscordNotificationService(BrokerProperties props) {
        BrokerProperties.Discord discord = props.getDiscord();
        this.webhookUrl = discord.getWebhookUrl();
        this.enabled = discord.isWebhookEnabled();
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(DISCORD_TIMEOUT)))
                .build();
    }

    @Override
    public boolean sendMessage(String content) {
        if (!enabled || webhookUrl.isBlank()) {
            return false;
        }
        return postWebhook(Map.of("content", content));
    }

    public boolean sendEmbed(String title, String description, int color) {
        if (!enabled || webhookUrl.isBlank()) {
            return false;
        }
        Map<String, Object> embed = Map.of(
            "title", title,
            "description", description,
            "color", color
        );
        return postWebhook(Map.of("embeds", List.of(embed)));
    }

    public boolean sendEmbed(String title, String description, int color, List<EmbedField> fields) {
        if (!enabled || webhookUrl.isBlank()) {
            return false;
        }
        Map<String, Object> embed = Map.of(
            "title", title,
            "description", description,
            "color", color,
            "fields", fields.stream().map(EmbedField::toMap).toList()
        );
        return postWebhook(Map.of("embeds", List.of(embed)));
    }

    private boolean postWebhook(Map<String, Object> payload) {
        try {
            webClient.post()
                .uri(webhookUrl)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(10));
            return true;
        } catch (Exception e) {
            log.error("Discord webhook failed: {}", e.getMessage());
            return false;
        }
    }

    public record EmbedField(String name, String value, boolean inline) {
        Map<String, Object> toMap() {
            return Map.of("name", name, "value", value, "inline", inline);
        }
    }
}