package com.yunmo.common.dto;

/**
 * LLM 请求配置
 */
public record LLMConfig(
    String model,
    int maxTokens,
    double temperature,
    double topP,
    int timeoutSeconds,
    double frequencyPenalty,
    double presencePenalty
) {
    public LLMConfig(String model, int maxTokens, double temperature, double topP, int timeoutSeconds) {
        this(model, maxTokens, temperature, topP, timeoutSeconds, 0.0, 0.0);
    }

    public static LLMConfig defaultConfig(String model) {
        return new LLMConfig(model, 4096, 0.8, 0.9, 180);  // qwen-max 长文可能超120s
    }

    public static LLMConfig creative(String model) {
        // 网文创作参数：高温度+惩罚参数减少AI重复用词
        return new LLMConfig(model, 8192, 1.0, 0.95, 300, 0.3, 0.6);
    }

    public static LLMConfig precise(String model) {
        return new LLMConfig(model, 2048, 0.2, 0.85, 90);
    }

    /** 大纲生成专用 — 全书规划需要更大输出空间 */
    public static LLMConfig outline(String model) {
        return new LLMConfig(model, 16384, 0.9, 0.92, 300, 0.2, 0.4);
    }
}
