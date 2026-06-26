package com.yunmo.agent.pipeline;

import java.util.*;

/**
 * 流水线图定义 — 替代 Python StateGraph
 * 声明式构建 DAG：stages + edges + conditionalEdges
 */
public class ChapterPipelineDefinition {

    public static final String END = ConditionalRouter.END;

    private String entryPoint;
    private final Map<String, PipelineStage> stages = new LinkedHashMap<>();
    private final Map<String, String> edges = new LinkedHashMap<>();
    private final Map<String, ConditionalRouter> routers = new LinkedHashMap<>();
    /**
     * 并行边: from stage → [stageA, stageB, ...]
     * from 完成后，列表中的阶段并行执行；全部完成后走 from 的普通边
     */
    private final Map<String, List<String>> parallelEdges = new LinkedHashMap<>();

    public String entryPoint() { return entryPoint; }
    public Map<String, PipelineStage> stages() { return Collections.unmodifiableMap(stages); }
    public Map<String, String> edges() { return Collections.unmodifiableMap(edges); }
    public Map<String, ConditionalRouter> routers() { return Collections.unmodifiableMap(routers); }
    public Map<String, List<String>> parallelEdges() { return Collections.unmodifiableMap(parallelEdges); }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String entryPoint;
        private final Map<String, PipelineStage> stages = new LinkedHashMap<>();
        private final Map<String, String> edges = new LinkedHashMap<>();
        private final Map<String, ConditionalRouter> routers = new LinkedHashMap<>();
        private final Map<String, List<String>> parallelEdges = new LinkedHashMap<>();

        public Builder entryPoint(String name) {
            this.entryPoint = name;
            return this;
        }

        public Builder stage(String name, PipelineStage stage) {
            stages.put(name, stage);
            return this;
        }

        /** 顺序边: stageA -> stageB */
        public Builder edge(String from, String to) {
            edges.put(from, to);
            return this;
        }

        /**
         * 并行边: from 完成后，parallel 列表中的阶段并发执行。
         * 全部完成后走 from 的普通边（edges）到下一阶段。
         */
        public Builder parallelEdge(String from, List<String> parallel) {
            parallelEdges.put(from, parallel);
            return this;
        }

        /** 条件边: 从 stage 出发，由 router 决定下一跳 */
        public Builder conditionalEdge(String from, ConditionalRouter router, Map<String, String> routingTable) {
            routers.put(from, state -> {
                String result = router.route(state);
                return routingTable.getOrDefault(result, result);
            });
            return this;
        }

        public ChapterPipelineDefinition build() {
            ChapterPipelineDefinition def = new ChapterPipelineDefinition();
            def.entryPoint = this.entryPoint;
            def.stages.putAll(this.stages);
            def.edges.putAll(this.edges);
            def.routers.putAll(this.routers);
            def.parallelEdges.putAll(this.parallelEdges);
            return def;
        }
    }
}
