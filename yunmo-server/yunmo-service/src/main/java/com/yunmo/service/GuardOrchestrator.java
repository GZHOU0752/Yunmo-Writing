package com.yunmo.service;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import com.yunmo.common.enums.AgentType;
import com.yunmo.common.util.AntiAIPatterns;
import com.yunmo.common.util.AntiAIPatterns.DiagnosisResult;
import com.yunmo.common.util.AntiAIPatterns.Finding;
import com.yunmo.llm.adapter.MultiProviderChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 三层门禁调度器 — 对标 novel-pipeline-write-engine guard_orchestrator.py
 *
 * L1 结构安全（可 FAIL）：连续性证据、正典证据、幻觉检测、场景增量
 * L2 质量建议（仅 WARNING）：Anti-AI 7-Gate、水文、角色口吻、展示不说教、对话自然度
 * L3 合规风险（可 BLOCK）：合规自查
 *
 * 核心设计：L2 永远不能 FAIL——防止过度自动化摧毁创作自由
 */
@Component
public class GuardOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GuardOrchestrator.class);

    private final AgentFactory agentFactory;
    private final GenrePackService genrePackService;

    public GuardOrchestrator(AgentFactory agentFactory, GenrePackService genrePackService) {
        this.agentFactory = agentFactory;
        this.genrePackService = genrePackService;
    }

    /** 门禁结果 */
    public static class GuardResult {
        public String guardName;
        public int level;          // 1/2/3
        public String status;      // PASS / WARNING / FAIL / BLOCKED
        public String summary;
        public List<String> issues = new ArrayList<>();
        public List<String> suggestions = new ArrayList<>();
        public Map<String, Object> rawData = new LinkedHashMap<>();
    }

    /** 调度报告 */
    public static class OrchestrationReport {
        public String finalStatus;        // PASS / WARNING / BLOCKED
        public List<GuardResult> results = new ArrayList<>();
        public int warningCount;
        public int failCount;
        public double qualityScore;       // 0-10
        public DiagnosisResult antiAIDiagnosis;  // 7-Gate诊断结果
        public String genreContext;       // 注入的体裁规则
    }

    /**
     * 运行标准模式门禁调度
     */
    public OrchestrationReport runStandard(String chapterContent, int chapterNumber,
                                            String genreId, String contextText,
                                            String chapterPlan,
                                            Map<String, String> characterProfiles) {
        OrchestrationReport report = new OrchestrationReport();
        Map<AgentType, AgentSpec> specs = agentFactory.createAllSpecs(Map.of());

        // ===== L1：结构安全层 =====
        log.info("[Guard] L1 结构安全检查 — chapter={}", chapterNumber);

        // L1-1: 连续性检查（LLM Guardian）
        GuardResult continuity = checkContinuity(specs, chapterContent, chapterNumber,
            contextText, chapterPlan, characterProfiles);
        report.results.add(continuity);

        // L1-2: 体裁规则验证（基于 YAML 规则包）
        GuardResult genreCheck = checkGenreRules(chapterContent, genreId);
        report.results.add(genreCheck);

        // ===== L2：质量建议层 =====
        log.info("[Guard] L2 质量建议检查 — chapter={}", chapterNumber);

        // L2-1: Anti-AI 7-Gate 检测（纯规则引擎，零 LLM 成本）
        DiagnosisResult aiDiagnosis = AntiAIPatterns.analyze(chapterContent);
        report.antiAIDiagnosis = aiDiagnosis;
        GuardResult antiAI = antiAIResultsToGuard(aiDiagnosis);
        report.results.add(antiAI);

        // 如果AI味严重，生成3-Pass修复Prompt
        if (aiDiagnosis.overallSeverity == AntiAIPatterns.Severity.FAIL) {
            log.warn("[Guard] 7-Gate检测不通过 — chapter={}, AI评分={}, 严重度=FAIL",
                    chapterNumber, String.format("%.0f", aiDiagnosis.aiScore));
            String fixPrompt = AntiAIPatterns.generateFixPrompt(aiDiagnosis, chapterContent);
            antiAI.rawData.put("fix_prompt", fixPrompt);
        }

        // ===== L3：合规风险层（目前仅记录，实际调用可后续接入） =====
        log.info("[Guard] L3 合规检查跳过（未配置外部合规服务）— chapter={}", chapterNumber);

        // ===== 汇总 =====
        report.finalStatus = computeFinalStatus(report.results);
        report.warningCount = (int) report.results.stream()
            .filter(r -> "WARNING".equals(r.status)).count();
        report.failCount = (int) report.results.stream()
            .filter(r -> "FAIL".equals(r.status) || "BLOCKED".equals(r.status)).count();

        // 质量评分（L1 占 60%，L2 占 40%）
        double l1Score = report.results.stream()
            .filter(r -> r.level == 1)
            .mapToDouble(r -> "PASS".equals(r.status) ? 5.0 : "WARNING".equals(r.status) ? 2.5 : 0)
            .average().orElse(5.0);
        double l2Score = Math.max(0, 10 - aiDiagnosis.aiScore / 10.0);
        report.qualityScore = Math.round((l1Score * 0.6 + l2Score * 0.4) * 10) / 10.0;

        // 体裁规则上下文
        report.genreContext = genrePackService.buildGenreContext(genreId);

        log.info("[Guard] 门禁完成 — final={}, warnings={}, fails={}, score={}/10, antiAI={}/100",
            report.finalStatus, report.warningCount, report.failCount, report.qualityScore,
            String.format("%.0f", aiDiagnosis.aiScore));
        return report;
    }

    // ===== L1-1: 连续性检查 =====

    private GuardResult checkContinuity(Map<AgentType, AgentSpec> specs, String content,
                                         int chapterNumber, String contextText,
                                         String chapterPlan,
                                         Map<String, String> characterProfiles) {
        GuardResult result = new GuardResult();
        result.guardName = "continuity";
        result.level = 1;

        try {
            AgentSpec readerSpec = specs.get(AgentType.READER);
            MultiProviderChatModel reader = agentFactory.createChatModel(readerSpec);

            String prompt = buildContinuityPrompt(content, chapterNumber, contextText, chapterPlan);
            var response = reader.generate(
                SystemMessage.from(readerSpec.systemPrompt()),
                UserMessage.from(prompt)
            );

            String raw = response.content().text();
            raw = com.yunmo.common.util.JsonExtractor.extractJson(raw);
            if (raw == null || raw.isBlank()) raw = "{}";

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw, Map.class);

            Boolean passed = (Boolean) parsed.getOrDefault("passed", true);
            result.status = Boolean.TRUE.equals(passed) ? "PASS" : "WARNING";
            result.summary = (String) parsed.getOrDefault("summary", "连续性检查完成");

            @SuppressWarnings("unchecked")
            List<String> issues = (List<String>) parsed.getOrDefault("issues", List.of());
            result.issues = issues != null ? issues : List.of();
            result.rawData = parsed;
        } catch (Exception e) {
            log.warn("[Guard] 连续性检查异常: {}", e.getMessage());
            result.status = "WARNING";
            result.summary = "连续性检查执行异常，已跳过";
        }
        return result;
    }

    private String buildContinuityPrompt(String content, int chapterNumber,
                                          String contextText, String chapterPlan) {
        return String.format("""
            检查以下章节的连续性。只需标记与前文设定明显矛盾的问题。

            ## 上下文
            %s

            ## 章纲
            %s

            ## 第%d章正文（前800字用于检查）
            %s

            输出 JSON：
            {"passed": true/false, "summary": "一句话总结", "issues": ["问题1", "问题2"]}
            只标记硬性矛盾（人名/地名/设定/时间线前后不一致），不要标记文风或节奏问题。
            """, contextText != null ? contextText.substring(0, Math.min(500, contextText.length())) : "无",
            chapterPlan != null ? chapterPlan.substring(0, Math.min(200, chapterPlan.length())) : "无",
            chapterNumber,
            content != null ? content.substring(0, Math.min(800, content.length())) : "");
    }

    // ===== L1-2: 体裁规则验证 =====

    private GuardResult checkGenreRules(String content, String genreId) {
        GuardResult result = new GuardResult();
        result.guardName = "genre_rules";
        result.level = 1;
        result.status = "PASS";

        List<Map<String, String>> forbidden = genrePackService.getForbiddenPatterns(genreId);
        if (forbidden.isEmpty()) {
            result.summary = "无体裁规则包，跳过";
            return result;
        }

        // 仅做关键词级快速检测：检查正文是否命中禁用模式的典型信号
        int violations = 0;
        for (Map<String, String> fp : forbidden) {
            String pattern = fp.get("pattern");
            if (pattern.contains("境界") && content.contains("境界")) {
                if (content.contains("突破") && !content.contains("代价") && !content.contains("风险")) {
                    result.issues.add("境界突破缺少代价描述——" + fp.get("reason"));
                    violations++;
                }
            }
        }

        if (violations > 0) {
            result.status = "WARNING";
            result.summary = violations + " 条体裁规则被触发";
        } else {
            result.summary = "体裁规则快速检查通过";
        }
        return result;
    }

    // ===== L2-1: Anti-AI 7-Gate 检测 → GuardResult =====

    private GuardResult antiAIResultsToGuard(DiagnosisResult diagnosis) {
        GuardResult result = new GuardResult();
        result.guardName = "anti_ai";
        result.level = 2;
        result.status = diagnosis.passed ? "PASS" : "WARNING";  // L2 永远不能 FAIL

        if (diagnosis.findings.isEmpty()) {
            result.summary = "Anti-AI 7-Gate检测通过，未发现明显 AI 痕迹";
        } else {
            result.summary = String.format("Anti-AI 7-Gate评分 %.0f/100，严重度=%s，发现 %d 处可疑",
                diagnosis.aiScore, diagnosis.overallSeverity.label, diagnosis.totalFindings);
            for (Finding f : diagnosis.findings) {
                result.issues.add(String.format("[%s] %s", f.gate, f.description));
                if (f.suggestion != null) result.suggestions.add(f.suggestion);
            }
        }
        // 6项客观指标
        result.rawData.put("ai_score", diagnosis.aiScore);
        result.rawData.put("total_findings", diagnosis.totalFindings);
        result.rawData.put("overall_severity", diagnosis.overallSeverity.name());
        result.rawData.put("metric_level1_density", diagnosis.metricLevel1Density);
        result.rawData.put("metric_fatal_hits", diagnosis.metricFatalSentenceHits);
        result.rawData.put("metric_psych_density", diagnosis.metricPsychDensity);
        result.rawData.put("metric_paragraph_cv", diagnosis.metricParagraphCV);
        result.rawData.put("metric_dialog_tag_ratio", diagnosis.metricDialogTagRatio);
        result.rawData.put("metric_explanation_density", diagnosis.metricExplanationDensity);
        return result;
    }

    // ===== 最终状态计算 =====

    private String computeFinalStatus(List<GuardResult> results) {
        boolean hasBlocked = results.stream().anyMatch(r -> "BLOCKED".equals(r.status));
        if (hasBlocked) return "BLOCKED";

        boolean hasFail = results.stream().anyMatch(r -> "FAIL".equals(r.status));
        if (hasFail) return "FAIL";

        boolean hasWarning = results.stream().anyMatch(r -> "WARNING".equals(r.status));
        if (hasWarning) return "WARNING";

        return "PASS";
    }
}
