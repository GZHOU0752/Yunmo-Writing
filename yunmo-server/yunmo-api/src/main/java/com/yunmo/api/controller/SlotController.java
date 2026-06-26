package com.yunmo.api.controller;

import com.yunmo.service.slot.SlotInfo;
import com.yunmo.service.slot.SlotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Slot 多作品隔离 REST API。
 *
 * @author yunmo
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/slots")
public class SlotController {

    private static final Logger log = LoggerFactory.getLogger(SlotController.class);

    private final SlotManager slotManager;

    public SlotController(SlotManager slotManager) {
        this.slotManager = slotManager;
    }

    /** 列出所有槽位 */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listSlots() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SlotInfo s : slotManager.listSlots()) {
            result.add(toSlotMap(s));
        }
        log.debug("[SlotAPI] 列出所有槽位 — count={}", result.size());
        return ResponseEntity.ok(result);
    }

    /** 创建新槽位 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSlot(@RequestBody Map<String, Object> body) {
        try {
            String title = (String) body.getOrDefault("title", "未命名作品");
            log.info("[SlotAPI] 创建槽位请求 — title={}", title);
            SlotInfo slot = slotManager.createSlot(title);
            log.info("[SlotAPI] 槽位已创建 — id={}, title={}", slot.getId(), slot.getTitle());
            return ResponseEntity.ok(toSlotMap(slot));
        } catch (Exception e) {
            log.error("[SlotAPI] 创建槽位失败 — title={}, error={}", body.getOrDefault("title", "未知"), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 切换活跃槽位 */
    @PutMapping("/{slotId}/activate")
    public ResponseEntity<Map<String, Object>> activateSlot(@PathVariable String slotId) {
        try {
            log.info("[SlotAPI] 切换活跃槽位请求 — slotId={}", slotId);
            slotManager.switchSlot(slotId);
            SlotInfo active = slotManager.getActiveSlot();
            log.info("[SlotAPI] 槽位已切换 — activeSlot={}", active != null ? active.getId() : "null");
            return ResponseEntity.ok(toSlotMap(active));
        } catch (Exception e) {
            log.error("[SlotAPI] 切换槽位失败 — slotId={}, error={}", slotId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 安全删除（移到回收站） */
    @DeleteMapping("/{slotId}")
    public ResponseEntity<Map<String, Object>> deleteSlot(@PathVariable String slotId) {
        try {
            log.info("[SlotAPI] 删除槽位请求 — slotId={}", slotId);
            slotManager.deleteSlotSafe(slotId);
            log.info("[SlotAPI] 槽位已移至回收站 — slotId={}", slotId);
            return ResponseEntity.ok(Map.of("deleted", true, "message", "已移至回收站"));
        } catch (Exception e) {
            log.error("[SlotAPI] 删除槽位失败 — slotId={}, error={}", slotId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 从回收站恢复 */
    @PostMapping("/trash/{trashDir}/restore")
    public ResponseEntity<Map<String, Object>> restoreFromTrash(@PathVariable String trashDir) {
        try {
            log.info("[SlotAPI] 恢复槽位请求 — trashDir={}", trashDir);
            slotManager.restoreFromTrash(trashDir);
            log.info("[SlotAPI] 槽位已恢复 — trashDir={}", trashDir);
            return ResponseEntity.ok(Map.of("restored", true));
        } catch (Exception e) {
            log.error("[SlotAPI] 恢复槽位失败 — trashDir={}, error={}", trashDir, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 清空回收站 */
    @DeleteMapping("/trash")
    public ResponseEntity<Map<String, Object>> purgeTrash() {
        try {
            log.info("[SlotAPI] 清空回收站请求");
            slotManager.purgeTrash();
            log.info("[SlotAPI] 回收站已清空");
            return ResponseEntity.ok(Map.of("purged", true));
        } catch (Exception e) {
            log.error("[SlotAPI] 清空回收站失败 — error={}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 查看回收站 */
    @GetMapping("/trash")
    public ResponseEntity<List<Map<String, Object>>> listTrash() {
        List<Map<String, Object>> trashList = slotManager.listTrash();
        log.debug("[SlotAPI] 查看回收站 — count={}", trashList.size());
        return ResponseEntity.ok(trashList);
    }

    private Map<String, Object> toSlotMap(SlotInfo slot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", slot.getId());
        map.put("slug", slot.getSlug());
        map.put("title", slot.getTitle());
        map.put("dbPath", slot.getDbPath());
        map.put("workspacePath", slot.getWorkspacePath());
        map.put("chapterCount", slot.getChapterCount());
        map.put("totalWords", slot.getTotalWords());
        map.put("status", slot.getStatus().name());
        map.put("createdAt", slot.getCreatedAt() != null ? slot.getCreatedAt().toString() : null);
        map.put("lastAccessedAt", slot.getLastAccessedAt() != null ? slot.getLastAccessedAt().toString() : null);
        return map;
    }
}
