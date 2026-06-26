package com.yunmo.agent.audit;

import com.yunmo.agent.audit.AuditDimension.AuditMode;
import com.yunmo.agent.audit.AuditDimension.AuditTier;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审计报告 — 37维度审计的完整结构化输出
 *
 * <p>一次完整的审计流程产生一份AuditReport，包含：
 * <ul>
 *   <li>各维度评分</li>
 *   <li>发现的问题列表</li>
 *   <li>逐个维度的通过/失败判定</li>
 *   <li>分层级的阻断记账（crashed_guards / quality_warns / compliance_blocks）</li>
 *   <li>最终的入库判定（passed）和整体评分</li>
 * </ul>
 * </p>
 *
 * <h3>通过判定规则</h3>
 * <ol>
 *   <li>任一L1维度失败 → passed=false（硬阻塞）</li>
 *   <li>任一L3维度失败 → passed=false（合规阻断）</li>
 *   <li>L2维度失败 → 不阻断passed，但WARN计入报告（fail-open）</li>
 *   <li>所有维度通过 → passed=true</li>
 * </ol>
 *
 * @param chapterId          章节ID
 * @param overallScore       整体评分（0-100）
 * @param passed             是否通过审计
 * @param issues             发现的问题列表（按严重度降序）
 * @param dimensionScores    各维度评分（Map<维度ID, 0-10分>）
 * @param summary            审计总结（≤200字）
 * @param crashedGuards      硬阻塞维度的失败详情（key=维度名, value=失败原因）
 * @param qualityWarns       质量警告维度的失败详情
 * @param complianceBlocks   合规维度的失败详情
 * @param crashedGuardCount  L1失败的维度计数
 * @param qualityWarnCount   L2失败的维度计数
 * @param complianceBlockCount L3失败的维度计数
 * @param auditMode          审计模式
 * @param auditedAt          审计时间戳
 * @param deAiScore          去AI味评分（0-10）
 * @param rawInspectorJson   Inspector原始返回JSON（供调试）
 */
