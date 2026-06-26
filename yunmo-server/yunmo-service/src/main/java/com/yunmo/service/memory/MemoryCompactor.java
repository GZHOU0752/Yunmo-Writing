package com.yunmo.service.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆压缩器 — 当记忆总量超过阈值（默认500条）时触发压缩。
 *
 * <h3>压缩策略（按优先级依次执行）</h3>
 * <ol>
 *   <li><b>去重</b>: 同语义键（category+subject+field）的 outdated 条目只保留最新一条</li>
 *   <li><b>清理已回收伏笔</b>: status=resolved 的条目直接移除</li>
 *   <li><b>时间线压缩</b>: 超过50章的 timeline 条目合并为摘要</li>
 *   <li><b>全局截断</b>: active优先 > sourceChapter新 > updatedAt新</li>
 * </ol>
 */
public class MemoryCompactor {

    private static final Logger log = LoggerFactory.getLogger(MemoryCompactor.class);

    /** 默认触发阈值 */
    public static final int DEFAULT_THRESHOLD = 500;
    /** 时间线压缩触发章数 */
    private static final int TIMELINE_COMPACT_CHAPTERS = 50;
    /** 全局截断目标（压缩后的目标条数） */
    private static final int TRUNCATION_TARGET = 300;

    private final int threshold;

    public MemoryCompactor() {
        this(DEFAULT_THRESHOLD);
    }

    public MemoryCompactor(int threshold) {
        this.threshold = threshold;
    }

    /**
     * 对记忆列表执行压缩
     *
     * @param items          待压缩的记忆列表
     * @param currentChapter 当前章节号
     * @return 压缩后的记忆列表（可能为空列表，不会返回 null）
     */
    public List<MemoryItem> compact(List<MemoryItem> items, int currentChapter) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        int originalSize = items.size();
        if (originalSize < threshold) {
            log.debug("[MemoryCompactor] 记忆量 {} 未达阈值 {}，跳过压缩", originalSize, threshold);
            return new ArrayList<>(items);
        }

        log.info("[MemoryCompactor] 触发压缩 — 当前记忆量 {} >= 阈值 {}，章节={}",
                originalSize, threshold, currentChapter);

        List<MemoryItem> working = new ArrayList<>(items);

        // --- 第1步: 去重 ---
        working = deduplicate(working);
        int afterDedup = working.size();
        if (afterDedup < originalSize) {
            log.info("[MemoryCompactor] 去重: {} → {} (减少{})", originalSize, afterDedup, originalSize - afterDedup);
        }

        // --- 第2步: 清理已回收伏笔 ---
        int beforeResolved = working.size();
        working = removeResolved(working);
        int resolvedRemoved = beforeResolved - working.size();
        if (resolvedRemoved > 0) {
            log.info("[MemoryCompactor] 清理已回收条目: 移除 {} 条", resolvedRemoved);
        }

        // --- 第3步: 时间线压缩 ---
        working = compactTimeline(working, currentChapter);

        // --- 第4步: 全局截断 ---
        if (working.size() > TRUNCATION_TARGET) {
            working = globalTruncation(working, currentChapter);
            log.info("[MemoryCompactor] 全局截断: {} → {} (目标={})",
                    working.size(), TRUNCATION_TARGET, TRUNCATION_TARGET);
        }

        log.info("[MemoryCompactor] 压缩完成: {} → {} (总减少{})",
                originalSize, working.size(), originalSize - working.size());

