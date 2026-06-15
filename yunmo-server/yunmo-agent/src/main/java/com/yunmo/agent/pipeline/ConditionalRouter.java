package com.yunmo.agent.pipeline;

/**
 * 条件路由 — 替代 Python LangGraph conditional_edges
 * 根据当前状态决定下一阶段名称
 */
@FunctionalInterface
public interface ConditionalRouter {

    /** 流水线结束标记 */
    String END = "__END__";

    /**
     * @param state 当前流水线状态
     * @return 下一阶段名称，返回 END 表示流水线结束
     */
    String route(PipelineState state);
}
