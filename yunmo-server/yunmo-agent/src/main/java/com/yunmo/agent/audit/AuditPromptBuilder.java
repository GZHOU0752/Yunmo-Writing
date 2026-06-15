package com.yunmo.agent.audit;

import com.yunmo.common.enums.AuditDimension;
import com.yunmo.common.enums.AuditDimension.AuditCategory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 结构化审计 Prompt 构建器 — 生成33维审计的规范 prompt
 */
public class AuditPromptBuilder {

    public static String buildInspectorSystemPrompt(Map<String, Object> genreConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是严格的质量检查官（Inspector Agent），负责从 33 个维度对章节进行全方位质量分析。
                参考 InkOS 审计体系设计，33个维度分为 4 组：情节(8维)、角色(7维)、文笔(12维)、合规(6维)。

                评分标准：
                - 8-10分：优秀，无明显问题
                - 6-7分：良好，有轻微可优化空间
                - 4-5分：一般，有需要修改的问题
                - 2-3分：较差，存在明显缺陷
                - 0-1分：严重问题，必须重写

                """);

        sb.append("## 审计维度\n\n");

        for (AuditCategory category : AuditCategory.values()) {
            sb.append("### ").append(category.getDisplayName()).append("组\n");
            List<AuditDimension> dims = Arrays.stream(AuditDimension.values())
                    .filter(d -> d.getCategory() == category)
                    .toList();
            for (int i = 0; i < dims.size(); i++) {
                AuditDimension d = dims.get(i);
                sb.append(String.format("%d. **%s**（权重%.1f）：%s\n",
                        i + 1, d.getDisplayName(), d.getWeight(), d.getDescription()));
            }
            sb.append("\n");
        }

        sb.append("""
                ## 输出格式（严格遵守 JSON Schema）

                请以 JSON 对象输出，结构如下：
                ```json
                {
                  "verdict": "pass|rewrite|regenerate",
                  "score": 0-100,
                  "fatal_count": 0,
                  "severe_count": 0,
                  "dimensions": [
                    {
                      "name": "维度名",
                      "category": "情节|角色|文笔|合规",
                      "score": 0-10,
                      "comment": "简短评语（≤30字）",
                      "severe": false,
                      "fatal": false
                    }
                  ],
                  "overall_comment": "总体评价（≤100字）"
                }
                ```

                verdict 判定规则：
                - 任一维度评分 ≤1 → fatal_count++，verdict = "regenerate"
                - 任一维度评分 ≤3 → severe_count++，severe_count > 2 → verdict = "rewrite"
                - 去AI味维度 ≤3 → verdict = "rewrite"
                - 否则 verdict = "pass"
                """);

        if (genreConfig != null && !genreConfig.isEmpty()) {
            sb.append("\n## 类型专属规则\n");
            genreConfig.forEach((k, v) ->
                    sb.append(String.format("- %s：%s\n", k, v)));
        }

        return sb.toString();
    }

    public static String buildInspectionUserPrompt(String chapterContent, String guardianResult,
                                                     Map<String, Object> deAiResult) {
        return String.format("""
                请对以下章节进行完整的 33 维质量分析：

                ## Guardian 禁止术语扫描结果
                %s

                ## 去 AI 味预检测结果
                正则得分：%s / 10
                LLM 得分：%s / 10
                综合得分：%s / 10
                匹配到的模板：%s

                ## 章节正文（前4000字）
                %s

                请输出完整的 33 维 JSON 审计结果。""",
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
                        if (m instanceof Map<?,?> mm) {
                            return mm.get("description") + "(×" + mm.get("count") + ")";
                        }
                        return m.toString();
                    })
                    .collect(Collectors.joining(", "));
        }
        return "无";
    }
}
