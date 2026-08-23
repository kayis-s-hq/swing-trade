package com.swingtrade.api.controller;

/**
 * Constants for LLM-related setting keys used across the API module.
 */
public final class LlmSettingKeys {

    private LlmSettingKeys() {}

    // Backend selection
    public static final String BACKEND = "llm.backend";
    public static final String BACKEND_LOCAL = "local";
    public static final String BACKEND_PI_SSH = "pi_ssh";
    public static final String BACKEND_OPENAI = "openai";
    public static final String BACKEND_MLX = "mlx";

    // Base URLs
    public static final String BASE_URL = "llm.base_url";
    public static final String OPENAI_BASE_URL = "openai.base_url";
    public static final String MLX_SERVER_URL = "mlx.server.url";
    public static final String PDF_BASE_URL = "llm.pdf.base_url";

    // Models
    public static final String OPENAI_MODEL = "openai.model";
    public static final String LLAMACPP_MODEL = "llamacpp.model";
    public static final String MLX_MODEL = "mlx.model";
    public static final String PDF_MODEL = "llm.pdf.model";

    // API keys
    public static final String OPENAI_API_KEY = "openai.api_key";

    // Defaults
    public static final String DEFAULT_BASE_URL = "http://localhost:8080";
    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-4o";
    public static final String DEFAULT_LLAMACPP_MODEL = "/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf";
    public static final String DEFAULT_MLX_MODEL = "Qwen/Qwen2.5-3B-Instruct";
    public static final String DEFAULT_MLX_SERVER_URL = "http://192.168.1.50:8081";
    public static final String DEFAULT_PDF_BASE_URL = "";
    public static final String DEFAULT_PDF_MODEL = "";
    public static final String DEFAULT_BACKEND = BACKEND_LOCAL;
    public static final String DEFAULT_API_KEY = "";
}