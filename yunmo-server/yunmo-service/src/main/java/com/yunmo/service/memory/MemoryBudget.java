package com.yunmo.service.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆预算分配器 — 根据任务类型按比例分配三层记忆的条目上限。
 *
 * <h3>三层记忆架构</h3>
 * <pre>
 * 工作记忆 (Working Memory) — 当前章纲 + 近3-5章摘要 + 主角快照
 * 情节记忆 (Episodic Memory) — 近10条状态变更 + 关系变更 + 出场记录
 * 语义记忆 (Semantic Memory) — character_state(优先级0) > relationship(1) >
 *     story_fact(2) > open_loop(3) > reader_promise(4) > world_rule(5) > timeline(6)
 * </pre>
 *
 * <h3>预算分配表</h3>
 * <pre>
 * 任务类型  | working | episodic | semantic | 总量上限
 * write     |   45%   |   30%    |   25%    |   30
 * review    |   35%   |   35%    |   30%    |   40
 * query     |   30%   |   45%    |   25%    |   25
 * </pre>
 */
public class MemoryBudget {

    private static final Logger log = LoggerFactory.getLogger(MemoryBudget.class);

    /** 写作用预算：45/30/25，总量30 */
    private static final BudgetConfig WRITE = new BudgetConfig(0.45, 0.30, 0.25, 30);
    /** 审阅用预算：35/35/30，总量40 */
    private static final BudgetConfig REVIEW = new BudgetConfig(0.35, 0.35, 0.30, 40);
    /** 查询用预算：30/45/25，总量25 */
    private static final BudgetConfig QUERY = new BudgetConfig(0.30, 0.45, 0.25, 25);

    /**
     * 预算配置
     */
    private record BudgetConfig(double workingPct, double episodicPct, double semanticPct, int maxTotal) {
    }

    /**
     * 语义记忆优先级映射 — 数字越小越优先
     */
    private static final Map<String, Integer> SEMANTIC_PRIORITY = Map.ofEntries(
            Map.entry("character_state", 0),
            Map.entry("relationship", 1),
            Map.entry("story_fact", 2),
            Map.entry("open_loop", 3),
            Map.entry("reader_promise", 4),
            Map.entry("world_rule", 5),
            Map.entry("timeline", 6)
    );

    /**
     * 记忆包 — 三层记忆的分配结果
     */
    public record MemoryPack(
            List<MemoryItem> workingItems,
            List<MemoryItem> episodicItems,
            List<MemoryItem> semanticItems,
            int totalTokens
    ) {
        /** 扁平化为单一列表 */
        public List<MemoryItem> flatten() {
            List<MemoryItem> all = new ArrayList<>();
            all.addAll(workingItems);
            all.addAll(episodicItems);
            all.addAll(semanticItems);
            return Collections.unmodifiableList(all);
        }

        /** 总条目数 */
        public int totalCount() {
            return workingItems.size() + episodicItems.size() + semanticItems.size();
        }

        /** 是否为空包 */
        public boolean isEmpty() {
            return workingItems.isEmpty() && episodicItems.isEmpty() && semanticItems.isEmpty();
        }
    }

    /**
     * 根据任务类型和全文记忆列表分配预算
     *
     * @param taskType      任务类型: write | review | query
     * @param allItems      所有可用的记忆条目
     * @param chapterNumber 当前章节号（用于新鲜度排序参考）
     * @return 分配后的记忆包
     */
    public MemoryPack allocate(String taskType, List<MemoryItem> allItems, int chapterNumber) {
        if (allItems == null || allItems.isEmpty()) {
            return new MemoryPack(List.of(), List.of(), List.of(), 0);
        }

        BudgetConfig config = switch (taskType) {
            case "write" -> WRITE;
            case "review" -> REVIEW;
            case "query" -> QUERY;
            default -> {
                log.warn("[MemoryBudget] 未知任务类型: {}, 默认使用 write 预算", taskType);
                yield WRITE;
            }
        };

        // 按层分组
        Map<String, List<MemoryItem>> byLayer = allItems.stream()
                .filter(MemoryItem::isActive)
                .collect(Collectors.groupingBy(MemoryItem::layer));

        List<MemoryItem> working = byLayer.getOrDefault("working", List.of());
        List<MemoryItem> episodic = byLayer.getOrDefault("episodic", List.of());
        List<MemoryItem> semantic = byLayer.getOrDefault("semantic", List.of());

        // 计算各层分配数量
        int workingLimit = Math.max(1, (int) Math.round(config.maxTotal * config.workingPct));
        int episodicLimit = Math.max(1, (int) Math.round(config.maxTotal * config.episodicPct));
        int semanticLimit = Math.max(1, (int) Math.round(config.maxTotal * config.semanticPct));

        // 工作记忆：按 sourceChapter 降序（越新越靠前）
        List<MemoryItem> selectedWorking = working.stream()
                .sorted(Comparator.comparingInt(MemoryItem::sourceChapter).reversed())
                .limit(workingLimit)
                .collect(Collectors.toList());

        // 情节记忆：按 sourceChapter 降序
        List<MemoryItem> selectedEpisodic = episodic.stream()
                .sorted(Comparator.comparingInt(MemoryItem::sourceChapter).reversed())
                .limit(episodicLimit)
                .collect(Collectors.toList());

        // 语义记忆：按优先级排序（category 优先级 → sourceChapter 降序）
        List<MemoryItem> selectedSemantic = semantic.stream()
                .sorted(Comparator
                        .<MemoryItem>comparingInt(m -> SEMANTIC_PRIORITY.getOrDefault(m.category(), 99))
                        .thenComparing(Comparator.comparingInt(MemoryItem::sourceChapter).reversed()))
                .limit(semanticLimit)
                .collect(Collectors.toList());

        // 估算 token 数（中文约1.5 token/字）
        int totalTokens = estimateTokens(selectedWorking) + estimateTokens(selectedEpisodic) + estimateTokens(selectedSemantic);

        log.debug("[MemoryBudget] 预算分配完成 — task={}, working={}/{}, episodic={}/{}, semantic={}/{}, tokens~{}",
                taskType, selectedWorking.size(), workingLimit, selectedEpisodic.size(), episodicLimit,
                selectedSemantic.size(), semanticLimit, totalTokens);

        return new MemoryPack(
                Collections.unmodifiableList(selectedWorking),
                Collections.unmodifiableList(selectedEpisodic),
                Collections.unmodifiableList(selectedSemantic),
                totalTokens
        );
    }

    /**
     * 粗略估算 token 数 — 中文字符数 × 1.5
     */
    private int estimateTokens(List<MemoryItem> items) {
        int total = 0;
        for (MemoryItem item : items) {
            // 统计 value + subject + field 的字符数
            total += (item.value() != null ? item.value().length() : 0) * 1.5;
            total += (item.subject() != null ? item.subject().length() : 0) * 1.5;
            total += (item.field() != null ? item.field().length() : 0) * 1.5;
        }
        return (int) total;
    }

    /**
     * 获取指定 category 的语义优先级
     */
    public static int semanticPriorityOf(String category) {
        return SEMANTIC_PRIORITY.getOrDefault(category, 99);
    }
}
