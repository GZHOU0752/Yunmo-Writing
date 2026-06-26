package com.yunmo.agent.audit;

import com.yunmo.agent.audit.AuditDimension.AuditMode;
import com.yunmo.agent.audit.AuditDimension.AuditTier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 结构化审计 Prompt 构建器 — 生成37维审计的规范 prompt
 *
 * <p>v2升级：从33维4组（情节/角色/文笔/合规）升级为37维3层（L1硬阻塞/L2质量警告/L3合规），
 * 按tier分组呈现维度，并在prompt中明确各层的判定策略。</p>
 */
public class AuditPromptBuilder {

    /**
     * 构建 Inspector 系统提示（37维度版）
     *
     * @param genreConfig 类型专属规则（可选）
     * @param mode        审计模式，控制启用哪些维度
     * @param includeFanfic 是否包含同人/番外维度
     */
    public static String buildInspectorSystemPrompt(Map<String, Object> genreConfig,
                                                     AuditMode mode,
                                                     boolean includeFanfic) {
        List<AuditDimension> enabledDims = AuditDimension.enabledForMode(mode, includeFanfic);

        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是严格的质量检查官（Inspector Agent），负责对章节进行全方位的37维度质量分析。
                审计体系采用三层架构，参考inkos的DIMENSION_LABELS设计，各层有不同的判定策略。

                ## 分层判定策略

                | 层级 | 名称 | FAIL处置 | 说明 |
                |------|------|----------|------|
                | L1 | 硬阻塞（Crashed Guard） | 拒绝入库 | 结构性缺陷不可放行，必须修复 |
                | L2 | 质量警告（Quality Warn） | 降级为WARN | fail-open机制，不阻断但需记录 |
                | L3 | 合规（Compliance） | BLOCK阻断 | 合规自检必须通过，否则入库阻断 |

                ## 评分标准（每个维度0-10分）
                - 8-10分：优秀，无明显问题
                - 6-7分：良好，有轻微可优化空间
                - 4-5分：一般，有需要修改的问题
                - 2-3分：较差，存在明显缺陷
                - 0-1分：严重问题，必须重写

                """);

        sb.append("## 审计维度（").append(enabledDims.size()).append("维）\n\n");

        // 按tier分组输出
        for (AuditTier tier : AuditTier.values()) {
            List<AuditDimension> tierDims = enabledDims.stream()
                    .filter(d -> d.tier() == tier)
                    .toList();
            if (tierDims.isEmpty()) continue;

            sb.append("### ").append(tier.displayName()).append("（")
                    .append(tier.failPolicy()).append("）\n\n");

            int seq = 0;
            for (AuditDimension d : tierDims) {
                seq++;
                sb.append(String.format("%d. **%s**（id=%d, 权重%.1f）：%s\n",
                        seq, d.chineseName(), d.id(), d.defaultWeight(), d.description()));
            }
            sb.append("\n");
        }

        sb.append("""
                ## 输出格式（严格遵守 JSON Schema）

                请以 JSON 对象输出，结构如下：
                ```json
                {
                  "verdict": "pass|block",
                  "score": 0-100,
                  "summary": "总体评价（≤200字）",
                  "dimension_scores": {
                    "1": 8,
                    "2": 7,
                    ...
                  },
                  "issues": [
                    {
                      "dimension_id": 1,
                      "dimension_name": "OOC检查",
                      "tier": "L1_CRASHED_GUARD",
                      "score": 2,
                      "severity": "S2_MAJOR",
                      "comment": "具体问题描述（≤60字）",
                      "suggestion": "修改建议（≤80字）",
                      "evidence": "原文引用（≤100字）",
                      "repair_scope": "local|structural|global",
                      "blocking": true
                    }
                  ],
                  "crashed_guard_count": 0,
                  "quality_warn_count": 0,
                  "compliance_block_count": 0
                }
                ```

                ## verdict 判定规则
                - 任一L1维度评分 ≤3 → crashed_guard_count++，verdict = "block"
                - 任一L3维度评分 ≤3 → compliance_block_count++，verdict = "block"
                - L2维度评分 ≤3 → quality_warn_count++（不影响verdict，fail-open）
                - 否则 verdict = "pass"

                ## 重要提醒
                - 每个维度都必须评分，不可遗漏
                - 评分必须有依据，不可随意给分
                - evidence必须引用原文具体语句
                - L1维度要特别严格，宁严勿宽
                - L2维度如实评价但不要过度影响verdict
                """);

        if (genreConfig != null && !genreConfig.isEmpty()) {
            sb.append("\n## 类型专属规则\n");
            genreConfig.forEach((k, v) ->
                    sb.append(String.format("- %s：%s\n", k, v)));
        }

        return sb.toString();
    }

    /**
     * 构建检查用户提示（37维度版）
     *
     * @param chapterContent 章节正文（会自动截取前4000字）
     * @param guardianResult Guardian禁止术语扫描结果
     * @param deAiResult     去AI味预检测结果
     * @param mode           审计模式
     * @return 组装完成的用户提示
     */
    public static String buildInspectionUserPrompt(String chapterContent,
                                                    String guardianResult,
                                                    Map<String, Object> deAiResult,
                                                    AuditMode mode) {
        return String.format("""
                请对以下章节进行完整的37维质量分析：

                ## 审计模式：%s

                ## Guardian 禁止术语扫描结果
                %s

                ## 去 AI 味预检测结果
                正则得分：%s / 10
                LLM 得分：%s / 10
                综合得分：%s / 10
                匹配到的模板：%s

                ## 章节正文（前4000字）
                %s

                请严格按照输出格式，对每个启用的维度逐一评分，输出完整的JSON审计结果。""",
                mode.displayName(),
                guardianResult != null ? guardianResult : "无",
                deAiResult.getOrDefault("regexScore", "N/A"),
                deAiResult.getOrDefault("llmScore", "N/A"),
                deAiResult.getOrDefault("score", "N/A"),
                formatRegexMatches(deAiResult),
                chapterContent.length() > 4000 ? chapterContent.substring(0, 4000) + "..." : chapterContent
        );
    }

    @SuppressWarnings("unchecked")
    private static String formatRegexMatches(Map<String, Object> deAiResult) {
        Object matches = deAiResult.get("regexMatches");
        if (matches instanceof List<?> list && !list.isEmpty()) {
            return list.stream()
                    .limit(10)
                    .map(m -> {
                        if (m instanceof Map<?, ?> mm) {
                            return mm.get("description") + "(×" + mm.get("count") + ")";
                        }
                        return m.toString();
                    })
                    .collect(Collectors.joining(", "));
        }
        return "无";
    }
}
