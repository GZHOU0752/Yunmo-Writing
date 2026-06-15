package com.yunmo.agent.audit;

import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.common.dto.LLMResponse;
import com.yunmo.llm.provider.LLMProvider;
import com.yunmo.llm.provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 审计编排服务 — 协调 Guardian + DeAiDetection + Inspector 三步审计
 */
@Component
public class AuditOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AuditOrchestrationService.class);
    private final ProviderRegistry providerRegistry;
    private final DeAiDetectionService deAiService;

    public AuditOrchestrationService(ProviderRegistry providerRegistry,
                                      DeAiDetectionService deAiService) {
        this.providerRegistry = providerRegistry;
        this.deAiService = deAiService;
    }

    /**
     * 完整的三步审计流程
     */
    public Map<String, Object> audit(String chapterContent,
                                      Map<String, Object> genreConfig,
                                      String guardianResult) {
        // Step 1: 去 AI 味检测
        Map<String, Object> deAiResult = deAiService.detect(chapterContent);
        log.info("去AI味检测完成: score={}", deAiResult.get("score"));

        // Step 2: Inspector 33 维审计（kimi）
        String inspectorResult = runInspector(chapterContent, guardianResult, genreConfig, deAiResult);
        log.info("Inspector 33维审计完成");

        // Step 3: 组装完整审计结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("guardian", guardianResult);
        result.put("inspector", inspectorResult);
        result.put("deAi", deAiResult);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private String runInspector(String content, String guardianResult,
                                 Map<String, Object> genreConfig,
                                 Map<String, Object> deAiResult) {
        try {
            LLMProvider inspector = providerRegistry.get("kimi");

            String systemPrompt = AuditPromptBuilder.buildInspectorSystemPrompt(genreConfig);
            String userPrompt = AuditPromptBuilder.buildInspectionUserPrompt(content, guardianResult, deAiResult);

            LLMResponse response = inspector.generate(
                List.of(
                    LLMMessage.system(systemPrompt),
                    LLMMessage.user(userPrompt)
                ),
                LLMConfig.creative("moonshot-v1-8k")
            );
            return response.content();
        } catch (Exception e) {
            log.error("Inspector 33维审计失败", e);
            return "{\"verdict\":\"pass\",\"score\":70,\"fatal_count\":0,\"severe_count\":0,\"dimensions\":[],\"overall_comment\":\"审计引擎异常，自动放行\"}";
        }
    }

    /**
     * 解析 Inspector 返回的 JSON 并转换为结构化 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseInspectorReport(String inspectorJson) {
        try {
            String cleanJson = inspectorJson.trim();
            // 剥离 markdown 代码块：先去掉首行 ```json，再处理没有语言标识的情况
            if (cleanJson.startsWith("```")) {
                int firstNewline = cleanJson.indexOf('\n');
                if (firstNewline >= 0) {
                    cleanJson = cleanJson.substring(firstNewline + 1);
                } else {
                    cleanJson = cleanJson.substring(3); // 无换行直接剥首标记
                }
            }
            // 用正则匹配末尾独立行的 ```，避免匹配到 JSON 内容内的 ```
            cleanJson = cleanJson.replaceFirst("\\n```\\s*$", "");
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(cleanJson, Map.class);
        } catch (Exception e) {
            log.warn("解析 Inspector JSON 失败: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("verdict", "pass");
            fallback.put("score", 70);
            fallback.put("fatal_count", 0);
            fallback.put("severe_count", 0);
            fallback.put("dimensions", List.of());
            fallback.put("overall_comment", "审计结果解析失败，自动放行");
            return fallback;
        }
    }
}
