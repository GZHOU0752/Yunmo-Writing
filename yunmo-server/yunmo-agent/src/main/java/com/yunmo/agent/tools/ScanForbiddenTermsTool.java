package com.yunmo.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 禁止术语机械扫描工具 — 替代 Python guard_tools.scan_forbidden_terms()
 * 100% 召回率，零 LLM 调用，纯 Java 字符串匹配
 */
@Component
public class ScanForbiddenTermsTool {

    private static final Logger log = LoggerFactory.getLogger(ScanForbiddenTermsTool.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 机械扫描章节正文中的禁止术语
     *
     * @param text          待扫描的章节正文
     * @param forbiddenTermsStr 禁止术语 JSON 数组: ["魔力","魔核","法环",...]
     * @return JSON 格式扫描结果: {"violations": [...], "total": N}
     */
    public String scan(String text, String forbiddenTermsStr) {
        List<String> forbiddenTerms = parseTerms(forbiddenTermsStr);
        if (forbiddenTerms.isEmpty() || text == null || text.isEmpty()) {
            return "{\"violations\": [], \"total\": 0, \"passed\": true}";
        }

        String lowerText = text.toLowerCase();
        List<Map<String, Object>> violations = new ArrayList<>();

        for (String term : forbiddenTerms) {
            // 逐词精确匹配（大小写不敏感）
            int pos = lowerText.indexOf(term.toLowerCase());
            if (pos >= 0) {
                // 找到后继续搜索所有出现位置
                int count = 0;
                int searchPos = 0;
                while ((searchPos = lowerText.indexOf(term.toLowerCase(), searchPos)) >= 0) {
                    count++;
                    // 提取上下文 (前后 20 字符)
                    int ctxStart = Math.max(0, searchPos - 20);
                    int ctxEnd = Math.min(text.length(), searchPos + term.length() + 20);
                    String context = text.substring(ctxStart, ctxEnd).replace('\n', ' ');

                    Map<String, Object> violation = new LinkedHashMap<>();
                    violation.put("term", term);
                    violation.put("severity", "minor");
                    violation.put("context", "..." + context + "...");
                    violation.put("position", searchPos);
                    violations.add(violation);
                    searchPos += term.length();
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(Map.of(
                    "violations", violations,
                    "total", violations.size(),
                    "passed", violations.isEmpty()
            ));
        } catch (Exception e) {
            return "{\"violations\": [], \"total\": 0, \"passed\": true, \"error\": true}";
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTerms(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            if (json.trim().startsWith("[")) {
                return objectMapper.readValue(json, List.class);
            }
            // 逗号分隔也支持
            return Arrays.stream(json.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
}
