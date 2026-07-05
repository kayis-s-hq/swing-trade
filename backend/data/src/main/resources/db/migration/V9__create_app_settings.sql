-- V9__create_app_settings.sql

CREATE TABLE app_settings (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(64) NOT NULL UNIQUE,
    value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_settings_key ON app_settings(key);

-- Seed default values
INSERT INTO app_settings (key, value) VALUES
    ('llm.vllm.base_url', 'https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1'),
    ('llm.vllm.model', 'Qwen3-30B-AWQ'),
    ('llm.pdf.base_url', ''),
    ('llm.pdf.model', 'gemma-4-E2B'),
    ('discord.webhook.url', ''),
    ('discord.webhook.enabled', 'false');