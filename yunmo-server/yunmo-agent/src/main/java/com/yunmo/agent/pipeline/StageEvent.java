package com.yunmo.agent.pipeline;

/**
 * SSE 流式阶段事件 — 对应 Python graph.astream() 推送的每个节点输出
 */
public record StageEvent(String stageName, String phase, StageOutput output, long timestamp) {

    public StageEvent(String stageName, String phase, StageOutput output) {
        this(stageName, phase, output, System.currentTimeMillis());
    }
}
