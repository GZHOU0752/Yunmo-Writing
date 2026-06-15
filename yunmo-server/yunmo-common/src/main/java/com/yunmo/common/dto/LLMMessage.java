package com.yunmo.common.dto;

/**
 * LLM 消息 — 替代 Python LLMMessage dataclass
 */
public record LLMMessage(String role, String content) {

    public static LLMMessage system(String content) {
        return new LLMMessage("system", content);
    }

    public static LLMMessage user(String content) {
        return new LLMMessage("user", content);
    }

    public static LLMMessage assistant(String content) {
        return new LLMMessage("assistant", content);
    }
}
