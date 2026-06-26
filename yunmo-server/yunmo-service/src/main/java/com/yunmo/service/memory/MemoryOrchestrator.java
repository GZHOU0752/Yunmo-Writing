package com.yunmo.service.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.MemorySnapshot;
import com.yunmo.domain.entity.OutlineNode;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.MemorySnapshotRepository;
import com.yunmo.domain.repository.OutlineNodeRepository;
import com.yunmo.service.memory.MemoryBudget.MemoryPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆编排器 — 三层记忆架构的核心调度引擎。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>组装记忆包（buildMemoryPack）— 按任务类型分配三层预算</li>
 *   <li>更新记忆（updateMemory）— 合并新条目并持久化到 DB</li>
 *   <li>历史回填（bootstrapFromHistory）— 从已有章节数据构建初始记忆</li>
 *   <li>自动压缩 — 每50章或记忆量超过500条时触发</li>
 * </ul>
 *
 * <h3>过滤链路</h3>
 * <pre>
 * 大纲关键词匹配 → 优先级排序 → 新鲜度排序 → source_window(20章)保留
 * </pre>
 */
@Service
public class MemoryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MemoryOrchestrator.class);

    /** 源代码窗口：保留最近20章的条目不受过滤影响 */
    private static final int SOURCE_WINDOW = 20;
    /** 自动压缩触发章数间隔 */
    private static final int COMPACT_CHAPTER_INTERVAL = 50;
    /** 自动压缩触发条目数 */
    private static final int COMPACT_ITEM_THRESHOLD = 500;

    private final MemorySnapshotRepository snapshotRepo;
    private final ChapterRepository chapterRepo;
    private final OutlineNodeRepository outlineNodeRepo;
    private final MemoryBudget budget;
    private final MemoryCompactor compactor;
    private final ObjectMapper mapper;

    public MemoryOrchestrator(MemorySnapshotRepository snapshotRepo,
                              ChapterRepository chapterRepo,
                              OutlineNodeRepository outlineNodeRepo) {
        this.snapshotRepo = snapshotRepo;
        this.chapterRepo = chapterRepo;
        this.outlineNodeRepo = outlineNodeRepo;
        this.budget = new MemoryBudget();
        this.compactor = new MemoryCompactor(COMPACT_ITEM_THRESHOLD);
        this.mapper = new ObjectMapper();
    }

    // ==================== 核心 API ====================

    /**
     * 组装记忆包 — 为 LLM 写作/审阅/查询提供三层记忆上下文。
     *
     * @param novelId       小说ID
     * @param chapterNumber 当前章节号
     * @param taskType      任务类型: write | review | query
     * @return 分配后的记忆包（不会返回 null，可能为空包）
     */
    public MemoryPack buildMemoryPack(String novelId, int chapterNumber, String taskType) {
        try {
            // 加载所有记忆条目
            List<MemoryItem> allItems = loadAllItems(novelId);
            if (allItems.isEmpty()) {
                log.debug("[MemoryOrchestrator] 无记忆条目 — novel={}, chapter={}", novelId, chapterNumber);
                return new MemoryPack(List.of(), List.of(), List.of(), 0);
            }

            // 过滤链路
            List<MemoryItem> filtered = applyFilters(allItems, novelId, chapterNumber);

            // 预算分配
            MemoryPack pack = budget.allocate(taskType, filtered, chapterNumber);

            log.info("[MemoryOrchestrator] 记忆包组装完成 — novel={}, chapter={}, task={}, total={}, tokens~{}",
                    novelId, chapterNumber, taskType, pack.totalCount(), pack.totalTokens());

            return pack;

        } catch (Exception e) {
            log.error("[MemoryOrchestrator] 构建记忆包失败 — novel={}, chapter={}", novelId, chapterNumber, e);
            return new MemoryPack(List.of(), List.of(), List.of(), 0);
        }
    }

    /**
     * 更新记忆 — 将新条目合并到持久化存储中。
     *
     * @param novelId       小说ID
     * @param chapterNumber 当前章节号
     * @param newItems      新记忆条目
     */
    @Transactional
    public void updateMemory(String novelId, int chapterNumber, List<MemoryItem> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            log.debug("[MemoryOrchestrator] 无新记忆条目，跳过更新 — novel={}, chapter={}", novelId, chapterNumber);
            return;
        }

        try {
            // 加载当前快照
            MemorySnapshot snapshot = loadOrCreateSnapshot(novelId, chapterNumber);
            List<MemoryItem> existing = parseItems(snapshot.getMemoryJson());

            // 合并：新条目的语义键若与旧条目冲突，标记旧条目为 outdated
            Set<String> newKeys = newItems.stream()
                    .map(MemoryItem::semanticKey)
                    .collect(Collectors.toSet());

            List<MemoryItem> merged = new ArrayList<>();
            for (MemoryItem old : existing) {
                if (newKeys.contains(old.semanticKey())) {
                    // 冲突：保留旧条目但标记为 outdated
                    merged.add(old.withStatus("outdated"));
                } else {
                    merged.add(old);
                }
            }

            // 添加新条目
            merged.addAll(newItems);

            // 检查是否需要压缩
            boolean shouldCompact = shouldTriggerCompact(merged.size(), chapterNumber);
            if (shouldCompact) {
                merged = compactor.compact(merged, chapterNumber);
            }

            // 持久化
            String json = serializeItems(merged);
            snapshot.setMemoryJson(json);
            snapshot.setItemCount(merged.size());
            snapshot.setChapterNumber(chapterNumber);
            snapshot.setUpdatedAt(LocalDateTime.now());
            snapshotRepo.save(snapshot);

            log.info("[MemoryOrchestrator] 记忆已更新 — novel={}, chapter={}, new={}, total={}, compacted={}",
                    novelId, chapterNumber, newItems.size(), merged.size(), shouldCompact);

        } catch (Exception e) {
            log.error("[MemoryOrchestrator] 更新记忆失败 — novel={}, chapter={}", novelId, chapterNumber, e);
            // 异常安全：不抛出让调用方崩溃
        }
    }

    /**
     * 从已有章节历史回填记忆 — 用于首次启用记忆分层功能。
     *
     * @param novelId 小说ID
     */
    @Transactional
    public void bootstrapFromHistory(String novelId) {
        try {
            // 检查是否已有快照
            Optional<MemorySnapshot> existing = snapshotRepo.findTopByNovelIdOrderByChapterNumberDesc(novelId);
            if (existing.isPresent() && existing.get().getItemCount() > 0) {
                log.info("[MemoryOrchestrator] 已有记忆快照，跳过回填 — novel={}, items={}",
                        novelId, existing.get().getItemCount());
                return;
            }

            // 加载所有已有章节
            List<Chapter> chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
            if (chapters.isEmpty()) {
                log.info("[MemoryOrchestrator] 无已有章节，跳过回填 — novel={}", novelId);
                return;
            }

            List<MemoryItem> bootstrapped = new ArrayList<>();
            for (Chapter ch : chapters) {
                List<MemoryItem> chapterItems = extractItemsFromChapter(ch);
                bootstrapped.addAll(chapterItems);
            }

            if (bootstrapped.isEmpty()) {
                log.info("[MemoryOrchestrator] 未提取到有效记忆条目 — novel={}", novelId);
                return;
            }

            // 保存初始快照
            int lastChapter = chapters.get(chapters.size() - 1).getChapterNumber();
            String json = serializeItems(bootstrapped);
            MemorySnapshot snapshot = new MemorySnapshot();
            snapshot.setNovelId(novelId);
            snapshot.setMemoryJson(json);
            snapshot.setItemCount(bootstrapped.size());
            snapshot.setChapterNumber(lastChapter);
            snapshotRepo.save(snapshot);

            log.info("[MemoryOrchestrator] 历史回填完成 — novel={}, chapters={}, items={}",
                    novelId, chapters.size(), bootstrapped.size());

        } catch (Exception e) {
            log.error("[MemoryOrchestrator] 历史回填失败 — novel={}", novelId, e);
        }
    }

    /**
     * 获取当前记忆条目的总数
     */
    public int getMemoryCount(String novelId) {
        try {
            Optional<MemorySnapshot> snapshot = snapshotRepo.findTopByNovelIdOrderByChapterNumberDesc(novelId);
            return snapshot.map(MemorySnapshot::getItemCount).orElse(0);
        } catch (Exception e) {
            log.debug("[MemoryOrchestrator] 获取记忆数失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 手动触发压缩
     */
    @Transactional
    public void forceCompact(String novelId, int currentChapter) {
        try {
            Optional<MemorySnapshot> snapshotOpt = snapshotRepo.findTopByNovelIdOrderByChapterNumberDesc(novelId);
            if (snapshotOpt.isEmpty()) return;

            MemorySnapshot snapshot = snapshotOpt.get();
            List<MemoryItem> items = parseItems(snapshot.getMemoryJson());
            List<MemoryItem> compacted = compactor.compact(items, currentChapter);

            snapshot.setMemoryJson(serializeItems(compacted));
            snapshot.setItemCount(compacted.size());
            snapshot.setChapterNumber(currentChapter);
            snapshot.setUpdatedAt(LocalDateTime.now());
            snapshotRepo.save(snapshot);

            log.info("[MemoryOrchestrator] 手动压缩完成 — novel={}, {} → {}",
                    novelId, items.size(), compacted.size());

        } catch (Exception e) {
            log.error("[MemoryOrchestrator] 手动压缩失败 — novel={}", novelId, e);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 加载所有记忆条目
     */
    private List<MemoryItem> loadAllItems(String novelId) {
        try {
            Optional<MemorySnapshot> snapshotOpt = snapshotRepo.findTopByNovelIdOrderByChapterNumberDesc(novelId);
            if (snapshotOpt.isEmpty()) {
                return List.of();
            }
            return parseItems(snapshotOpt.get().getMemoryJson());
        } catch (Exception e) {
            log.debug("[MemoryOrchestrator] 加载记忆失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 加载或创建空快照
     */
    private MemorySnapshot loadOrCreateSnapshot(String novelId, int chapterNumber) {
        Optional<MemorySnapshot> existing = snapshotRepo.findTopByNovelIdOrderByChapterNumberDesc(novelId);
        if (existing.isPresent()) {
            return existing.get();
        }
        MemorySnapshot snapshot = new MemorySnapshot();
        snapshot.setNovelId(novelId);
        snapshot.setMemoryJson("[]");
        snapshot.setItemCount(0);
        snapshot.setChapterNumber(chapterNumber);
        return snapshot;
    }

    /**
     * 过滤链路: 大纲关键词匹配 → 优先级排序 → 新鲜度排序 → source_window保留
     */
    private List<MemoryItem> applyFilters(List<MemoryItem> items, String novelId, int currentChapter) {
        // 第1步: source_window — 最近20章的条目无条件保留
        Set<String> windowItemIds = items.stream()
                .filter(m -> m.sourceChapter() > currentChapter - SOURCE_WINDOW)
                .map(MemoryItem::id)
                .collect(Collectors.toSet());

        // 第2步: 大纲关键词匹配 — 提取当前章大纲的关键词
        Set<String> outlineKeywords = extractOutlineKeywords(novelId, currentChapter);

        // 第3步: 综合评分排序
        List<MemoryItem> sorted = items.stream()
                .sorted(createRelevanceComparator(windowItemIds, outlineKeywords))
                .collect(Collectors.toList());

        log.debug("[MemoryOrchestrator] 过滤完成 — total={}, source_window={}, outline_keywords={}",
                items.size(), windowItemIds.size(), outlineKeywords.size());

        return sorted;
    }

    /**
     * 从当前章大纲中提取关键词
     */
    private Set<String> extractOutlineKeywords(String novelId, int chapterNumber) {
        try {
            List<OutlineNode> nodes = outlineNodeRepo.findByNovelIdAndChapterNumber(novelId, chapterNumber);
            Set<String> keywords = new HashSet<>();
            for (OutlineNode node : nodes) {
                if (node.getTitle() != null) {
                    Collections.addAll(keywords, node.getTitle().split("[，。；、\\s]+"));
                }
                if (node.getCausalSentence() != null) {
                    Collections.addAll(keywords, node.getCausalSentence().split("[，。；、\\s]+"));
                }
                if (node.getOutlineContent() != null) {
                    String[] words = node.getOutlineContent().split("[，。；、\\s]+");
                    // 只取较长的关键词（>=2字）
                    for (String w : words) {
                        if (w.length() >= 2) keywords.add(w);
                    }
                }
            }
            // 过滤掉过短的关键词
            return keywords.stream()
                    .filter(k -> k.length() >= 2)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.debug("[MemoryOrchestrator] 提取大纲关键词失败: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 创建相关性排序比较器
     * <p>
     * 排序规则: source_window成员优先 > 大纲关键词命中 > 优先级高 > 章节新 > 更新新
     * </p>
     */
    private Comparator<MemoryItem> createRelevanceComparator(Set<String> windowItemIds, Set<String> outlineKeywords) {
        return Comparator
                // source_window 成员排最前面
                .<MemoryItem>comparingInt(m -> windowItemIds.contains(m.id()) ? 0 : 1)
                // 大纲关键词命中计数（越多越靠前）
                .thenComparingInt(m -> -countKeywordHits(m, outlineKeywords))
                // 语义优先级
                .thenComparingInt(m -> MemoryBudget.semanticPriorityOf(m.category()))
                // 来源章节越新越靠前
                .thenComparing(Comparator.comparingInt(MemoryItem::sourceChapter).reversed())
                // 更新时间越新越靠前
                .thenComparing(Comparator.comparingLong(MemoryItem::updatedAt).reversed());
    }

    /**
     * 统计一条记忆条目命中几个大纲关键词
     */
    private int countKeywordHits(MemoryItem item, Set<String> keywords) {
        if (keywords.isEmpty()) return 0;
        int hits = 0;
        String searchText = (item.subject() + item.value() + item.field()).toLowerCase();
        for (String kw : keywords) {
            if (searchText.contains(kw)) hits++;
        }
        return hits;
    }

    /**
     * 判断是否应触发自动压缩
     */
    private boolean shouldTriggerCompact(int itemCount, int chapterNumber) {
        // 每50章触发一次，或超过500条触发
        return itemCount >= COMPACT_ITEM_THRESHOLD || chapterNumber % COMPACT_CHAPTER_INTERVAL == 0;
    }

    /**
     * 从章节实体中提取记忆条目（用于历史回填）
     */
    private List<MemoryItem> extractItemsFromChapter(Chapter ch) {
        List<MemoryItem> items = new ArrayList<>();
        int cn = ch.getChapterNumber();

        // 尝试从文件系统读取已有的章节记忆
        List<MemoryItem> fileItems = tryLoadChapterMemoryFile(ch.getNovelId(), cn);
        if (!fileItems.isEmpty()) {
            return fileItems;
        }

        // 回退：从章节内容提取基础条目
        String content = ch.getContent();
        if (content == null || content.isBlank()) return items;

        String trimmed = content.length() > 200 ? content.substring(0, 200) : content;

        // 工作记忆: 章节摘要
        items.add(MemoryItem.of(
                UUID.randomUUID().toString(), "working", "chapter_summary",
                "第" + cn + "章", "summary",
                trimmed, cn, "历史回填"
        ));

        return items;
    }

    /**
     * 尝试从文件系统加载已有的章节记忆 JSON
     */
    private List<MemoryItem> tryLoadChapterMemoryFile(String novelId, int chapterNumber) {
        try {
            Path memFile = Path.of("data", "memory", novelId, "ch_" + chapterNumber + ".json");
            if (!Files.exists(memFile)) return List.of();

            // 读取旧的 ChapterMemory 格式并转换为新的 MemoryItem
            String json = Files.readString(memFile);
            Map<String, Object> oldMem = mapper.readValue(json, new TypeReference<>() {});
            List<MemoryItem> items = new ArrayList<>();

            String summary = (String) oldMem.getOrDefault("summary", "");
            if (!summary.isEmpty()) {
                items.add(MemoryItem.of(
                        UUID.randomUUID().toString(), "working", "chapter_summary",
                        "第" + chapterNumber + "章", "summary", summary,
                        chapterNumber, "从旧格式迁移"
                ));
            }

            // 新角色 → 语义记忆
            @SuppressWarnings("unchecked")
            List<String> newChars = (List<String>) oldMem.getOrDefault("newCharacters", List.of());
            for (String name : newChars) {
                items.add(MemoryItem.of(
                        UUID.randomUUID().toString(), "semantic", "character_state",
                        name, "introduced", "首次出场于第" + chapterNumber + "章",
                        chapterNumber, "从旧格式迁移"
                ));
            }

            return items;
        } catch (IOException e) {
            log.debug("[MemoryOrchestrator] 读取旧记忆文件失败 ch_{}: {}", chapterNumber, e.getMessage());
            return List.of();
        }
    }

    // ==================== JSON 序列化 ====================

    /**
     * 将记忆列表序列化为 JSON 字符串
     */
    private String serializeItems(List<MemoryItem> items) {
        try {
            // 转换为可序列化的 Map 列表（record 的序列化可能有问题，手动映射）
            List<Map<String, Object>> list = new ArrayList<>();
            for (MemoryItem item : items) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", item.id());
                map.put("layer", item.layer());
                map.put("category", item.category());
                map.put("subject", item.subject());
                map.put("field", item.field());
                map.put("value", item.value());
                map.put("payload", item.payload());
                map.put("status", item.status());
                map.put("sourceChapter", item.sourceChapter());
                map.put("evidence", item.evidence());
                map.put("updatedAt", item.updatedAt());
                list.add(map);
            }
            return mapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("[MemoryOrchestrator] 序列化失败", e);
            return "[]";
        }
    }

    /**
     * 从 JSON 字符串反序列化为记忆列表
     */
    private List<MemoryItem> parseItems(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> list = mapper.readValue(json, new TypeReference<>() {});
            List<MemoryItem> items = new ArrayList<>();
            for (Map<String, Object> map : list) {
                items.add(MemoryItem.of(
                        safeString(map, "id", UUID.randomUUID().toString()),
                        safeString(map, "layer", "semantic"),
                        safeString(map, "category", "story_fact"),
                        safeString(map, "subject", ""),
                        safeString(map, "field", ""),
                        safeString(map, "value", ""),
                        safeMap(map, "payload"),
                        safeString(map, "status", "active"),
                        intFrom(map, "sourceChapter", 1),
                        safeString(map, "evidence", ""),
                        longFrom(map, "updatedAt", System.currentTimeMillis())
                ));
            }
            return items;
        } catch (JsonProcessingException e) {
            log.error("[MemoryOrchestrator] 反序列化失败", e);
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return new HashMap<>();
    }

    private String safeString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private int intFrom(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private long longFrom(Map<String, Object> map, String key, long defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
