package com.yunmo.agent.pipeline;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import org.springframework.beans.factory.annotation.Qualifier;
import com.yunmo.common.enums.AgentType;
import com.yunmo.llm.adapter.MultiProviderChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PreFlight 阶段 — Architect ∥ Guardian 并行执行
 * 替代 Python preflight_node + asyncio.gather
 */
@Component
public class PreFlightStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(PreFlightStage.class);
    private final AgentFactory agentFactory;
    private final Executor taskExecutor;
    private Map<AgentType, AgentSpec> agentSpecs;

    public PreFlightStage(AgentFactory agentFactory,
                          @Qualifier("boundedElasticExecutor") Executor taskExecutor) {
        this.agentFactory = agentFactory;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public StageOutput execute(PipelineState state) throws Exception {
        log.info("[PreFlight] 开始预检 — Architect ∥ Guardian 并行");
        ensureSpecs();

        String chapterPlan = state.get("chapter_plan", String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> genreConfig = state.get("genre_config", Map.class);
        String contextText = state.getOrDefault("context_text", String.class, "");

        // 构建 Prompt
        String archPrompt = buildArchitectPrompt(chapterPlan, contextText);
        String guardPrompt = buildGuardianPrompt(chapterPlan, genreConfig);

        // Java 17: CompletableFuture 并行 — 等价 Python asyncio.gather
        var archFuture = CompletableFuture.supplyAsync(() -> {
            var model = agentFactory.createChatModel(agentSpecs.get(AgentType.ARCHITECT));
            return model.generate(
                    SystemMessage.from(agentSpecs.get(AgentType.ARCHITECT).systemPrompt()),
                    UserMessage.from(archPrompt)
            ).content().text();
        }, taskExecutor);

        var guardFuture = CompletableFuture.supplyAsync(() -> {
            var model = agentFactory.createChatModel(agentSpecs.get(AgentType.GUARDIAN));
            return model.generate(
                    SystemMessage.from(agentSpecs.get(AgentType.GUARDIAN).systemPrompt()),
                    UserMessage.from(guardPrompt)
            ).content().text();
        }, taskExecutor);

        // 等待两者完成（120秒超时）
        try {
            CompletableFuture.allOf(archFuture, guardFuture)
                    .orTimeout(120, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            archFuture.cancel(true);
            guardFuture.cancel(true);
            if (e.getCause() instanceof TimeoutException) {
                throw new RuntimeException("PreFlight 预检超时（120秒）", e);
            }
            throw e;
        }

        String archResult = archFuture.get();
        String guardResult = guardFuture.get();

        log.info("[PreFlight] 并行预检完成 — Architect: {} chars, Guardian: {} chars",
                archResult.length(), guardResult.length());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("architect_report", archResult);
        data.put("guardian_pre_check", guardResult);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("architect_report.json", archResult);
        files.put("guardian_pre_check.json", guardResult);
        return StageOutput.withFiles(data, files);
    }

    @Override
    public String name() {
        return "preflight";
    }

    private String buildArchitectPrompt(String chapterPlan, String contextText) {
        return String.format("""
                请分析以下章节写作计划的情节合理性：

                ## 章节计划
                %s

                ## 当前上下文
                %s

                请输出 JSON：
                {"passed": true/false, "concerns": [...], "suggestions": [...], "causal_chain": "..."}
                """, chapterPlan != null ? chapterPlan : "无", contextText);
    }

    @SuppressWarnings("unchecked")
    private String buildGuardianPrompt(String chapterPlan, Map<String, Object> genreConfig) {
        List<String> forbiddenTerms = List.of();
        if (genreConfig != null && genreConfig.containsKey("forbidden_terms")) {
            forbiddenTerms = (List<String>) genreConfig.get("forbidden_terms");
        }
        return String.format("""
                请预检本章写作计划是否违反类型规范：

                ## 章节计划
                %s

                ## 禁止术语列表
                %s

                请输出 JSON：
                {"passed": true/false, "potential_violations": [...]}
                """, chapterPlan != null ? chapterPlan : "无", String.join(", ", forbiddenTerms));
    }

    private void ensureSpecs() {
        if (agentSpecs == null) {
            agentSpecs = agentFactory.createAllSpecs(Map.of());
        }
    }
}
