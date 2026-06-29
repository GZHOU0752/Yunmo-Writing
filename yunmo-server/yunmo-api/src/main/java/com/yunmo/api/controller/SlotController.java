package com.yunmo.api.controller;

import com.yunmo.service.slot.SlotInfo;
import com.yunmo.service.slot.SlotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    public Mono<ResponseEntity<List<Map<String, Object>>>> listSlots() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (SlotInfo s : slotManager.listSlots()) {
                result.add(toSlotMap(s));
            }
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 创建新槽位 */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createSlot(@RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String title = (String) body.getOrDefault("title", "未命名作品");
            SlotInfo slot = slotManager.createSlot(title);
            return ResponseEntity.ok(toSlotMap(slot));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 切换活跃槽位 */
    @PutMapping("/{slotId}/activate")
    public Mono<ResponseEntity<Map<String, Object>>> activateSlot(@PathVariable String slotId) {
        return Mono.fromCallable(() -> {
            slotManager.switchSlot(slotId);
            SlotInfo active = slotManager.getActiveSlot();
            if (active == null) {
                return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "槽位切换失败"));
            }
            return ResponseEntity.ok(toSlotMap(active));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 安全删除（移到回收站） */
    @DeleteMapping("/{slotId}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteSlot(@PathVariable String slotId) {
        return Mono.fromCallable(() -> {
            slotManager.deleteSlotSafe(slotId);
            return ResponseEntity.ok(Map.<String, Object>of("deleted", true, "message", "已移至回收站"));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 从回收站恢复 */
    @PostMapping("/trash/{trashDir}/restore")
    public Mono<ResponseEntity<Map<String, Object>>> restoreFromTrash(@PathVariable String trashDir) {
        return Mono.fromCallable(() -> {
            slotManager.restoreFromTrash(trashDir);
            return ResponseEntity.ok(Map.<String, Object>of("restored", true));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 清空回收站 */
    @DeleteMapping("/trash")
    public Mono<ResponseEntity<Map<String, Object>>> purgeTrash() {
        return Mono.fromCallable(() -> {
            slotManager.purgeTrash();
            return ResponseEntity.ok(Map.<String, Object>of("purged", true));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 查看回收站 */
    @GetMapping("/trash")
    public Mono<ResponseEntity<List<Map<String, Object>>>> listTrash() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> trashList = slotManager.listTrash();
            return ResponseEntity.ok(trashList);
        }).subscribeOn(Schedulers.boundedElastic());
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
