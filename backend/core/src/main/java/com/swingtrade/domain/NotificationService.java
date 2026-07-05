package com.swingtrade.domain;

/**
 * Abstraction over notification channels so llm module can send notifications
 * without depending on a specific broker implementation.
 */
public interface NotificationService {
    boolean sendMessage(String content);
}