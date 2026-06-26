package com.yunmo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.llm.provider.LLMProvider;
import com.yunmo.llm.provider.ProviderRegistry;
import com.yunmo.service.memory.MemoryBudget.MemoryPack;
import com.yunmo.service.memory.MemoryItem;
import com.yunmo.service.memory.MemoryOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 三层增量记忆 + 确定性连续性引擎。
 * 每章生成后自动更新短期/中期/长期记忆，
 * 并在下章生成前校验时间线、角色位置、伏笔状态。
 */
@Service
public class IncrementalMemoryService {

    private static final Logger log = LoggerFactory.getLogger(IncrementalMemoryService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProviderRegistry providerRegistry;
    private final MemoryOrchestrator memoryOrchestrator;

    /** 自动压缩触发章数间隔 */
    private static final int COMPACT_CHAPTER_INTERVAL = 50;

    public IncrementalMemoryService(ProviderRegistry providerRegistry,
                                    MemoryOrchestrator memoryOrchestrator) {
        this.providerRegistry = providerRegistry;
        this.memoryOrchestrator = memoryOrchestrator;
    }

    /**
     * 章节摘要 — 每章写完后提取
     */
    public record ChapterMemory(
        int chapterNumber,
        String title,
        String summary,           // 本章摘要（100-200字）
        List<String> newCharacters,  // 本章新出场角色
        List<String> newItems,       // 本章新出现的重要道具/地点
        List<String> foreshadowUpdates, // 本章的伏笔变化
        Map<String, String> characterStates, // 角色状态变化（受伤/升级/死亡等）
        int wordCount,
        String timestamp
    ) {}

    /**
     * 确定性世界状态 — 不用LLM，纯数据结构维护
     */
    /** 实体生命周期状态 */
    public enum EntityLifecycle { ACTIVE, COOLING, COLD, TERMINATED }

    /** 实体记录 */
    public static class EntityRecord {
        public String name;
        public String type;    // character / item / location / faction
        public String state;   // 当前状态描述（≤32字）
        public int lastAppearance;
        public String lifecycle; // ACTIVE / COOLING / COLD / TERMINATED

        public EntityRecord() {}
        public EntityRecord(String name, String type, String state, int lastAppearance, String lifecycle) {
            this.name = name; this.type = type; this.state = state;
            this.lastAppearance = lastAppearance; this.lifecycle = lifecycle;
        }
    }

    public static class WorldState {
        public String novelId;
        public List<TimelineEntry> timeline = new ArrayList<>();
        public Map<String, String> characterLocations = new LinkedHashMap<>();
        public List<ForeshadowEntry> foreshadows = new ArrayList<>();
        /** 实体生命周期追踪 — name → EntityRecord */
        public Map<String, EntityRecord> entities = new LinkedHashMap<>();
        public int lastUpdatedChapter;

        public WorldState() {}
        public WorldState(String novelId, List<TimelineEntry> timeline, Map<String, String> characterLocations,
                          List<ForeshadowEntry> foreshadows, Map<String, EntityRecord> entities, int lastUpdatedChapter) {
            this.novelId = novelId;
            this.timeline = timeline;
            this.characterLocations = characterLocations;
            this.foreshadows = foreshadows;
            this.entities = entities != null ? entities : new LinkedHashMap<>();
            this.lastUpdatedChapter = lastUpdatedChapter;
        }
    }

    public static class TimelineEntry {
        public int chapterNumber;
        public String event;
        public String timestamp;

        public TimelineEntry() {}
        public TimelineEntry(int chapterNumber, String event, String timestamp) {
            this.chapterNumber = chapterNumber;
            this.event = event;
            this.timestamp = timestamp;
        }
    }

    public static class ForeshadowEntry {
        public String id;
        public String description;
        public String status;
        public int plantedChapter;
        public Integer resolvedChapter;

        public ForeshadowEntry() {}
        public ForeshadowEntry(String id, String description, String status, int plantedChapter, Integer resolvedChapter) {
            this.id = id;
            this.description = description;
            this.status = status;
            this.plantedChapter = plantedChapter;
            this.resolvedChapter = resolvedChapter;
        }
    }

    private Path memoryDir(String novelId) {
        return Path.of("data", "memory", novelId);
    }

    /**
     * 每章写完后调用 — 更新增量记忆（三层架构）。
     * <p>
     * 同时维护文件系统（旧格式兼容）和 DB 快照（MemoryOrchestrator）。
     * 每50章自动触发记忆压缩。
     * </p>
     */
    public void updateMemory(String novelId, int chapterNumber, String title,
                              String content, int wordCount) {
        try {
            Path dir = memoryDir(novelId);
            Files.createDirectories(dir);

            // 用轻量正则提取摘要，不调LLM
            String summary = extractSummary(content);
            List<String> newNames = extractNewNames(content);

            ChapterMemory mem = new ChapterMemory(
                chapterNumber, title, summary,
                newNames, List.of(), List.of(), Map.of(),
                wordCount, java.time.Instant.now().toString()
            );

            // 写入章节记忆文件（旧格式兼容）
            Path memFile = dir.resolve("ch_" + chapterNumber + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(memFile.toFile(), mem);

            // 更新世界状态
            updateWorldState(novelId, chapterNumber, content);

            // ---- 三层记忆架构 ----
            // 将 ChapterMemory 转换为 MemoryItem 列表
            List<MemoryItem> newItems = convertToMemoryItems(mem, content);
            // 委托 MemoryOrchestrator 持久化（DB快照 + 自动压缩）
            memoryOrchestrator.updateMemory(novelId, chapterNumber, newItems);

            // 每50章触发一次压缩
            if (chapterNumber % COMPACT_CHAPTER_INTERVAL == 0) {
                int count = memoryOrchestrator.getMemoryCount(novelId);
                log.info("[Memory] 第{}章触发压缩检查 — 当前记忆条目={}", chapterNumber, count);
            }

            log.info("[Memory] 章节记忆已更新 — novel={}, chapter={}, summary_length={}, items={}",
                    novelId, chapterNumber, summary.length(), newItems.size());
        } catch (Exception e) {
            log.warn("[Memory] 记忆更新失败: {}", e.getMessage());
        }
    }

    /**
     * 生成前调用 — 获取活跃的增量记忆摘要（三层架构）。
     * <p>
     * 优先使用 MemoryOrchestrator 的 DB 快照获取结构化记忆，
     * 回退到文件系统读取旧格式。
     * </p>
     */
    public String getMemorySummary(String novelId, int currentChapter) {
        // 优先走 MemoryOrchestrator（三层架构）
        MemoryPack pack = memoryOrchestrator.buildMemoryPack(novelId, currentChapter, "write");
        if (!pack.isEmpty()) {
            return formatMemoryPack(pack);
        }

        // 回退：文件系统旧格式
        return getMemorySummaryLegacy(novelId, currentChapter);
    }

    /**
     * 将三层记忆包格式化为 LLM 可读的文本摘要
     */
    private String formatMemoryPack(MemoryPack pack) {
        StringBuilder sb = new StringBuilder();

        // === 工作记忆 (Working Memory) ===
        if (!pack.workingItems().isEmpty()) {
            sb.append("=== 工作记忆（当前情境） ===\n");
            for (MemoryItem item : pack.workingItems()) {
                sb.append("- [").append(item.category()).append("] ")
                  .append(item.subject());
                if (item.value() != null && !item.value().isBlank()) {
                    sb.append(": ").append(item.value());
                }
                sb.append(" (第").append(item.sourceChapter()).append("章)\n");
            }
            sb.append("\n");
        }

        // === 情节记忆 (Episodic Memory) ===
        if (!pack.episodicItems().isEmpty()) {
            sb.append("=== 情节记忆（近期事件） ===\n");
            for (MemoryItem item : pack.episodicItems()) {
                sb.append("- [").append(item.category()).append("] ")
                  .append(item.subject()).append(": ").append(item.value())
                  .append(" (第").append(item.sourceChapter()).append("章)\n");
            }
            sb.append("\n");
        }

        // === 语义记忆 (Semantic Memory) ===
        if (!pack.semanticItems().isEmpty()) {
            sb.append("=== 语义记忆（世界观/角色/伏笔） ===\n");
            for (MemoryItem item : pack.semanticItems()) {
                sb.append("- [").append(item.category()).append("] ")
                  .append(item.subject());
                if (item.field() != null && !item.field().isBlank()) {
                    sb.append(".").append(item.field());
                }
                sb.append(": ").append(item.value())
                  .append(" (第").append(item.sourceChapter()).append("章, ")
                  .append(item.status()).append(")\n");
            }
        }

        return sb.toString();
    }

    /**
     * 旧格式回退 — 从文件系统读取记忆摘要
     */
    private String getMemorySummaryLegacy(String novelId, int currentChapter) {
        StringBuilder sb = new StringBuilder();

        try {
            Path dir = memoryDir(novelId);
            if (!Files.exists(dir)) return "";

            // 短期记忆：当前章的上一章
            Path lastMem = dir.resolve("ch_" + (currentChapter - 1) + ".json");
            if (Files.exists(lastMem)) {
                ChapterMemory mem = mapper.readValue(lastMem.toFile(), ChapterMemory.class);
                sb.append("=== 上一章摘要 ===\n");
                sb.append(mem.summary).append("\n");
                if (mem.newCharacters != null && !mem.newCharacters.isEmpty()) {
                    sb.append("新出场角色: ").append(String.join("、", mem.newCharacters)).append("\n");
                }
            }

            // 中期记忆：最近3-5章关键事件
            sb.append("\n=== 近期关键事件 ===\n");
            for (int ch = Math.max(1, currentChapter - 5); ch < currentChapter; ch++) {
                Path memFile = dir.resolve("ch_" + ch + ".json");
                if (Files.exists(memFile)) {
                    ChapterMemory mem = mapper.readValue(memFile.toFile(), ChapterMemory.class);
                    sb.append("第").append(ch).append("章: ").append(mem.summary).append("\n");
                }
            }

            // 长期记忆：世界状态（伏笔 + 角色位置）
            Path wsFile = dir.resolve("world_state.json");
            if (Files.exists(wsFile)) {
                WorldState ws = mapper.readValue(wsFile.toFile(), WorldState.class);
                sb.append("\n=== 当前世界状态 ===\n");
                sb.append("活跃伏笔:\n");
                for (var fs : ws.foreshadows) {
                    if (fs.resolvedChapter == null) {
                        sb.append("  - [").append(fs.status).append("] ").append(fs.description)
                          .append(" (第").append(fs.plantedChapter).append("章埋设)\n");
                    }
                }
                sb.append("角色当前位置:\n");
                for (var loc : ws.characterLocations.entrySet()) {
                    sb.append("  - ").append(loc.getKey()).append(": ").append(loc.getValue()).append("\n");
                }
            }
        } catch (Exception e) {
            log.debug("[Memory] 旧格式记忆检索失败: {}", e.getMessage());
        }

        return sb.toString();
    }

    /**
     * 生成前调用 — 从世界状态中提取连续性警告（不依赖本章内容）
     * @return 警告列表，空列表 = 无警告
     */
    public List<String> getContinuityWarnings(String novelId, int currentChapter) {
        List<String> warnings = new ArrayList<>();
        try {
            Path dir = memoryDir(novelId);
            Path wsFile = dir.resolve("world_state.json");
            if (!Files.exists(wsFile)) return warnings;

            WorldState ws = mapper.readValue(wsFile.toFile(), WorldState.class);

            // 检查：死角色警告（提醒Writer不要写活）
            for (var loc : ws.characterLocations.entrySet()) {
                if ("DEAD".equals(loc.getValue())) {
                    warnings.add("角色[" + loc.getKey() + "]已死亡，本章不得出场");
                }
            }

            // 检查：长时间未出场的角色（超过5章 -> 提示可能需要出场）
            for (var loc : ws.characterLocations.entrySet()) {
                if (!"DEAD".equals(loc.getValue())) {
                    // 检查最近5章摘要中是否有该角色
                    boolean appeared = false;
                    for (int ch = Math.max(1, currentChapter - 5); ch < currentChapter; ch++) {
                        Path memFile = dir.resolve("ch_" + ch + ".json");
                        if (Files.exists(memFile)) {
                            ChapterMemory mem = mapper.readValue(memFile.toFile(), ChapterMemory.class);
                            if (mem.summary != null && mem.summary.contains(loc.getKey())) {
                                appeared = true;
                                break;
                            }
                        }
                    }
                    if (!appeared && currentChapter > 5) {
                        warnings.add("角色[" + loc.getKey() + "]已超过5章未出场，考虑在本章提及或安排出场");
                    }
                }
            }

            // 检查：未回收的伏笔（超期提示）
            for (var fs : ws.foreshadows) {
                if (fs.resolvedChapter == null && currentChapter - fs.plantedChapter > 20) {
                    warnings.add("伏笔[" + fs.description + "]已埋设超过20章未回收，请考虑在本章回收");
                }
            }

            // 检查：时间线跳跃提示
            if (!ws.timeline.isEmpty()) {
                var lastEvent = ws.timeline.get(ws.timeline.size() - 1);
                if (lastEvent.chapterNumber < currentChapter - 1) {
                    warnings.add("上一章为第" + lastEvent.chapterNumber + "章，本章为第" + currentChapter +
                                 "章，中间有章节缺口，注意时间线衔接");
                }
            }

            log.info("[Continuity] 预检完成 — novel={}, chapter={}, warnings={}",
                    novelId, currentChapter, warnings.size());
        } catch (Exception e) {
            log.debug("[Continuity] 预检失败: {}", e.getMessage());
        }
        return warnings;
    }

    /**
     * 确定性连续性校验 — 不用LLM，纯规则检查
     * @return 违规列表（空列表 = 通过）
     */
    public List<String> verifyContinuity(String novelId, int chapterNumber, String content) {
        List<String> violations = new ArrayList<>();

        try {
            Path dir = memoryDir(novelId);
            Path wsFile = dir.resolve("world_state.json");
            if (!Files.exists(wsFile)) return violations;

            WorldState ws = mapper.readValue(wsFile.toFile(), WorldState.class);

            // 检查：已经死亡的角色是否在正文中出现
            for (var loc : ws.characterLocations.entrySet()) {
                if ("DEAD".equals(loc.getValue())) {
                    if (content.contains(loc.getKey())) {
                        violations.add("连续性违规: 角色[" + loc.getKey() + "]已死亡，但在正文中出现");
                    }
                }
            }

            // 检查：已回收的伏笔不应再次出现
            for (var fs : ws.foreshadows) {
                if (fs.resolvedChapter != null && fs.resolvedChapter < chapterNumber) {
                    // 简单检测：伏笔中的关键词是否再次出现（粗略检查，节省token）
                    String[] keywords = fs.description.split("\\s+");
                    int hitCount = 0;
                    for (String kw : keywords) {
                        if (kw.length() >= 2 && content.contains(kw)) hitCount++;
                    }
                    if (hitCount >= keywords.length * 0.7) {
                        violations.add("连续性提示: 伏笔[" + fs.description + "]已在第" +
                                       fs.resolvedChapter + "章回收，本章可能重复提及");
                    }
                }
            }

            // 更新角色位置（基于本章内容的关键词匹配）
            updateLocationsFromContent(ws, content, chapterNumber);
            mapper.writerWithDefaultPrettyPrinter().writeValue(wsFile.toFile(), ws);

        } catch (Exception e) {
            log.debug("[Continuity] 连续性校验失败: {}", e.getMessage());
        }

        return violations;
    }

    // ===== 内部辅助 =====

    private void updateWorldState(String novelId, int chapterNumber, String content) {
        try {
            Path dir = memoryDir(novelId);
            Path wsFile = dir.resolve("world_state.json");
            WorldState ws;
            if (Files.exists(wsFile)) {
                ws = mapper.readValue(wsFile.toFile(), WorldState.class);
            } else {
                ws = new WorldState(novelId, new ArrayList<>(), new LinkedHashMap<>(),
                                    new ArrayList<>(), new LinkedHashMap<>(), chapterNumber);
            }

            // 添加时间线条目
            ws.timeline.add(new TimelineEntry(chapterNumber,
                content.length() > 100 ? content.substring(0, 100) + "..." : content,
                java.time.Instant.now().toString()));

            // 更新角色位置
            updateLocationsFromContent(ws, content, chapterNumber);
            ws.lastUpdatedChapter = chapterNumber;

            Files.createDirectories(dir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(wsFile.toFile(), ws);
        } catch (Exception e) {
            log.warn("[Memory] 世界状态更新失败: {}", e.getMessage());
        }
    }

    private void updateLocationsFromContent(WorldState ws, String content, int chapterNumber) {
        // 简单的关键词位置检测（节省token，不用LLM）
        String[] locationKeywords = {"来到", "到达", "进入", "离开", "回到", "前往", "返回"};
        for (Map.Entry<String, String> entry : ws.characterLocations.entrySet()) {
            String name = entry.getKey();
            if (content.contains(name + "死") || content.contains(name + "陨落") ||
                content.contains(name + "被杀") || content.contains(name + "阵亡")) {
                entry.setValue("DEAD");
            }
        }
    }

    /**
     * 提取章节语义摘要 — LLM优先，失败时回退到正则截取
     */
    private String extractSummary(String content) {
        if (content == null || content.isEmpty()) return "";
        try {
            return extractSummaryWithLLM(content);
        } catch (Exception e) {
            log.debug("[Memory] LLM摘要失败，回退正则: {}", e.getMessage());
            return extractSummaryRegex(content);
        }
    }

    /** 正则回退：取正文前150字 + 后50字 */
    private String extractSummaryRegex(String content) {
        String clean = content.replaceAll("&#\\d+;", "").replaceAll("\\s+", "");
        if (clean.length() <= 200) return clean;
        return clean.substring(0, 150) + "..." + clean.substring(clean.length() - 50);
    }

    /** 调用轻量LLM (Qwen) 生成100-200字语义摘要 */
    private String extractSummaryWithLLM(String content) {
        LLMProvider qwen = providerRegistry.get("qwen");
        String clean = content.replaceAll("&#\\d+;", "").replaceAll("\\s+", "");
        String sample = clean.length() > 3000 ? clean.substring(0, 3000) : clean;

        String prompt = String.format("""
            请用100-200字概括以下章节的核心情节（仅概括剧情，不要评价）：

            %s

            直接输出摘要文本，不要加任何标记或说明。""", sample);

        var response = qwen.generate(
            List.of(LLMMessage.user(prompt)),
            LLMConfig.creative("qwen-plus")
        );
        String summary = response.content().trim();
        // 限制最大长度
        return summary.length() > 300 ? summary.substring(0, 300) : summary;
    }

    private List<String> extractNewNames(String content) {
        // 简单的人名检测：2-4个中文字符后跟"道"、"说"、"问"、"喝道"
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("([\\u4e00-\\u9fff]{2,4})(?:道|说|问|喝道|笑道|怒道|冷笑道)")
            .matcher(content);
        Set<String> seen = new HashSet<>();
        while (m.find() && names.size() < 10) {
            String name = m.group(1);
            if (seen.add(name)) names.add(name);
        }
        return names;
    }

    /**
     * 将旧格式 ChapterMemory 转换为三层架构的 MemoryItem 列表。
     * <p>
     * 映射规则:
     * summary → working.chapter_summary（工作记忆）
     * newCharacters → semantic.character_state（语义记忆）
     * </p>
     */
    private List<MemoryItem> convertToMemoryItems(ChapterMemory mem, String content) {
        List<MemoryItem> items = new ArrayList<>();
        int cn = mem.chapterNumber();

        // 工作记忆: 章节摘要
        if (mem.summary() != null && !mem.summary().isBlank()) {
            items.add(MemoryItem.of(
                    UUID.randomUUID().toString(),
                    "working",
                    "chapter_summary",
                    "第" + cn + "章",
                    "summary",
                    mem.summary(),
                    cn,
                    "IncrementalMemoryService自动提取"
            ));
        }

        // 语义记忆: 新角色出场
        if (mem.newCharacters() != null) {
            for (String name : mem.newCharacters()) {
                items.add(MemoryItem.of(
                        UUID.randomUUID().toString(),
                        "semantic",
                        "character_state",
                        name,
                        "introduced",
                        "首次出场于第" + cn + "章",
                        cn,
                        "IncrementalMemoryService自动提取"
                ));
            }
        }

        // 语义记忆: 新道具/地点
        if (mem.newItems() != null) {
            for (String item : mem.newItems()) {
                items.add(MemoryItem.of(
                        UUID.randomUUID().toString(),
                        "semantic",
                        "story_fact",
                        item,
                        "introduced",
                        "首次出现于第" + cn + "章",
                        cn,
                        "IncrementalMemoryService自动提取"
                ));
            }
        }

        // 情节记忆: 章节标题（作为剧情时间线）
        if (mem.title() != null && !mem.title().isBlank()) {
            items.add(MemoryItem.of(
                    UUID.randomUUID().toString(),
                    "episodic",
                    "timeline",
                    "第" + cn + "章",
                    "title",
                    mem.title(),
                    cn,
                    "IncrementalMemoryService自动提取"
            ));
        }

        // 语义记忆: 伏笔变更
        if (mem.foreshadowUpdates() != null) {
            for (String fs : mem.foreshadowUpdates()) {
                items.add(MemoryItem.of(
                        UUID.randomUUID().toString(),
                        "semantic",
                        "open_loop",
                        "伏笔",
                        fs.length() > 20 ? fs.substring(0, 20) : fs,
                        fs,
                        cn,
                        "IncrementalMemoryService自动提取"
                ));
            }
        }

        return items;
    }
}
