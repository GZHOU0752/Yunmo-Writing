package com.yunmo.common.dto;

import java.util.Map;

/**
 * LLM 响应 — 替代 Python LLMResponse dataclass
 */
public record LLMResponse(
    String content,
    String model,
    Map<String, Integer> usage,
    String finishReason
) {
    /** 输入 token 数 */
    public int promptTokens() {
        return usage != null ? usage.getOrDefault("prompt_tokens", 0) : 0;
    }

    /** 输出 token 数 */
    public int completionTokens() {
        return usage != null ? usage.getOrDefault("completion_tokens", 0) : 0;
    }

    /** 总 token 数 */
    public int totalTokens() {
        return usage != null ? usage.getOrDefault("total_tokens", 0) : 0;
    }
}
