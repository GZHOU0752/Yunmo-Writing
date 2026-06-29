package com.yunmo.agent.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 章节生成流水线引擎 — 替代 Python build_chapter_graph().compile()
 *
 * 图结构:
 *   assemble_context → debate_outline → preflight → pleasure_beat
 *     → write_chapter → polish_chapter → adversarial_edit
 *     → decide_verdict → pass=END | rewrite=polish | regenerate=write
 */
@Component
public class ChapterPipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(ChapterPipelineEngine.class);

    /** 专用线程池 — 不被 Reactor 管理，避免 LLM 调用被打断 */
    private final Scheduler blockingScheduler;
    /** 并行阶段执行器 — 复用注入的线程池，避免每次创建新线程池 */
    private final java.util.concurrent.Executor pipelineParallelExecutor;

    public ChapterPipelineEngine(
            @org.springframework.beans.factory.annotation.Qualifier("pipelineParallelExecutor")
            java.util.concurrent.Executor pipelineParallelExecutor
    ) {
        this.pipelineParallelExecutor = pipelineParallelExecutor;
        this.blockingScheduler = Schedulers.fromExecutor(pipelineParallelExecutor);
    }

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

            // 并行阶段: 当前阶段完成后，并行执行其 parallelEdges 中的阶段
            List<String> parallelStages = definition.parallelEdges().get(currentStage);
            if (parallelStages != null && !parallelStages.isEmpty()) {
                log.info("[Pipeline] 并行执行 {} 个阶段: {}", parallelStages.size(), parallelStages);
                executeParallel(definition, state, parallelStages);
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
            // 非流式阶段 — 在专用线程池执行，避免 Reactor 中断 LLM 调用
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
            .subscribeOn(blockingScheduler)
            .flux();
        }

        // 并行阶段 + 本阶段完成后路由到下一阶段
        return stageFlux.concatWith(Flux.defer(() -> {
            // 先执行并行阶段（必须等待完成后再路由）
            List<String> parallelStages = definition.parallelEdges().get(stageName);
            Flux<StageEvent> parallelFlux = (parallelStages != null && !parallelStages.isEmpty())
                ? executeParallelStreaming(definition, state, parallelStages)
                : Flux.empty();

            // 等并行阶段全部完成后，再检查路由
            return parallelFlux.thenMany(Flux.defer(() -> {
                ConditionalRouter router = definition.routers().get(stageName);
                String nextStage;
                if (router != null) {
                    nextStage = router.route(state);
                } else {
                    nextStage = definition.edges().get(stageName);
                }
                return executeStageStreaming(definition, state, nextStage);
            }));
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
     * 响应式执行 — Mono 包装，跑在专用线程池避免 Reactor 中断
     */
    public Mono<PipelineState> executeReactive(
            ChapterPipelineDefinition definition, PipelineState state
    ) {
        return Mono.fromCallable(() -> execute(definition, state))
                .subscribeOn(blockingScheduler);
    }

    /** 并行执行多个阶段，合并所有输出到 state */
    private void executeParallel(ChapterPipelineDefinition definition, PipelineState state,
                                  List<String> stageNames) {
        // 复用注入的线程池，避免每次创建新线程池导致线程泄露
        List<CompletableFuture<StageOutput>> futures = stageNames.stream()
            .map(name -> CompletableFuture.supplyAsync(() -> {
                PipelineStage s = definition.stages().get(name);
                if (s == null) {
                    log.warn("[Pipeline-Parallel] 未找到阶段: {}", name);
                    return StageOutput.empty();
                }
                log.info("[Pipeline-Parallel] 执行: {}", name);
                try {
                    return s.execute(state);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, pipelineParallelExecutor))
            .toList();

        // 等待全部完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<StageOutput> f : futures) {
            try {
                mergeOutput(state, f.get());
            } catch (Exception e) {
                log.error("[Pipeline-Parallel] 阶段执行失败", e);
                throw new RuntimeException("并行阶段执行失败", e);
            }
        }
    }

    /** 流式并行：多个阶段并发流式执行，Flux.merge 合并输出 */
    private Flux<StageEvent> executeParallelStreaming(ChapterPipelineDefinition definition,
                                                       PipelineState state,
                                                       List<String> stageNames) {
        List<Flux<StageEvent>> fluxes = stageNames.stream()
            .map(name -> {
                PipelineStage s = definition.stages().get(name);
                if (s == null) return Flux.<StageEvent>empty();
                log.info("[Pipeline-Stream-Parallel] 执行: {}", name);
                if (s.supportsStreaming()) {
                    return s.executeStreaming(state)
                        .doOnNext(e -> {
                            if (!"token".equals(e.phase())) {
                                mergeOutput(state, e.output());
                            }
                        });
                } else {
                    return Mono.fromCallable(() -> {
                        StageOutput out = s.execute(state);
                        mergeOutput(state, out);
                        return new StageEvent(name, name, out);
                    })
                    .subscribeOn(blockingScheduler)
                    .flux();
                }
            })
            .toList();

        return Flux.merge(fluxes);
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

}
