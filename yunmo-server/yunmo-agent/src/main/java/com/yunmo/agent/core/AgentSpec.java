package com.yunmo.agent.core;

import com.yunmo.common.enums.AgentType;

import java.util.List;

/**
 * Agent 配置规格 — 替代 Python create_agent() 参数
 */
public record AgentSpec(
    AgentType type,
    String name,
    String description,
    String systemPrompt,
    List<String> toolNames,
    String provider,
    String model
) {
    public static AgentSpec of(AgentType type, String systemPrompt,
                                List<String> toolNames, String provider, String model) {
        return new AgentSpec(type, type.getRole(), type.getDescription(),
                systemPrompt, toolNames, provider, model);
    }
}
