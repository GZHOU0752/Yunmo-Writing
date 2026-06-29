package com.yunmo.agent.pipeline;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import com.yunmo.common.enums.AgentType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 大纲辩论阶段 — 2个Architect并行生成独立大纲方案，SUPERVISOR择优。
 * 仅在章纲不存在或无详细内容时触发。
 *
 * 参考: make-ur-Agent-writer 的 6-agent×6-round 辩论系统（简化版）
 */
@Component
public class DebateOutlineStage implements PipelinePlugin {
    @Override public int defaultPriority() { return 20; }

    private static final Logger log = LoggerFactory.getLogger(DebateOutlineStage.class);
    private final AgentFactory agentFactory;
    private final Executor taskExecutor;
    private Map<AgentType, AgentSpec> agentSpecs;

    public DebateOutlineStage(AgentFactory agentFactory,
                              @org.springframework.beans.factory.annotation.Qualifier("boundedElasticExecutor") Executor taskExecutor) {
        this.agentFactory = agentFactory;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public String name() {
        return "debate_outline";
    }

    @Override
    public StageOutput execute(PipelineState state) throws Exception {
        ensureSpecs();

        String chapterPlan = state.get("chapter_plan", String.class);
        String contextText = state.get("context_text", String.class);
        int chapterNumber = state.getInt("chapter_number", 0);

        // 判断是否需要辩论：章纲为空或仅有基本信息
        if (chapterPlan != null && isDetailedOutline(chapterPlan)) {
            log.info("[DebateOutline] 章纲已有详细内容，跳过辩论 — chapter={}", chapterNumber);
            return StageOutput.empty();
        }

        log.info("[DebateOutline] 章纲缺乏细节，启动辩论 — chapter={}", chapterNumber);

        // 2个Architect并行生成方案
        var arch1Future = CompletableFuture.supplyAsync(() -> {
            var model = agentFactory.createChatModel(agentSpecs.get(AgentType.ARCHITECT));
            return model.generate(
                SystemMessage.from(agentSpecs.get(AgentType.ARCHITECT).systemPrompt()),
                UserMessage.from(buildArchitectPrompt("A", chapterPlan, contextText, chapterNumber))
            ).content().text();
        }, taskExecutor);

        var arch2Future = CompletableFuture.supplyAsync(() -> {
            var model = agentFactory.createChatModel(agentSpecs.get(AgentType.ARCHITECT));
            return model.generate(
                SystemMessage.from(agentSpecs.get(AgentType.ARCHITECT).systemPrompt()),
                UserMessage.from(buildArchitectPrompt("B", chapterPlan, contextText, chapterNumber))
            ).content().text();
        }, taskExecutor);

        String planA;
        String planB;
        try {
            CompletableFuture.allOf(arch1Future, arch2Future)
                    .orTimeout(90, TimeUnit.SECONDS)
                    .join();
            planA = arch1Future.get();
            planB = arch2Future.get();
        } catch (Exception e) {
            log.warn("[DebateOutline] 辩论超时或失败: {}", e.getMessage());
            return StageOutput.empty();
        }

        log.info("[DebateOutline] 两个方案生成完成 — A:{} chars, B:{} chars",
                planA.length(), planB.length());

        // SUPERVISOR投票选择
        var supervisor = agentFactory.createChatModel(agentSpecs.get(AgentType.SUPERVISOR));
        String judgePrompt = buildJudgePrompt(planA, planB, contextText);
        var judgeResponse = supervisor.generate(
            SystemMessage.from(agentSpecs.get(AgentType.SUPERVISOR).systemPrompt()),
            UserMessage.from(judgePrompt)
        );
        String verdict = judgeResponse.content().text();

        // 从判断中提取最终方案
        String winner = extractWinner(verdict, planA, planB);
        log.info("[DebateOutline] 辩论完成 — winner_length={}, verdict_preview={}",
                winner.length(),
                verdict.length() > 100 ? verdict.substring(0, 100) : verdict);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chapter_plan", winner);
        data.put("debate_history", List.of(
            Map.of("plan_a", planA, "plan_b", planB, "verdict", verdict)
        ));
        Map<String, String> files = new LinkedHashMap<>();
        files.put("chapter_plan.txt", winner);
        return StageOutput.withFiles(data, files);
    }

    /** 判断章纲是否已有详细内容 */
    private boolean isDetailedOutline(String plan) {
        // 有明确场景/因果/角色指示 = 详细
        return plan.contains("场景") || plan.contains("因果") || plan.contains("冲突")
            || plan.contains("节纲") || plan.contains("章纲")
            || (plan.length() > 200);
    }

    private String buildArchitectPrompt(String label, String currentPlan,
                                        String context, int chapterNumber) {
        return String.format("""
            你正在为一部小说的第%d章设计写作大纲。

            ## 当前上下文
            %s

            ## 现有的简要大纲（可能不完整）
            %s

            请从**方案%s**的角度设计本章大纲，包含：
            1. 本章的核心冲突
            2. 主角的行动路线
            3. 关键转折点
            4. 结尾钩子

            输出纯文本大纲，不要用JSON格式。""",
            chapterNumber,
            context != null && !context.isEmpty() ? (context.length() > 2000 ? context.substring(0, 2000) : context) : "暂无前文",
            currentPlan != null && !currentPlan.isBlank() ? currentPlan : "无",
            label);
    }

    private String buildJudgePrompt(String planA, String planB, String context) {
        return String.format("""
            你是一个资深网文主编，请对比以下两个章节大纲方案，选择更优的一个。
            评判标准：爽点密度、情节合理性、读者期待感、与前文的衔接。

            ## 前文上下文
            %s

            ## 方案A
            %s

            ## 方案B
            %s

            请用以下格式输出：
            选择：A 或 B
            理由：简要说明为什么选这个
            融合建议：如果有的话，可以从另一个方案中提取什么元素加入
            """,
            context != null ? context.substring(0, Math.min(context.length(), 1500)) : "无",
            truncate(planA, 2000),
            truncate(planB, 2000));
    }

    /** 从SUPERVISOR的判断中提取最终方案 */
    private String extractWinner(String verdict, String planA, String planB) {
        String upper = verdict.toUpperCase();
        if (upper.contains("选择：A") || upper.contains("选择 A") || upper.contains("方案A")) {
            return planA;
        }
        if (upper.contains("选择：B") || upper.contains("选择 B") || upper.contains("方案B")) {
            return planB;
        }
        // 无法判断时，取较长者（通常更详细）
        return planA.length() >= planB.length() ? planA : planB;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void ensureSpecs() {
        if (agentSpecs == null) {
            agentSpecs = agentFactory.createAllSpecs(Map.of());
        }
    }
}
