package com.yunmo.agent.pipeline;

import reactor.core.publisher.Flux;

/**
 * 流水线阶段接口 — 替代 Python LangGraph node
 */
public interface PipelineStage {

    /**
     * 执行阶段逻辑
     * @param state 当前流水线状态（可读写）
     * @return 阶段输出，Engine 会自动合并到全局状态
     */
    StageOutput execute(PipelineState state) throws Exception;

    /**
     * 是否支持流式输出（逐 token 的增量模式）
     * 重写此方法返回 true 时，Engine 会在流式模式下调用 executeStreaming()
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 流式执行 — 返回 Flux<StageEvent>，每个事件可以是一个增量 token
     * 仅在 supportsStreaming() 返回 true 时被调用
     */
    default Flux<StageEvent> executeStreaming(PipelineState state) {
        return Flux.empty();
    }

    /** 阶段名称，用于日志和 SSE phase 标识 */
    default String name() {
        return this.getClass().getSimpleName();
    }
}
