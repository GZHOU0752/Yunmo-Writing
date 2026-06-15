package com.yunmo.common.dto;

/**
 * LLM 请求配置
 */
public record LLMConfig(
    String model,
    int maxTokens,
    double temperature,
    double topP,
    int timeoutSeconds
) {
    public static LLMConfig defaultConfig(String model) {
        return new LLMConfig(model, 4096, 0.8, 0.9, 120);
    }

    public static LLMConfig creative(String model) {
        return new LLMConfig(model, 8192, 0.9, 0.95, 180);
    }

    public static LLMConfig precise(String model) {
        return new LLMConfig(model, 2048, 0.2, 0.85, 60);
    }
}
