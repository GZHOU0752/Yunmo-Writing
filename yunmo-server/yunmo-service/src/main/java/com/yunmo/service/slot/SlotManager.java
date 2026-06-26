package com.yunmo.service.slot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 槽位管理器 — 多小说工作区隔离。
 *
 * <h3>工作区布局</h3>
 * <pre>
 * workspace/
 *   registry.json           # 注册表
 *   slot_001/               # 小说1
 *     project.json, chapters/, outlines/, reports/, backups/
 *   slot_002/               # 小说2
 *   _trash/                 # 回收站(30天保留)
 * </pre>
 *
 * @author yunmo
 * @since 2.0
 */
@Service
public class SlotManager {

    private static final Logger log = LoggerFactory.getLogger(SlotManager.class);
    private static final String REGISTRY_FILE = "registry.json";
    private static final String TRASH_DIR = "_trash";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path workspaceRoot;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, SlotInfo> cache = new ConcurrentHashMap<>();

    public SlotManager() {
        // 默认工作区路径（可通过环境变量 WORKSPACE_PATH 覆盖）
        String wsPath = System.getenv().getOrDefault("WORKSPACE_PATH", "./workspace");
        this.workspaceRoot = Path.of(wsPath);
        initWorkspace();
    }

    // ==================== 公共 API ====================

    /** 创建新槽位 */
    public synchronized SlotInfo createSlot(String title) {
        String slug = deriveSlug(title);
        String slotId = findNextSlotId(slug);
        Path slotPath = workspaceRoot.resolve(slotId);

        try {
            Files.createDirectories(slotPath);
            Files.createDirectories(slotPath.resolve("chapters"));
            Files.createDirectories(slotPath.resolve("outlines"));
            Files.createDirectories(slotPath.resolve("reports"));
            Files.createDirectories(slotPath.resolve("backups"));

            SlotInfo slot = new SlotInfo(slotId, slug, title, slotPath.toString());
            slot.setDbPath(slotPath.resolve("novel.db").toString());
            slot.setStatus(SlotStatus.ACTIVE);

            // 写project.json
            Map<String, Object> project = new LinkedHashMap<>();
            project.put("slotId", slotId);
            project.put("title", title);
            project.put("slug", slug);
            project.put("createdAt", slot.getCreatedAt().toString());
            mapper.writeValue(slotPath.resolve("project.json").toFile(), project);

            // 更新注册表
            deactivateOthers(slotId);
            cache.put(slotId, slot);
            saveRegistry();

            log.info("[Slot] 创建槽位: id={}, title={}", slotId, title);
            return slot;
        } catch (IOException e) {
            throw new RuntimeException("创建槽位失败: " + title, e);
        }
    }

    /** 获取当前活跃槽位 */
    public SlotInfo getActiveSlot() {
        loadRegistry();
        return cache.values().stream()
            .filter(s -> s.getStatus() == SlotStatus.ACTIVE)
            .findFirst().orElse(null);
    }

    /** 切换活跃槽位 */
    public synchronized void switchSlot(String slotId) {
        SlotInfo slot = cache.get(slotId);
        if (slot == null) throw new IllegalArgumentException("槽位不存在: " + slotId);
        deactivateOthers(slotId);
        slot.setStatus(SlotStatus.ACTIVE);
        slot.setLastAccessedAt(LocalDateTime.now());
        saveRegistry();
        log.info("[Slot] 切换活跃槽位: {}", slotId);
    }

    /** 列出所有槽位 */
    public List<SlotInfo> listSlots() {
        loadRegistry();
        return new ArrayList<>(cache.values());
    }

    /** 安全删除（移到回收站） */
    public synchronized void deleteSlotSafe(String slotId) {
        SlotInfo slot = cache.get(slotId);
        if (slot == null) throw new IllegalArgumentException("槽位不存在: " + slotId);
        if (slot.getStatus() == SlotStatus.ACTIVE) throw new IllegalStateException("不能删除活跃槽位，请先切换");

        try {
            Path trashPath = workspaceRoot.resolve(TRASH_DIR)
                .resolve(LocalDateTime.now().format(TS_FMT) + "_" + slotId);
            Files.createDirectories(trashPath.getParent());
            Files.move(Path.of(slot.getWorkspacePath()), trashPath, StandardCopyOption.ATOMIC_MOVE);

            // 记录恢复元数据
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("originalId", slotId);
            meta.put("title", slot.getTitle());
            meta.put("deletedAt", LocalDateTime.now().toString());
            mapper.writeValue(trashPath.resolve(".trash_meta.json").toFile(), meta);

            cache.remove(slotId);
            saveRegistry();
            log.info("[Slot] 安全删除: {} → {}", slotId, trashPath);
        } catch (IOException e) {
            throw new RuntimeException("删除槽位失败: " + slotId, e);
        }
    }

