package com.yunmo.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Token 预算估算服务 — 统计上下文中各模块的 token 消耗
 * 中文 1 字 ≈ 1.5 token，英文 1 词 ≈ 1.3 token
 */
@Service
public class TokenBudgetService {

    public record BudgetItem(String name, String label, int charCount, int estimatedTokens) {}

    public record BudgetReport(List<BudgetItem> items, int totalTokens, int budgetLimit) {}

    /** 估算中文文本的 token 数（非常粗略：1 中文字 ≈ 1.5 token，1 英文词 ≈ 1.3 token） */
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int englishWords = 0;
        boolean inWord = false;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                chineseChars++;
                if (inWord) { englishWords++; inWord = false; }
            } else if (Character.isLetter(c)) {
                inWord = true;
            } else {
                if (inWord) { englishWords++; inWord = false; }
            }
        }
        if (inWord) englishWords++;
        return (int) Math.round(chineseChars * 1.5 + englishWords * 1.3);
    }

    /** 构建上下文预算报告 */
    public BudgetReport buildReport(Map<String, Object> state, int budgetLimit) {
        List<BudgetItem> items = new ArrayList<>();
        int total = 0;

        addItem(items, "context_text", "前文上下文", state);
        addItem(items, "chapter_plan", "章节大纲", state);
        addItem(items, "user_focus", "写作指令", state);
        addItem(items, "rag_context", "参考素材", state);
        addItem(items, "writing_guide", "写作指南", state);
        addItem(items, "character_profiles", "角色档案", state);
        addItem(items, "incremental_memory", "增量记忆", state);
        addItem(items, "continuity_warnings", "连续性警告", state);

        for (BudgetItem item : items) {
            total += item.estimatedTokens();
        }

        return new BudgetReport(items, total, budgetLimit);
    }

    private void addItem(List<BudgetItem> items, String key, String label,
                         Map<String, Object> state) {
        Object val = state.get(key);
        String text;
        if (val instanceof String s) {
            text = s;
        } else if (val instanceof List<?> list && !list.isEmpty()) {
            // 角色列表等复杂对象，做简要估算
            text = list.toString();
        } else {
            return; // 跳过空值
        }
        int tokens = estimate(text);
        items.add(new BudgetItem(key, label, text.length(), tokens));
    }
}
