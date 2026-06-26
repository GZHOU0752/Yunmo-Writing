package com.yunmo.api.controller;

import com.yunmo.domain.entity.ReferenceMaterial;
import com.yunmo.service.rag.ContextEnrichmentService;
import com.yunmo.service.rag.ReferenceMaterialService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * 参考素材管理 API — 上传 txt 文件作为 AI 写作参考
 */
@RestController
@RequestMapping("/api/v1/novels/{novelId}/references")
public class ReferenceMaterialController {

    private final ReferenceMaterialService materialService;
    private final ContextEnrichmentService enrichmentService;

    public ReferenceMaterialController(ReferenceMaterialService materialService,
                                        ContextEnrichmentService enrichmentService) {
        this.materialService = materialService;
        this.enrichmentService = enrichmentService;
    }

    /** 列出小说的所有参考素材 */
    @GetMapping
    public Mono<List<Map<String, Object>>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            List<ReferenceMaterial> materials = materialService.list(novelId);
            return materials.stream().map(m -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", m.getId());
                item.put("fileName", m.getFileName());
                item.put("fileSize", m.getFileSize());
                item.put("chunkCount", m.getChunkCount());
                item.put("status", m.getStatus());
                item.put("createdAt", m.getCreatedAt());
                item.put("triggerMode", m.getTriggerMode() != null ? m.getTriggerMode() : "MANUAL");
                item.put("triggerKeywords", m.getTriggerKeywords());
                item.put("cooldownChapters", m.getCooldownChapters());
                item.put("priority", m.getPriority());
                return item;
            }).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 上传 txt 素材 */
    @PostMapping
    public Mono<Map<String, Object>> upload(@PathVariable String novelId,
                                             @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String fileName = (String) body.get("fileName");
            String content = (String) body.get("content");
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("文件内容不能为空");
            }
            ReferenceMaterial material = materialService.upload(novelId, fileName, content);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", material.getId());
            result.put("fileName", material.getFileName());
            result.put("chunkCount", material.getChunkCount());
            result.put("status", material.getStatus());
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 删除素材 */
    @DeleteMapping("/{materialId}")
    public Mono<Void> delete(@PathVariable String novelId,
                              @PathVariable String materialId) {
        return Mono.fromRunnable(() -> materialService.delete(materialId))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** 更新素材触发配置 */
    @PatchMapping("/{materialId}")
    public Mono<Map<String, Object>> updateTrigger(@PathVariable String novelId,
                                                    @PathVariable String materialId,
                                                    @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String triggerMode = (String) body.get("triggerMode");
            String triggerKeywords = (String) body.get("triggerKeywords");
            Integer cooldownChapters = body.containsKey("cooldownChapters")
                ? ((Number) body.get("cooldownChapters")).intValue() : null;
            Integer priority = body.containsKey("priority")
                ? ((Number) body.get("priority")).intValue() : null;
            ReferenceMaterial m = materialService.updateTriggerConfig(
                materialId, triggerMode, triggerKeywords, cooldownChapters, priority);
            Map<String, Object> result = new LinkedHashMap<>();
            if (m != null) {
                result.put("id", m.getId());
                result.put("triggerMode", m.getTriggerMode());
                result.put("triggerKeywords", m.getTriggerKeywords());
                result.put("cooldownChapters", m.getCooldownChapters());
                result.put("priority", m.getPriority());
            }
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 检查小说是否有参考素材 */
    @GetMapping("/status")
    public Mono<Map<String, Object>> status(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            long count = materialService.count(novelId);
            boolean hasVectors = enrichmentService.hasReference(novelId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", count);
            result.put("hasVectors", hasVectors);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
