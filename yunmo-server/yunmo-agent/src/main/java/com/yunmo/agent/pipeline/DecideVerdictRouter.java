package com.yunmo.agent.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 判定路由 — 章节质量审查结果路由
 *
 * 决策逻辑:
 * - Guardian fatal 违规 → regenerate (丢弃当前结果重新生成)
 * - Inspector severe_count > 2 → rewrite (整章重写)
 * - 报告解析失败 → 若未达重试上限则 regenerate，否则强制通过
 * - 其余 → pass (通过)
 */
@Component
public class DecideVerdictRouter implements ConditionalRouter {

    private static final Logger log = LoggerFactory.getLogger(DecideVerdictRouter.class);
    private static final Set<String> VALID_VERDICTS = Set.of("pass", "regenerate", "rewrite");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppProperties appProperties;

    public DecideVerdictRouter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public String route(PipelineState state) {
        int retryCount = state.getOrDefault("retry_count", Integer.class, 0);
        int maxRetries = appProperties.getMaxRetries();

        if (retryCount >= maxRetries) {
            log.warn("[Verdict] 已达最大重试次数 {}，强制通过", maxRetries);
            state.put("verdict", "pass_forced");
            return END;
        }

        // 优先使用新管线的对抗编辑评分
        Integer adversarialScore = state.get("adversarial_score", Integer.class);
        if (adversarialScore != null) {
            if (adversarialScore < 4) {
                log.warn("[Verdict] 读者评分 {} < 4 → regenerate", adversarialScore);
                state.put("retry_count", retryCount + 1);
                state.put("verdict", "regenerate");
                return "regenerate";
            }
            if (adversarialScore < 8) {
                log.info("[Verdict] 读者评分 {} < 8 → rewrite（润色重写）", adversarialScore);
                state.put("retry_count", retryCount + 1);
                state.put("verdict", "rewrite");
                return "rewrite";
            }
            log.info("[Verdict] 读者评分 {} ≥ 8 → 通过 ✓", adversarialScore);
            state.put("verdict", "pass");
            return END;
        }

        // 兼容旧管线：Guardian + Inspector 报告
        String guardianJson = state.get("guardian_report", String.class);
        String inspectorJson = state.get("inspector_report", String.class);

        try {
            // 解析 Guardian 报告
            if (guardianJson != null && !guardianJson.isEmpty()) {
                Map<String, Object> guardianReport = parseReport(guardianJson, "Guardian");
                if (guardianReport != null) {
                    Boolean passed = (Boolean) guardianReport.get("passed");
                    if (passed != null && !passed) {
                        @SuppressWarnings("unchecked")
                        var violations = (List<Map<String, Object>>) guardianReport.get("violations");
                        if (violations != null && !violations.isEmpty()) {
                            boolean hasFatal = violations.stream()
                                    .anyMatch(v -> "fatal".equals(v.get("severity")));
                            if (hasFatal) {
                                log.warn("[Verdict] Guardian 检测到 fatal 违规 → regenerate");
                                state.put("retry_count", retryCount + 1);
                                state.put("verdict", "regenerate");
                                return "regenerate";
                            }
                        }
                    }
                }
            }

            // 解析 Inspector 报告
            if (inspectorJson != null && !inspectorJson.isEmpty()) {
                Map<String, Object> inspectorReport = parseReport(inspectorJson, "Inspector");
                if (inspectorReport != null) {
                    String verdict = (String) inspectorReport.get("verdict");
                    // 白名单校验：仅信任预定义的 verdict 值
                    if (verdict != null && VALID_VERDICTS.contains(verdict)) {
                        Object severeObj = inspectorReport.get("severe_count");
                        int severeCount = severeObj instanceof Number ? ((Number) severeObj).intValue() : 0;

                        if ("regenerate".equals(verdict) || "rewrite".equals(verdict)) {
                            log.warn("[Verdict] Inspector 判定: {} (severe_count={})", verdict, severeCount);
                            state.put("retry_count", retryCount + 1);
                            state.put("verdict", verdict);
                            return severeCount > 2 ? "rewrite" : "regenerate";
                        }
                    } else if (verdict != null) {
                        log.warn("[Verdict] Inspector 返回未知 verdict 值: {}，已忽略", verdict);
                    }
                }
            }

            log.info("[Verdict] 通过 ✓");
            state.put("verdict", "pass");
            return END;
        } catch (Exception e) {
            // 解析异常不应默认通过——应触发重试以修复生成
            log.error("[Verdict] 解析报告异常，将重试 (当前重试次数: {}/{})", retryCount + 1, maxRetries, e);
            state.put("retry_count", retryCount + 1);
            state.put("verdict", "parse_error");
            return "regenerate";
        }
    }

    /** 解析 LLM 返回的报告 JSON，解析失败时返回 null 而不抛异常 */
    private Map<String, Object> parseReport(String raw, String reportType) {
        try {
            String json = extractJson(raw);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(json, Map.class);
            return result;
        } catch (Exception e) {
            log.error("[Verdict] {} 报告 JSON 解析失败，原始内容前200字符: {}",
                    reportType, raw.length() > 200 ? raw.substring(0, 200) + "..." : raw, e);
            return null;
        }
    }

    /** 从 LLM 返回文本中提取 JSON 子串 — 委托给共享工具方法 */
    private String extractJson(String text) {
        return com.yunmo.common.util.JsonExtractor.extractJson(text);
    }
}
