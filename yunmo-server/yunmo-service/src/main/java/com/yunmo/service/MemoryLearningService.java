package com.yunmo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.domain.entity.MemoryRule;
import com.yunmo.domain.repository.MemoryRuleRepository;
import com.yunmo.llm.adapter.MultiProviderChatModel;
import com.yunmo.llm.provider.ProviderRegistry;
import org.apache.commons.text.diff.StringsComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记忆学习服务 — 替代 Python memory.py
 * AI 原稿 vs 作者修改稿 diff → LLM 提取规则 → MemoryRule
 */
@Service
public class MemoryLearningService {

    private static final Logger log = LoggerFactory.getLogger(MemoryLearningService.class);
    private final MemoryRuleRepository repo;
    private final MultiProviderChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MemoryLearningService(MemoryRuleRepository repo, ProviderRegistry registry) {
        this.repo = repo;
        this.chatModel = MultiProviderChatModel.create(registry.get("deepseek"), "deepseek-v4-pro");
    }

    /** 计算 AI 原稿与修改稿的实质性差异比例 */
    public double computeChangeRatio(String original, String modified) {
        if (original == null || modified == null) return 0;
        StringsComparator comparator = new StringsComparator(original, modified);
        int lcs = comparator.getScript().getLCSLength();
        int total = Math.max(original.length(), modified.length());
        return total > 0 ? 1.0 - (double) lcs / total : 0;
    }

    /** 从编辑中学习可复用规则 */
    public List<MemoryRule> learnFromEdit(String novelId, String chapterId,
                                           String aiDraft, String userEdit) {
        if (aiDraft == null || userEdit == null
                || aiDraft.isBlank() || userEdit.isBlank()) {
            log.info("草稿为空，跳过学习");
            return List.of();
        }

        double changeRatio = computeChangeRatio(aiDraft, userEdit);
        if (changeRatio < 0.1) {
            log.info("修改量过小 (ratio={:.2f})，跳过学习", changeRatio);
            return List.of();
        }

        // 调用 LLM 提取规则
        var response = chatModel.generate(
                dev.langchain4j.data.message.SystemMessage.from("""
                    分析 AI 原稿与作者修改稿的差异，提取作者偏好的写作规则。
                    JSON 格式: [{"pattern":"...","replacement":"...","category":"anti-ai|writer-style","priority":50}]
                    """),
                dev.langchain4j.data.message.UserMessage.from(String.format("""
                    ## AI 原稿
                    %s

                    ## 作者修改稿
                    %s

                    ## 修改比例: %.0f%%
                    """, aiDraft.length() > 2000 ? aiDraft.substring(0, 2000) : aiDraft,
                        userEdit.length() > 2000 ? userEdit.substring(0, 2000) : userEdit,
                        changeRatio * 100))
        );

        String llmResult = response.content().text();
        log.info("记忆学习完成: {}", llmResult.length() > 100 ? llmResult.substring(0, 100) + "..." : llmResult);

        // 解析 LLM 返回的 JSON 数组为多条规则
        List<MemoryRule> rules = new ArrayList<>();
        try {
            String json = llmResult.replace("```json", "").replace("```", "").trim();
            if (!json.startsWith("[")) {
                int s = json.indexOf('[');
                int e = json.lastIndexOf(']');
                if (s >= 0 && e > s) json = json.substring(s, e + 1);
            }
            var items = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            for (var item : items) {
                MemoryRule rule = new MemoryRule();
                rule.setNovelId(novelId);
                rule.setSourceChapterId(chapterId);
                rule.setPattern((String) item.getOrDefault("pattern", ""));
                rule.setReplacement((String) item.getOrDefault("replacement", ""));
                rule.setCategory((String) item.getOrDefault("category", "writer-style"));
                rule.setPriority(item.get("priority") instanceof Number n ? n.intValue() : 50);
                rules.add(rule);
            }
            if (!rules.isEmpty()) {
                repo.saveAll(rules);
            }
        } catch (Exception e) {
            // 解析失败时回退到单条规则
            log.warn("多规则解析失败，回退为单条规则: {}", e.getMessage());
            MemoryRule rule = new MemoryRule();
            rule.setNovelId(novelId);
            rule.setSourceChapterId(chapterId);
            rule.setCategory("writer-style");
            rule.setPattern("修改比例: " + String.format("%.0f%%", changeRatio * 100));
            rule.setReplacement(llmResult);
            rule.setPriority(50);
            repo.save(rule);
            rules.add(rule);
        }

        return rules;
    }

    /** 获取适用于当前生成的高优先级规则 */
    public List<MemoryRule> getApplicableRules(String novelId) {
        return repo.findByNovelIdOrderByPriorityDesc(novelId)
                .stream()
                .filter(r -> r.getPriority() >= 30)
                .collect(Collectors.toList());
    }
}
