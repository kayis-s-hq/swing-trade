package com.swingtrade.broker.config;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerPropertiesTest {
    @Test
    void bindsTopLevelRiskAndModeProperties() {
        BrokerProperties p = new BrokerProperties();
        p.setMaxConcurrentPositions(3); p.setMaxCapitalPerPosition(BigDecimal.TEN);
        p.setMaxCapitalPerTrade(BigDecimal.ONE); p.setInitialCapital(BigDecimal.valueOf(99));
        p.setMaxPositionSizePercentage(BigDecimal.valueOf(4)); p.setMinPositionSizePercentage(BigDecimal.TWO);
        p.setKillSwitchEnabled(false); p.setKillSwitchActive(true);
        p.setDailyLossCircuitBreaker(BigDecimal.valueOf(1.5)); p.setMode("live");
        assertThat(p.getMaxConcurrentPositions()).isEqualTo(3);
        assertThat(p.getMaxCapitalPerPosition()).isEqualByComparingTo("10");
        assertThat(p.getMaxCapitalPerTrade()).isEqualByComparingTo("1");
        assertThat(p.getInitialCapital()).isEqualByComparingTo("99");
        assertThat(p.getMaxPositionSizePercentage()).isEqualByComparingTo("4");
        assertThat(p.getMinPositionSizePercentage()).isEqualByComparingTo("2");
        assertThat(p.isKillSwitchEnabled()).isFalse(); assertThat(p.isKillSwitchActive()).isTrue();
        assertThat(p.getDailyLossCircuitBreaker()).isEqualByComparingTo("1.5");
        assertThat(p.getMode()).isEqualTo("live");
    }

    @Test
    void bindsNotificationAndBrokerNestedProperties() {
        BrokerProperties p = new BrokerProperties();
        p.getTelegram().setBotToken("bot"); p.getTelegram().setBotEnabled(false);
        p.getSignal().setEnabled(true); p.getSignal().setNotifyOnTrades(false);
        p.getSignal().setNotifyOnSignals(true); p.getSignal().setNotifyOnErrors(true);
        p.getSignal().setMaxMessageLength(10); p.getSignal().getApi().setToken("t");
        p.getSignal().getApi().setChatId("c"); p.getSignal().getQuietHours().setEnabled(true);
        p.getSignal().getQuietHours().setStart(1); p.getSignal().getQuietHours().setEnd(2);
        p.getKite().setApiKey("key"); p.getKite().setAccessToken("access");
        p.getKite().setEnvironment("sandbox"); p.getKite().setProxyHost("localhost");
        p.getKite().setProxyPort(8080); p.getDiscord().setWebhookUrl("url");
        p.getDiscord().setWebhookEnabled(true);
        assertThat(p.getTelegram().getBotToken()).isEqualTo("bot"); assertThat(p.getTelegram().isBotEnabled()).isFalse();
        assertThat(p.getSignal().isEnabled()).isTrue(); assertThat(p.getSignal().isNotifyOnTrades()).isFalse();
        assertThat(p.getSignal().isNotifyOnSignals()).isTrue(); assertThat(p.getSignal().isNotifyOnErrors()).isTrue();
        assertThat(p.getSignal().getMaxMessageLength()).isEqualTo(10);
        assertThat(p.getSignal().getApi().getToken()).isEqualTo("t"); assertThat(p.getSignal().getApi().getChatId()).isEqualTo("c");
        assertThat(p.getSignal().getQuietHours().isEnabled()).isTrue(); assertThat(p.getSignal().getQuietHours().getStart()).isEqualTo(1);
        assertThat(p.getSignal().getQuietHours().getEnd()).isEqualTo(2);
        assertThat(p.getKite().isConfigured()).isTrue(); assertThat(p.getKite().isSandbox()).isTrue();
        assertThat(p.getKite().isLive()).isFalse(); assertThat(p.getKite().getAccessToken()).isEqualTo("access");
        assertThat(p.getKite().getProxyHost()).isEqualTo("localhost"); assertThat(p.getKite().getProxyPort()).isEqualTo(8080);
        assertThat(p.getDiscord().getWebhookUrl()).isEqualTo("url"); assertThat(p.getDiscord().isWebhookEnabled()).isTrue();
    }
}