public record AuditReport(
        String chapterId,
        int overallScore,
        boolean passed,
        List<AuditIssue> issues,
        Map<Integer, Double> dimensionScores,
        String summary,
        Map<String, String> crashedGuards,
        Map<String, String> qualityWarns,
        Map<String, String> complianceBlocks,
        int crashedGuardCount,
        int qualityWarnCount,
        int complianceBlockCount,
        AuditMode auditMode,
        Instant auditedAt,
        double deAiScore,
        String rawInspectorJson
) {

    /**
     * 创建一个空的通过报告（用于异常fallback场景）
     */
    public static AuditReport createPassFallback(String chapterId, AuditMode mode, double deAiScore) {
        return new AuditReport(
                chapterId, 70, true,
                List.of(), Map.of(),
                "审计引擎异常，自动放行（fallback）",
                Map.of(), Map.of(), Map.of(),
                0, 0, 0,
                mode, Instant.now(), deAiScore, ""
        );
    }

    /**
     * 创建一个空的阻断报告（用于严重异常场景）
     */
    public static AuditReport createBlockFallback(String chapterId, AuditMode mode, String reason) {
        return new AuditReport(
                chapterId, 0, false,
                List.of(AuditIssue.critical(AuditDimension.SENSITIVE_WORD_CHECK,
                        "审计引擎致命异常: " + reason,
                        "请检查审计服务状态后重试", "")),
                Map.of(), reason,
                Map.of("审计引擎异常", reason), Map.of(), Map.of(),
                1, 0, 0,
                mode, Instant.now(), 0.0, ""
        );
    }

    /**
     * 获取所有阻断性问题（blocking=true）
     */
    public List<AuditIssue> blockingIssues() {
        return issues.stream()
                .filter(AuditIssue::blocking)
                .toList();
    }

    /**
     * 获取按严重度分组的问题
     */
    public Map<AuditIssue.Severity, List<AuditIssue>> issuesBySeverity() {
        return issues.stream()
                .collect(Collectors.groupingBy(
                        AuditIssue::severity,
                        () -> new TreeMap<>(Comparator.comparingInt(Enum::ordinal)),
                        Collectors.toList()
                ));
    }

    /**
     * 获取指定维度的问题
     */
    public List<AuditIssue> issuesForDimension(AuditDimension dimension) {
        return issues.stream()
                .filter(i -> i.dimension() == dimension)
                .toList();
    }

    /**
     * 生成人类可读的审计摘要文本
     */
    public String toSummaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== 云墨37维审计报告 ===\n"));
        sb.append(String.format("章节: %s | 模式: %s | 评分: %d/100 | 结果: %s\n",
                chapterId, auditMode.displayName(), overallScore, passed ? "通过" : "未通过"));
        sb.append(String.format("去AI味评分: %.1f/10\n", deAiScore));
        sb.append(String.format("L1硬阻塞: %d | L2质量警告: %d | L3合规: %d\n",
                crashedGuardCount, qualityWarnCount, complianceBlockCount));

        if (!crashedGuards.isEmpty()) {
            sb.append("\n--- L1 硬阻塞（CRASHED GUARDS）---\n");
            crashedGuards.forEach((dim, reason) ->
                    sb.append(String.format("  [BLOCK] %s: %s\n", dim, reason)));
        }

        if (!qualityWarns.isEmpty()) {
            sb.append("\n--- L2 质量警告（fail-open，不阻断）---\n");
            qualityWarns.forEach((dim, reason) ->
                    sb.append(String.format("  [WARN] %s: %s\n", dim, reason)));
        }

        if (!complianceBlocks.isEmpty()) {
            sb.append("\n--- L3 合规阻断 ---\n");
            complianceBlocks.forEach((dim, reason) ->
                    sb.append(String.format("  [BLOCK] %s: %s\n", dim, reason)));
        }

        if (!issues.isEmpty()) {
            sb.append(String.format("\n--- 问题列表（共%d条）---\n", issues.size()));
            for (AuditIssue issue : issues) {
                sb.append(String.format("  [%s][%s] %s: %s\n",
                        issue.severity().displayName(),
                        issue.dimension().chineseName(),
                        issue.description(),
                        issue.suggestion() != null && !issue.suggestion().isEmpty()
                                ? "→ " + issue.suggestion() : ""));
            }
        }

        sb.append("\n--- 总结 ---\n").append(summary);
        return sb.toString();
    }

    /**
     * 将报告转为兼容旧版管线的Map格式
     * <p>用于向PipelineState写入审计结果，兼容DecideVerdictRouter等现有组件。</p>
     */
    public Map<String, Object> toLegacyMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chapter_id", chapterId);
        map.put("overall_score", overallScore);
        map.put("passed", passed);
        map.put("verdict", passed ? "pass" : "block");
        map.put("crashed_guard_count", crashedGuardCount);
        map.put("quality_warn_count", qualityWarnCount);
        map.put("compliance_block_count", complianceBlockCount);
        map.put("deai_score", deAiScore);
        map.put("summary", summary);
        map.put("audit_mode", auditMode.code());
        map.put("timestamp", auditedAt.toEpochMilli());

        // 维度的fatal/severe计数（兼容旧版DecideVerdictRouter）
        map.put("fatal_count", crashedGuardCount + complianceBlockCount);
        map.put("severe_count", qualityWarnCount);

        // crashed_guards显式记账
        map.put("crashed_guards", crashedGuards);
        map.put("quality_warns", qualityWarns);
        map.put("compliance_blocks", complianceBlocks);

        // 问题列表
        List<Map<String, Object>> issueMaps = issues.stream()
                .map(i -> {
                    Map<String, Object> im = new LinkedHashMap<>();
                    im.put("dimension", i.dimension().chineseName());
                    im.put("dimension_id", i.dimension().id());
                    im.put("tier", i.dimension().tier().name());
                    im.put("severity", i.severity().name());
                    im.put("description", i.description());
                    im.put("suggestion", i.suggestion());
                    im.put("evidence", i.evidence());
                    im.put("repair_scope", i.repairScope().name());
                    im.put("blocking", i.blocking());
                    return im;
                })
                .toList();
        map.put("issues", issueMaps);

        // 逐维度评分
        List<Map<String, Object>> dimScores = new ArrayList<>();
        for (var entry : dimensionScores.entrySet()) {
            AuditDimension.byId(entry.getKey()).ifPresent(dim -> {
                Map<String, Object> ds = new LinkedHashMap<>();
                ds.put("id", dim.id());
                ds.put("name", dim.chineseName());
                ds.put("tier", dim.tier().name());
                ds.put("score", entry.getValue());
                dimScores.add(ds);
            });
        }
        map.put("dimensions", dimScores);

        // 原始数据（供调试）
        map.put("raw_inspector_json", rawInspectorJson);

        return map;
    }

    /**
     * Builder模式 — 用于逐步构建审计报告
     */
    public static class Builder {
        private String chapterId;
        private AuditMode auditMode = AuditMode.STANDARD;
        private double deAiScore;
        private String rawInspectorJson;
        private final List<AuditIssue> issues = new ArrayList<>();
        private final Map<Integer, Double> dimensionScores = new LinkedHashMap<>();
        private String summary = "";
        private Instant auditedAt = Instant.now();
        private Integer llmOverallScore = null;

        public Builder chapterId(String chapterId) { this.chapterId = chapterId; return this; }
        public Builder auditMode(AuditMode mode) { this.auditMode = mode; return this; }
        public Builder deAiScore(double score) { this.deAiScore = score; return this; }
        public Builder rawInspectorJson(String json) { this.rawInspectorJson = json; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder auditedAt(Instant time) { this.auditedAt = time; return this; }
        public Builder score(int score) { this.llmOverallScore = score; return this; }

        public Builder addIssue(AuditIssue issue) {
            if (issue != null) this.issues.add(issue);
            return this;
        }

        public Builder addIssues(List<AuditIssue> newIssues) {
            if (newIssues != null) newIssues.forEach(this::addIssue);
            return this;
        }

        public Builder putScore(int dimensionId, double score) {
            this.dimensionScores.put(dimensionId, Math.max(0, Math.min(10, score)));
            return this;
        }

        public Builder putScores(Map<Integer, Double> scores) {
            if (scores != null) scores.forEach(this::putScore);
            return this;
        }

        public AuditReport build() {
            // 分类统计
            Map<String, String> crashed = new LinkedHashMap<>();
            Map<String, String> warns = new LinkedHashMap<>();
            Map<String, String> compliances = new LinkedHashMap<>();

            for (AuditIssue issue : issues) {
                String dimName = issue.dimension().chineseName();
                String desc = issue.description();
                switch (issue.dimension().tier()) {
                    case L1_CRASHED_GUARD -> {
                        if (issue.blocking()) crashed.put(dimName, desc);
                    }
                    case L2_QUALITY_WARN -> warns.put(dimName, desc);
                    case L3_COMPLIANCE -> compliances.put(dimName, desc);
                }
            }

            boolean passed = crashed.isEmpty() && compliances.isEmpty();

            // 计算整体评分：各维度加权平均，按维度默认权重
            double weightedSum = 0;
            double totalWeight = 0;
            for (var entry : dimensionScores.entrySet()) {
                var dim = AuditDimension.byId(entry.getKey());
                if (dim.isPresent()) {
                    double w = dim.get().defaultWeight();
                    weightedSum += entry.getValue() * w;
                    totalWeight += w;
                }
            }
            int overallScore;
            if (llmOverallScore != null) {
                overallScore = llmOverallScore;
            } else if (totalWeight > 0) {
                overallScore = (int) Math.round((weightedSum / totalWeight) * 10);  // 0-10 -> 0-100
            } else {
                overallScore = 70;
            }

            // 自动生成摘要（如果未显式设置）
            if (summary == null || summary.isEmpty()) {
                summary = generateAutoSummary(passed, crashed.size(), warns.size(), compliances.size(), overallScore);
            }

            return new AuditReport(
                    chapterId,
                    Math.max(0, Math.min(100, overallScore)),
                    passed,
                    issues.stream()
                            .sorted(Comparator.comparingInt(i -> i.severity().ordinal()))
                            .toList(),
                    Collections.unmodifiableMap(dimensionScores),
                    summary,
                    Collections.unmodifiableMap(crashed),
                    Collections.unmodifiableMap(warns),
                    Collections.unmodifiableMap(compliances),
                    crashed.size(),
                    warns.size(),
                    compliances.size(),
                    auditMode,
                    auditedAt,
                    deAiScore,
                    rawInspectorJson
            );
        }

        private static String generateAutoSummary(boolean passed, int crashed, int warns, int compliances, int score) {
            if (passed && crashed == 0 && warns == 0 && compliances == 0) {
                return String.format("全部维度通过审计，综合评分%d/100，无需修改。", score);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(passed ? "审计通过" : "审计未通过");
            sb.append(String.format("（评分%d/100）", score));
            if (crashed > 0) sb.append(String.format("，L1硬阻塞%d项", crashed));
            if (compliances > 0) sb.append(String.format("，L3合规阻断%d项", compliances));
            if (warns > 0) sb.append(String.format("，L2质量警告%d项（fail-open不阻断）", warns));
            sb.append("。");
            return sb.toString();
        }
    }

    /**
     * 创建Builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