        return working;
    }

    /**
     * 去重: 同语义键的 outdated 条目只保留最新一条（按 updatedAt 降序）
     */
    private List<MemoryItem> deduplicate(List<MemoryItem> items) {
        // 按语义键分组
        Map<String, List<MemoryItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(MemoryItem::semanticKey));

        List<MemoryItem> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<MemoryItem> group = entry.getValue();

            // 如果组内只有一条，直接保留
            if (group.size() == 1) {
                result.add(group.get(0));
                continue;
            }

            // 分离 active 和 outdated/resolved
            List<MemoryItem> actives = group.stream()
                    .filter(MemoryItem::isActive)
                    .collect(Collectors.toList());

            // 保留所有 active 条目
            result.addAll(actives);

            // outdated 条目只保留最新的一条
            List<MemoryItem> outdates = group.stream()
                    .filter(m -> !m.isActive())
                    .sorted(Comparator.comparingLong(MemoryItem::updatedAt).reversed())
                    .collect(Collectors.toList());

            if (!outdates.isEmpty()) {
                // 只保留最新的一条 outdated，标记为 outdated
                MemoryItem latest = outdates.get(0);
                result.add(latest.isOutdated() ? latest : latest.withStatus("outdated"));
            }
        }

        return result;
    }

    /**
     * 移除已回收（resolved）和已过时（outdated）的条目
     */
    private List<MemoryItem> removeResolved(List<MemoryItem> items) {
        return items.stream()
                .filter(m -> !m.isResolved())
                .collect(Collectors.toList());
    }

    /**
     * 时间线压缩: 超过 TIMELINE_COMPACT_CHAPTERS 章的 timeline 条目合并为摘要
     */
    private List<MemoryItem> compactTimeline(List<MemoryItem> items, int currentChapter) {
        // 找出所有 timeline 条目
        List<MemoryItem> timelines = items.stream()
                .filter(m -> "timeline".equals(m.category()))
                .sorted(Comparator.comparingInt(MemoryItem::sourceChapter))
                .collect(Collectors.toList());

        if (timelines.size() <= TIMELINE_COMPACT_CHAPTERS) {
            return items; // 未到压缩阈值，不处理
        }

        // 非 timeline 条目保持不变
        List<MemoryItem> others = items.stream()
                .filter(m -> !"timeline".equals(m.category()))
                .collect(Collectors.toCollection(ArrayList::new));

        // 将旧的 timeline 合并为一条摘要
        int compactBefore = currentChapter - TIMELINE_COMPACT_CHAPTERS;
        List<MemoryItem> recent = timelines.stream()
                .filter(t -> t.sourceChapter() > compactBefore)
                .collect(Collectors.toList());

        List<MemoryItem> old = timelines.stream()
                .filter(t -> t.sourceChapter() <= compactBefore)
                .collect(Collectors.toList());

        if (!old.isEmpty()) {
            // 构建压缩摘要
            StringBuilder sb = new StringBuilder();
            sb.append("早期时间线摘要（第1-").append(compactBefore).append("章）: ");
            List<String> events = old.stream()
                    .map(m -> "第" + m.sourceChapter() + "章 " + m.value())
                    .collect(Collectors.toList());
            sb.append(String.join("; ", events));

            // 截断到合适长度（不超过500字）
            String summary = sb.toString();
            if (summary.length() > 500) {
                summary = summary.substring(0, 497) + "...";
            }

            // 创建压缩后的时间线条目
            MemoryItem compacted = MemoryItem.of(
                    UUID.randomUUID().toString(),
                    "semantic",
                    "timeline",
                    "时间线",
                    "compacted_timeline",
                    summary,
                    compactBefore,
                    "由 " + old.size() + " 条旧时间线压缩而成"
            );

            others.add(compacted);
            log.debug("[MemoryCompactor] 时间线压缩: {} 条旧条目 → 1 条摘要", old.size());
        }

        // 保留近期时间线条目
        others.addAll(recent);

        return others;
    }

    /**
     * 全局截断: 按优先级保留条目
     * <p>
     * 排序规则: active优先 > semantic优先级高 > sourceChapter新 > updatedAt新
     * </p>
     */
    private List<MemoryItem> globalTruncation(List<MemoryItem> items, int currentChapter) {
        return items.stream()
                .sorted(createTruncationComparator())
                .limit(TRUNCATION_TARGET)
                .collect(Collectors.toList());
    }

    /**
     * 截断排序比较器: active优先 > 语义优先级高 > sourceChapter新 > updatedAt新
     */
    private Comparator<MemoryItem> createTruncationComparator() {
        return Comparator
                // active 排在最前面
                .<MemoryItem>comparingInt(m -> m.isActive() ? 0 : 1)
                // 语义优先级（数字越小越优先）
                .thenComparingInt(m -> MemoryBudget.semanticPriorityOf(m.category()))
                // 来源章节越新越靠前
                .thenComparing(Comparator.comparingInt(MemoryItem::sourceChapter).reversed())
                // 更新时间越新越靠前
                .thenComparing(Comparator.comparingLong(MemoryItem::updatedAt).reversed());
    }

    /**
     * 判断是否需要触发压缩
     */
    public boolean shouldCompact(int itemCount) {
        return itemCount >= threshold;
    }
}
