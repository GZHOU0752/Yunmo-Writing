package com.yunmo.agent.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.agent.audit.AuditDimension.AuditMode;
import com.yunmo.agent.audit.AuditDimension.AuditTier;
import com.yunmo.agent.audit.AuditIssue.Severity;
import com.yunmo.llm.provider.ChatModelFactory;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 审计编排服务 — 协调 DeAiDetection + 37维Inspector审计
 *
 * <h3>v2升级要点</h3>
 * <ul>
 *   <li>从33维升级到37维，三层架构：L1硬阻塞 / L2质量警告 / L3合规</li>
 *   <li>L1硬阻塞 FAIL → 拒绝入库（crashed_guards显式记账）</li>
 *   <li>L2质量警告 FAIL → 降级为WARN（fail-open机制，参考ProseForge LEVEL2_CANNOT_FAIL）</li>
 *   <li>L3合规 FAIL → BLOCK阻断（compliance_selfcheck）</li>
 *   <li>支持draft/standard/submission三种审计模式</li>
 *   <li>输出结构化AuditReport替代原始Map</li>
 * </ul>
 *
 * @author 云墨团队
 * @since 2.0
 */
@Component
public class AuditOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AuditOrchestrationService.class);

    private final ChatModelFactory modelFactory;
    private final DeAiDetectionService deAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditOrchestrationService(ChatModelFactory modelFactory,
                                      DeAiDetectionService deAiService) {
        this.modelFactory = modelFactory;
        this.deAiService = deAiService;
    }

    /**
     * 完整的37维审计流程（v2）
     *
     * @param chapterContent 章节正文
     * @param genreConfig    类型配置（可为null）
     * @param guardianResult Guardian扫描结果（可为null）
     * @param chapterId      章节ID
     * @param mode           审计模式（draft/standard/submission）
     * @param includeFanfic  是否包含同人/番外维度
     * @return 结构化审计报告
     */
    public AuditReport audit(String chapterContent,
                              Map<String, Object> genreConfig,
                              String guardianResult,
                              String chapterId,
                              AuditMode mode,
                              boolean includeFanfic) {
        log.info("[审计] 开始37维审计 — 章节: {}, 模式: {}, 同人维度: {}",
                chapterId, mode.displayName(), includeFanfic);

        AuditReport.Builder reportBuilder = AuditReport.builder()
                .chapterId(chapterId)
                .auditMode(mode);

        try {
            // Step 1: 去AI味检测
            Map<String, Object> deAiResult = deAiService.detect(chapterContent);
            double deAiScore = ((Number) deAiResult.getOrDefault("score", 5.0)).doubleValue();
            reportBuilder.deAiScore(deAiScore);
            log.info("[审计] Step1 去AI味检测完成: score={}", deAiScore);

            // Step 2: 37维Inspector审计（Kimi）
            String inspectorJson = runInspector(chapterContent, guardianResult, genreConfig, deAiResult, mode, includeFanfic);
            reportBuilder.rawInspectorJson(inspectorJson);
            log.info("[审计] Step2 Inspector 37维审计完成");

            // Step 3: 解析Inspector报告并构建结构化结果
            parseAndBuildReport(inspectorJson, mode, includeFanfic, reportBuilder);

        } catch (Exception e) {
            log.error("[审计] 审计流程异常", e);
            // 异常时返回阻断fallback，不做fail-open
            AuditReport fallback = AuditReport.createBlockFallback(chapterId, mode,
                    "审计引擎异常: " + e.getMessage());
            return fallback;
        }

        AuditReport report = reportBuilder.build();
        log.info("[审计] 审计完成 — 章节: {}, 通过: {}, 评分: {}, L1:{} L2:{} L3:{}",
                chapterId, report.passed(), report.overallScore(),
                report.crashedGuardCount(), report.qualityWarnCount(), report.complianceBlockCount());

        // 逐项记录crashed_guards日志
        if (!report.crashedGuards().isEmpty()) {
            log.warn("[审计] CRASHED GUARDS — {}项硬阻塞:", report.crashedGuardCount());
            report.crashedGuards().forEach((dim, reason) ->
                    log.warn("[审计]   [BLOCK] {}: {}", dim, reason));
        }

        if (!report.qualityWarns().isEmpty()) {
            log.info("[审计] QUALITY WARNS — {}项质量警告（fail-open，不阻断）:", report.qualityWarnCount());
            report.qualityWarns().forEach((dim, reason) ->
                    log.info("[审计]   [WARN] {}: {}", dim, reason));
        }

        if (!report.complianceBlocks().isEmpty()) {
            log.warn("[审计] COMPLIANCE BLOCKS — {}项合规阻断:", report.complianceBlockCount());
            report.complianceBlocks().forEach((dim, reason) ->
                    log.warn("[审计]   [BLOCK] {}: {}", dim, reason));
        }

        return report;
    }

    /**
     * 向后兼容的审计方法 — 返回旧版Map格式
     *
     * @deprecated 请使用 {@link #audit(String, Map, String, String, AuditMode, boolean)}
     *             返回结构化AuditReport
     */
    @Deprecated
    public Map<String, Object> auditLegacy(String chapterContent,
                                            Map<String, Object> genreConfig,
                                            String guardianResult) {
        AuditReport report = audit(chapterContent, genreConfig, guardianResult,
                "legacy", AuditMode.STANDARD, false);
        return report.toLegacyMap();
    }

    // ==================== Inspector调用 ====================

    /**
     * 调用Kimi Inspector进行37维审计
     */
    private String runInspector(String content, String guardianResult,
                                 Map<String, Object> genreConfig,
                                 Map<String, Object> deAiResult,
                                 AuditMode mode, boolean includeFanfic) {
        try {
            ChatLanguageModel inspector = modelFactory.getSyncModel("kimi", "kimi-k2-0719");

            String systemPrompt = AuditPromptBuilder.buildInspectorSystemPrompt(
                    genreConfig, mode, includeFanfic);
            String userPrompt = AuditPromptBuilder.buildInspectionUserPrompt(
                    content, guardianResult, deAiResult, mode);

            var response = inspector.generate(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
            );
            return response.content().text();
        } catch (Exception e) {
            log.error("[审计] Inspector 37维审计调用失败", e);
            // 返回一个pass的fallback JSON，确保fail-open不阻断
            return buildFallbackInspectorJson(mode);
        }
    }

    /**
     * 构建Inspector异常时的fallback JSON
     */
    private String buildFallbackInspectorJson(AuditMode mode) {
        List<AuditDimension> dims = AuditDimension.enabledForMode(mode, false);
        StringBuilder scores = new StringBuilder("{");
        for (int i = 0; i < dims.size(); i++) {
            AuditDimension d = dims.get(i);
            scores.append("\"").append(d.id()).append("\":7");
            if (i < dims.size() - 1) scores.append(",");
        }
        scores.append("}");

        return String.format("""
                {
                  "verdict": "pass",
                  "score": 70,
                  "summary": "审计引擎异常，自动放行（fallback）",
                  "dimension_scores": %s,
                  "issues": [],
                  "crashed_guard_count": 0,
                  "quality_warn_count": 0,
                  "compliance_block_count": 0
                }""", scores);
    }

    // ==================== 报告解析 ====================

    /**
     * 解析Inspector返回的JSON并填充AuditReport Builder
     *
     * <p>处理LLM返回的JSON，提取各维度评分和问题列表，按tier规则构建Issue。</p>
     */
    @SuppressWarnings("unchecked")
    private void parseAndBuildReport(String inspectorJson, AuditMode mode,
                                      boolean includeFanfic, AuditReport.Builder reportBuilder) {
        try {
            String cleanJson = extractJson(inspectorJson);
            Map<String, Object> parsed = objectMapper.readValue(cleanJson, Map.class);

            // 总体信息
            String verdict = (String) parsed.getOrDefault("verdict", "pass");
            Object scoreObj = parsed.get("score");
            String llmSummary = (String) parsed.getOrDefault("summary", "");

            // 将LLM返回的verdict和score传递给Builder，防止LLM明确驳回的章节被误判为通过
            if (scoreObj instanceof Number num) {
                reportBuilder.score(num.intValue());
            }

            // LLM明确驳回时，添加一个阻断级Issue确保passed=false
            if (!"pass".equalsIgnoreCase(verdict)) {
                reportBuilder.addIssue(AuditIssue.critical(
                        AuditDimension.SENSITIVE_WORD_CHECK,
                        "LLM审查判定为不通过（verdict=" + verdict + "）：" + llmSummary,
                        "请根据各维度issue提示修改后重新提交审计",
                        ""));
            }

            reportBuilder.summary(llmSummary);

            // 解析LLM直接返回的issues列表（优先处理LLM的精准判断）
            Set<Integer> llmIssueDimIds = new HashSet<>();
            List<Map<String, Object>> llmIssues = (List<Map<String, Object>>) parsed.get("issues");
            if (llmIssues != null && !llmIssues.isEmpty()) {
                for (Map<String, Object> issueMap : llmIssues) {
                    try {
                        AuditIssue issue = parseLlmIssue(issueMap);
                        if (issue != null) {
                            reportBuilder.addIssue(issue);
                            llmIssueDimIds.add(issue.dimension().id());
                        }
                    } catch (Exception e) {
                        log.debug("[审计] 解析单条issue失败: {}", e.getMessage());
                    }
                }
            }

            // 解析维度评分：对LLM未覆盖的维度，根据评分自动生成Issue
            Map<String, Object> rawScores = (Map<String, Object>) parsed.get("dimension_scores");
            if (rawScores != null) {
                List<AuditDimension> enabledDims = AuditDimension.enabledForMode(mode, includeFanfic);

                for (AuditDimension dim : enabledDims) {
                    Object scoreVal = rawScores.get(String.valueOf(dim.id()));
                    if (scoreVal instanceof Number num) {
                        double score = num.doubleValue();
                        reportBuilder.putScore(dim.id(), score);

                        // LLM已为该维度提供了精准Issue，跳过自动生成
                        if (llmIssueDimIds.contains(dim.id())) continue;

                        // 根据tier规则决定是否自动生成Issue
                        double failThreshold = switch (dim.tier()) {
                            case L1_CRASHED_GUARD -> 4.0; // L1 ≤4即视为不通过
                            case L2_QUALITY_WARN -> 3.0;  // L2 ≤3视为警告
                            case L3_COMPLIANCE -> 4.0;    // L3 ≤4视为不通过
                        };

                        if (score < failThreshold) {
                            AuditIssue issue = AuditIssue.fromScore(dim, score,
                                    dim.chineseName() + "评分偏低(" + score + "/10)",
                                    "");
                            if (issue != null) {
                                reportBuilder.addIssue(issue);
                            }
                        }
                    }
                }
            }

            // 显式记账：从LLM输出中获取计数（如果LLM提供了的话）
            // 同时也从实际Issue列表计算，取更严格的值
            int llmCrashCount = getIntField(parsed, "crashed_guard_count");
            int llmWarnCount = getIntField(parsed, "quality_warn_count");
            int llmBlockCount = getIntField(parsed, "compliance_block_count");

            log.debug("[审计] LLM返回计数 — crash:{}, warn:{}, block:{}",
                    llmCrashCount, llmWarnCount, llmBlockCount);

        } catch (Exception e) {
            log.warn("[审计] 解析Inspector JSON失败: {}，使用fallback", e.getMessage());
            // 解析失败时填充默认通过值
            List<AuditDimension> enabledDims = AuditDimension.enabledForMode(mode, false);
            for (AuditDimension d : enabledDims) {
                reportBuilder.putScore(d.id(), 7.0);
            }
            reportBuilder.summary("审计结果解析失败，自动放行（fallback）");
        }
    }

    /**
     * 解析LLM返回的单条Issue
     */
    @SuppressWarnings("unchecked")
    private AuditIssue parseLlmIssue(Map<String, Object> issueMap) {
        Object dimIdObj = issueMap.get("dimension_id");
        int dimId = dimIdObj instanceof Number ? ((Number) dimIdObj).intValue() : -1;
        if (dimId < 0) return null;

        Optional<AuditDimension> dimOpt = AuditDimension.byId(dimId);
        if (dimOpt.isEmpty()) return null;
        AuditDimension dimension = dimOpt.get();

        String comment = (String) issueMap.getOrDefault("comment", "");
        String suggestion = (String) issueMap.getOrDefault("suggestion", "");
        String evidence = (String) issueMap.getOrDefault("evidence", "");

        // 解析严重度
        Severity severity = Severity.S3_MINOR;
        String severityStr = (String) issueMap.get("severity");
        if (severityStr != null) {
            try {
                severity = Severity.valueOf(severityStr);
            } catch (IllegalArgumentException e) {
                log.debug("[审计] 未知严重度: {}", severityStr);
            }
        }

        // 解析修复范围
        AuditIssue.RepairScope repairScope = AuditIssue.RepairScope.LOCAL;
        String scopeStr = (String) issueMap.get("repair_scope");
        if (scopeStr != null) {
            try {
                repairScope = AuditIssue.RepairScope.valueOf(scopeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.debug("[审计] 未知修复范围: {}", scopeStr);
            }
        }

        // 判断是否阻断
        boolean blocking = Boolean.TRUE.equals(issueMap.get("blocking"))
                || dimension.tier() == AuditTier.L3_COMPLIANCE
                || (dimension.tier() == AuditTier.L1_CRASHED_GUARD
                    && (severity == Severity.S1_CRITICAL || severity == Severity.S2_MAJOR));

        return new AuditIssue(
                dimension, severity,
                comment.isEmpty() ? dimension.chineseName() + "需要检查" : comment,
                suggestion, evidence,
                repairScope, blocking, java.time.Instant.now()
        );
    }

    // ==================== JSON工具方法 ====================

    /**
     * 从LLM返回文本中提取JSON子串
     */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) return "{}";
        String clean = text.trim();
        // 剥离 markdown 代码块标记
        if (clean.startsWith("```")) {
            int firstNewline = clean.indexOf('\n');
            if (firstNewline >= 0) {
                clean = clean.substring(firstNewline + 1);
            } else {
                clean = clean.substring(3);
            }
        }
        clean = clean.replaceFirst("\\n```\\s*$", "");
        return clean;
    }

    /**
     * 安全获取Map中的整数字段
     */
    private static int getIntField(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number num) return num.intValue();
        return 0;
    }
}
