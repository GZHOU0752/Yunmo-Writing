package com.yunmo.agent.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CompletableFuture;

/**
 * 章节生成流水线引擎 — 替代 Python build_chapter_graph().compile()
 *
 * 图结构:
 *   assemble_context → preflight (Architect ∥ Guardian 并行)
 *     → write_chapter (SSE 流式输出)
 *     → review_chapter (Guardian → Inspector 串行)
 *     → decide_verdict → pass=END | rewrite=write | regenerate=write
 */
@Component
public class ChapterPipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(ChapterPipelineEngine.class);

    /**
     * 非流式执行 — 替代 Python graph.ainvoke(state)
     * @param definition 流水线图定义
     * @param state 初始状态
     * @return 执行完所有阶段后的最终状态
     */
    public PipelineState execute(ChapterPipelineDefinition definition, PipelineState state) {
        String currentStage = definition.entryPoint();
        int iterations = 0;
        int maxIterations = definition.stages().size() * definition.stages().size();

        while (currentStage != null && !ChapterPipelineDefinition.END.equals(currentStage)) {
            iterations++;
            if (iterations > maxIterations) {
                log.error("[Pipeline] 超过最大迭代次数 {}，可能存在路由死循环", maxIterations);
                state.put("verdict", "pass_forced");
                break;
            }
            PipelineStage stage = definition.stages().get(currentStage);
            if (stage == null) {
                log.error("未找到阶段: {}", currentStage);
                break;
            }
            log.info("[Pipeline] 执行阶段: {}", stage.name());
            try {
                StageOutput output = stage.execute(state);
                mergeOutput(state, output);
            } catch (Exception e) {
                log.error("[Pipeline] 阶段 {} 执行失败", stage.name(), e);
                throw new RuntimeException("Pipeline 阶段 " + stage.name() + " 执行失败", e);
            }

            // 路由决定下一步
            ConditionalRouter router = definition.routers().get(currentStage);
            if (router != null) {
                currentStage = router.route(state);
                if (ChapterPipelineDefinition.END.equals(currentStage)) {
                    log.info("[Pipeline] 流水线结束 (verdict=pass)");
                } else {
                    log.info("[Pipeline] 条件路由 → {}", currentStage);
                }
            } else {
                currentStage = definition.edges().get(currentStage);
            }
        }
        return state;
    }

    /**
     * 流式执行 — 替代 Python graph.astream(state, stream_mode="updates")
     * 通过 Flux<StageEvent> 推送每个阶段的输出，供 Controller 转 SSE
     *
     * 如果某个阶段实现了 executeStreaming()（如 WriteChapterStage），
     * 则该阶段的每个增量 token 都会作为独立的 StageEvent 推送。
     */
    public Flux<StageEvent> executeStream(ChapterPipelineDefinition definition, PipelineState state) {
        return executeStageStreaming(definition, state, definition.entryPoint());
    }

    /** 递归执行各个阶段（支持流式 Stage） */
    private Flux<StageEvent> executeStageStreaming(
            ChapterPipelineDefinition definition, PipelineState state, String stageName
    ) {
        if (stageName == null || ChapterPipelineDefinition.END.equals(stageName)) {
            return Flux.empty();
        }

        PipelineStage stage = definition.stages().get(stageName);
        if (stage == null) {
            log.warn("[Pipeline-Stream] 未找到阶段: {}", stageName);
            return Flux.empty();
        }

        log.info("[Pipeline-Stream] 执行阶段: {}", stage.name());

        Flux<StageEvent> stageFlux;
        if (stage.supportsStreaming()) {
            // 流式阶段 — 每个 token 作为一个独立事件
            stageFlux = stage.executeStreaming(state)
                    .doOnError(e -> log.error("[Pipeline-Stream] 流式阶段 {} 执行失败", stage.name(), e))
                    .onErrorResume(e -> Flux.just(
                            new StageEvent(stage.name(), "error",
                                    StageOutput.of("error", "流式生成失败: " + e.getMessage()))
                    ));
        } else {
            // 非流式阶段 — 在 boundedElastic 线程执行，避免 block() 阻塞事件循环
            stageFlux = Mono.fromCallable(() -> {
                try {
                    StageOutput output = stage.execute(state);
                    mergeOutput(state, output);
                    return new StageEvent(stage.name(), stageName, output);
                } catch (Exception e) {
                    log.error("[Pipeline-Stream] 阶段 {} 执行失败", stage.name(), e);
                    throw new RuntimeException(
                            "Pipeline 阶段 " + stage.name() + " 执行失败", e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flux();
        }

        // 本阶段完成后，路由到下一阶段
        return stageFlux.concatWith(Flux.defer(() -> {
            ConditionalRouter router = definition.routers().get(stageName);
            String nextStage;
            if (router != null) {
                nextStage = router.route(state);
            } else {
                nextStage = definition.edges().get(stageName);
            }
            return executeStageStreaming(definition, state, nextStage);
        }));
    }

    /**
     * 异步执行 — CompletableFuture 包装
     */
    public CompletableFuture<PipelineState> executeAsync(
            ChapterPipelineDefinition definition, PipelineState state
    ) {
        return CompletableFuture.supplyAsync(() -> execute(definition, state));
    }

    /**
     * 响应式执行 — Mono 包装，跑在 boundedElastic 线程池
     */
    public Mono<PipelineState> executeReactive(
            ChapterPipelineDefinition definition, PipelineState state
    ) {
        return Mono.fromCallable(() -> execute(definition, state))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ===== 内部辅助 =====

    /** 将阶段输出合并到全局状态 */
    private void mergeOutput(PipelineState state, StageOutput output) {
        if (output.data() != null && !output.data().isEmpty()) {
            state.merge(output.data());
        }
        if (output.files() != null && !output.files().isEmpty()) {
            state.mergeFiles(output.files());
        }
    }

    /** 流式执行上下文 — 跟踪当前阶段位置 */
    private static class StreamContext {
        final PipelineState state;
        private final ChapterPipelineDefinition definition;
        String currentStageName;

        StreamContext(PipelineState state, String startStage, ChapterPipelineDefinition definition) {
            this.state = state;
            this.currentStageName = startStage;
            this.definition = definition;
        }

        boolean isComplete() {
            return currentStageName == null
                    || ChapterPipelineDefinition.END.equals(currentStageName);
        }

        PipelineStage currentStage() {
            return definition.stages().get(currentStageName);
        }

        void advance() {
            ConditionalRouter router = definition.routers().get(currentStageName);
            if (router != null) {
                currentStageName = router.route(state);
            } else {
                currentStageName = definition.edges().get(currentStageName);
            }
        }
    }
}