    /** 从回收站恢复 */
    public synchronized void restoreFromTrash(String trashDirName) {
        Path trashPath = workspaceRoot.resolve(TRASH_DIR).resolve(trashDirName);
        if (!Files.exists(trashPath)) throw new IllegalArgumentException("回收站中不存在: " + trashDirName);

        try {
            Map<?, ?> meta = mapper.readValue(trashPath.resolve(".trash_meta.json").toFile(), Map.class);
            String originalId = (String) meta.get("originalId");
            String title = (String) meta.get("title");
            Path restorePath = workspaceRoot.resolve(originalId);
            Files.move(trashPath, restorePath, StandardCopyOption.ATOMIC_MOVE);

            SlotInfo slot = new SlotInfo(originalId, deriveSlug(title), title, restorePath.toString());
            slot.setStatus(SlotStatus.IDLE);
            cache.put(originalId, slot);
            saveRegistry();
            log.info("[Slot] 恢复槽位: {} ← {}", originalId, trashDirName);
        } catch (IOException e) {
            throw new RuntimeException("恢复槽位失败: " + trashDirName, e);
        }
    }

    /** 清空回收站 */
    public void purgeTrash() {
        Path trashRoot = workspaceRoot.resolve(TRASH_DIR);
        if (!Files.exists(trashRoot)) return;
        try {
            try (var stream = Files.walk(trashRoot)) {
                stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            log.info("[Slot] 回收站已清空");
        } catch (IOException e) {
            throw new RuntimeException("清空回收站失败", e);
        }
    }

    /** 查看回收站 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listTrash() {
        Path trashRoot = workspaceRoot.resolve(TRASH_DIR);
        if (!Files.exists(trashRoot)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        try (var dirs = Files.list(trashRoot)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    Path metaFile = dir.resolve(".trash_meta.json");
                    if (Files.exists(metaFile)) {
                        Map<String, Object> meta = (Map<String, Object>) mapper.readValue(metaFile.toFile(), Map.class);
                        result.add(meta);
                        return;
                    }
                } catch (IOException ignored) {}
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("dir", dir.getFileName().toString());
                result.add(fallback);
            });
        } catch (IOException e) {
            return List.of();
        }
        return result;
    }

    // ==================== 内部方法 ====================

    private void initWorkspace() {
        try {
            Files.createDirectories(workspaceRoot);
            Files.createDirectories(workspaceRoot.resolve(TRASH_DIR));
            loadRegistry();
        } catch (IOException e) {
            throw new RuntimeException("初始化工作区失败: " + workspaceRoot, e);
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadRegistry() {
        Path regFile = workspaceRoot.resolve(REGISTRY_FILE);
        if (!Files.exists(regFile)) return;
        try {
            Map<String, Object> data = mapper.readValue(regFile.toFile(), Map.class);
            List<Map<String, Object>> slots = (List<Map<String, Object>>) data.getOrDefault("slots", List.of());
            Map<String, SlotInfo> loaded = new LinkedHashMap<>();
            for (var s : slots) {
                SlotInfo slot = new SlotInfo();
                slot.setId((String) s.get("id"));
                slot.setSlug((String) s.get("slug"));
                slot.setTitle((String) s.get("title"));
                slot.setWorkspacePath((String) s.get("workspacePath"));
                slot.setDbPath((String) s.get("dbPath"));
                slot.setChapterCount(s.get("chapterCount") instanceof Number n ? n.intValue() : 0);
                slot.setTotalWords(s.get("totalWords") instanceof Number n ? n.intValue() : 0);
                slot.setStatus(SlotStatus.valueOf((String) s.getOrDefault("status", "IDLE")));
                loaded.put(slot.getId(), slot);
            }
            cache.clear();
            cache.putAll(loaded);
        } catch (IOException e) {
            log.warn("[Slot] 注册表读取失败: {}", e.getMessage());
        }
    }

    private void saveRegistry() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeSlot", cache.values().stream()
            .filter(s -> s.getStatus() == SlotStatus.ACTIVE)
            .map(SlotInfo::getId).findFirst().orElse(null));
        List<Map<String, Object>> slots = new ArrayList<>();
        for (SlotInfo s : cache.values()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("slug", s.getSlug());
            sm.put("title", s.getTitle());
            sm.put("workspacePath", s.getWorkspacePath());
            sm.put("dbPath", s.getDbPath());
            sm.put("chapterCount", s.getChapterCount());
            sm.put("totalWords", s.getTotalWords());
            sm.put("status", s.getStatus().name());
            slots.add(sm);
        }
        data.put("slots", slots);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(workspaceRoot.resolve(REGISTRY_FILE).toFile(), data);
        } catch (IOException e) {
            log.error("[Slot] 注册表写入失败", e);
        }
    }

    private void deactivateOthers(String activeSlotId) {
        cache.values().forEach(s -> {
            if (!s.getId().equals(activeSlotId) && s.getStatus() == SlotStatus.ACTIVE) {
                s.setStatus(SlotStatus.IDLE);
            }
        });
    }

    /** 中文标题→拼音slug（简化版：取前20字符作标识） */
    private String deriveSlug(String title) {
        if (title == null || title.isBlank()) return "untitled";
        // 简化为取前20个字符，过滤非字母数字中文
        String cleaned = title.trim().replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
    }

    /** 查找下一个可用槽位ID */
    private String findNextSlotId(String slug) {
        int idx = 1;
        String candidate;
        do {
            candidate = "slot_" + String.format("%03d", idx);
            idx++;
        } while (cache.containsKey(candidate)
            || Files.exists(workspaceRoot.resolve(candidate)));
        return candidate;
    }
}
