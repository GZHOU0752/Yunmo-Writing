package com.yunmo.common.util;

/**
 * 从 LLM 返回文本中提取 JSON 子串 — 括号计数法，防嵌套/代码块干扰。
 * 统一实现，避免各模块各自复制导致的细微行为差异。
 */
public final class JsonExtractor {

    private JsonExtractor() {}

    /**
     * 从 LLM 返回的文本中提取 JSON 对象子串。
     * 先去掉 markdown 代码块标记，再用括号计数法找到完整 JSON。
     * 返回提取到的 JSON 字符串，失败时返回原始文本。
     */
    public static String extractJson(String text) {
        if (text == null || text.isEmpty()) return text;

        // 去掉 markdown 代码块标记（只去掉首尾的标记，不去掉内容中的）
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline >= 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        cleaned = cleaned.replaceFirst("\\n```\\s*$", "").trim();

        int start = cleaned.indexOf('{');
        if (start < 0) return cleaned;

        int depth = 0;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return cleaned.substring(start, i + 1);
                }
            }
        }
        // 括号未闭合，fallback 到 lastIndexOf
        int end = cleaned.lastIndexOf('}');
        return end > start ? cleaned.substring(start, end + 1) : cleaned;
    }
}
