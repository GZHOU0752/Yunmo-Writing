package com.yunmo.agent.pipeline;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import com.yunmo.common.enums.AgentType;
import com.yunmo.agent.audit.AuditOrchestrationService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 审校阶段 — Guardian → 33维审计（DeAI + Inspector）
 * 替代 Python review_chapter_node，集成 InkOS 33维审计体系
 */
@Component
public class ReviewChapterStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(ReviewChapterStage.class);
    private final AgentFactory agentFactory;
    private final AuditOrchestrationService auditOrchestration;
    private Map<AgentType, AgentSpec> agentSpecs;

    public ReviewChapterStage(AgentFactory agentFactory,
                               AuditOrchestrationService auditOrchestration) {
        this.agentFactory = agentFactory;
        this.auditOrchestration = auditOrchestration;
    }

    @Override
    public StageOutput execute(PipelineState state) {
        log.info("[ReviewChapter] 开始审校（33维审计 + 去AI味检测）...");
        ensureSpecs();

        String chapterContent = state.get("chapter_content", String.class);
        if (chapterContent == null || chapterContent.isEmpty()) {
            log.warn("[ReviewChapter] 章节内容为空，跳过审校");
            return StageOutput.withFiles(
                    Map.of("guardian_report", "{\"passed\":true,\"violations\":[]}",
                           "inspector_report", "{\"verdict\":\"pass\",\"score\":0,\"dimensions\":[],\"overall_comment\":\"empty_content\"}"),
                    Collections.emptyMap());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> genreConfig = state.get("genre_config", Map.class);

        // Step 1: Guardian 禁止术语扫描（保持原有逻辑）
        log.info("[ReviewChapter] Guardian 扫描禁止术语...");
        String guardianResult = runGuardian(chapterContent, genreConfig);

        // Step 2: 33维审计 + 去AI味检测（新的审计编排服务）
        log.info("[ReviewChapter] 33维审计 + 去AI味检测...");
        Map<String, Object> auditResult = auditOrchestration.audit(
                chapterContent, genreConfig, guardianResult);

        // 提取 Inspector 报告（JSON 字符串）
        String inspectorReport = (String) auditResult.get("inspector");

        // 提取去AI味检测结果
        @SuppressWarnings("unchecked")
        Map<String, Object> deAiResult = (Map<String, Object>) auditResult.get("deAi");

        // 将去AI味得分合并到 inspector_report JSON 中的 dimensions 里
        inspectorReport = mergeDeAiIntoInspector(inspectorReport, deAiResult);

        log.info("[ReviewChapter] 审校完成 — 33维审计 + 去AI味检测");

        return StageOutput.withFiles(
                Map.of("guardian_report", guardianResult,
                        "inspector_report", inspectorReport,
                        "deai_result", toJson(deAiResult)),
                Map.of("guardian_report.json", guardianResult,
                        "inspector_report.json", inspectorReport)
        );
    }

    @Override
    public String name() {
        return "review_chapter";
    }

    /**
     * 将去AI味检测结果合并到 Inspector 报告的 dimensions 数组中
     */
    @SuppressWarnings("unchecked")
    private String mergeDeAiIntoInspector(String inspectorJson, Map<String, Object> deAiResult) {
        if (deAiResult == null) return inspectorJson;
        try {
            Map<String, Object> report = auditOrchestration.parseInspectorReport(inspectorJson);
            List<Map<String, Object>> dimensions = (List<Map<String, Object>>) report.get("dimensions");
            if (dimensions == null) dimensions = new ArrayList<>();

            // 检查是否已有 AI味检测 维度，如果有则更新分数
            boolean found = false;
            for (Map<String, Object> dim : dimensions) {
                if ("AI味检测".equals(dim.get("name"))) {
                    dim.put("score", deAiResult.get("score"));
                    if (dim.containsKey("comment")) {
                        dim.put("comment", deAiResult.get("analysis"));
                    }
                    found = true;
                    break;
                }
            }
            // 如果没有则添加
            if (!found) {
                Map<String, Object> aiDim = new LinkedHashMap<>();
                aiDim.put("name", "AI味检测");
                aiDim.put("category", "文笔");
                aiDim.put("score", deAiResult.get("score"));
                aiDim.put("comment", deAiResult.get("analysis"));
                aiDim.put("severe", ((Number) deAiResult.getOrDefault("score", 10)).doubleValue() < 4);
                aiDim.put("fatal", ((Number) deAiResult.getOrDefault("score", 10)).doubleValue() < 2);
                dimensions.add(aiDim);
            }

            report.put("dimensions", dimensions);
            // 重新计算总分
            double totalScore = dimensions.stream()
                    .mapToDouble(d -> ((Number) d.getOrDefault("score", 5)).doubleValue())
                    .average().orElse(5.0) * 10;
            report.put("score", (int) Math.round(totalScore));

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(report);
        } catch (Exception e) {
            log.warn("合并去AI味检测结果失败: {}", e.getMessage());
            return inspectorJson;
        }
    }

    @SuppressWarnings("unchecked")
    private String runGuardian(String content, Map<String, Object> genreConfig) {
        var guardian = agentFactory.createChatModel(agentSpecs.get(AgentType.GUARDIAN));
        List<String> forbiddenTerms = List.of();
        if (genreConfig != null && genreConfig.containsKey("forbidden_terms")) {
            forbiddenTerms = (List<String>) genreConfig.get("forbidden_terms");
        }
        var response = guardian.generate(
                SystemMessage.from(agentSpecs.get(AgentType.GUARDIAN).systemPrompt()),
                UserMessage.from(String.format("""
                    请扫描以下章节正文中的禁止术语：

                    ## 禁止术语列表
                    %s

                    ## 章节正文
                    %s

                    输出 JSON：{"passed": true/false, "violations": [...], "evasion_detected": false}
                    """, String.join(", ", forbiddenTerms), content))
        );
        return response.content().text();
    }

    private void ensureSpecs() {
        if (agentSpecs == null) {
            agentSpecs = agentFactory.createAllSpecs(Map.of());
        }
    }

    private String toJson(Map<String, Object> data) {
        if (data == null) return "{}";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
