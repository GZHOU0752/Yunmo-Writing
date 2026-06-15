package com.yunmo.api.controller;

import com.yunmo.domain.entity.OutlineNode;
import com.yunmo.service.outline.OutlineCompletionService;
import com.yunmo.service.outline.OutlineNodeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * 大纲层级 API
 */
@RestController
@RequestMapping("/api/v1/novels/{novelId}/outline")
public class OutlineController {

    private final OutlineNodeService outlineNodeService;
    private final OutlineCompletionService completionService;

    public OutlineController(OutlineNodeService outlineNodeService,
                              OutlineCompletionService completionService) {
        this.outlineNodeService = outlineNodeService;
        this.completionService = completionService;
    }

    /** 获取完整大纲树 */
    @GetMapping
    public Mono<List<Map<String, Object>>> getTree(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            List<OutlineNode> nodes = outlineNodeService.getTree(novelId);
            return nodes.stream().map(this::toMap).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 获取指定节点的子节点 */
    @GetMapping("/{parentId}/children")
    public Mono<List<Map<String, Object>>> getChildren(@PathVariable String novelId,
                                                        @PathVariable String parentId) {
        return Mono.fromCallable(() -> {
            String pid = "null".equals(parentId) ? null : parentId;
            List<OutlineNode> nodes = outlineNodeService.getChildren(novelId, pid);
            return nodes.stream().map(this::toMap).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 新增大纲节点 */
    @PostMapping
    public Mono<Map<String, Object>> create(@PathVariable String novelId,
                                             @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String parentId = (String) body.get("parentId");
            String title = (String) body.get("title");
            int level = ((Number) body.getOrDefault("level", 2)).intValue();
            String causalSentence = (String) body.get("causalSentence");
            String outlineContent = (String) body.get("outlineContent");
            Integer wordCountTarget = body.get("wordCountTarget") != null
                    ? ((Number) body.get("wordCountTarget")).intValue() : null;

            OutlineNode node = outlineNodeService.create(
                    novelId, parentId, title, level, causalSentence, outlineContent, wordCountTarget);
            return toMap(node);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 更新大纲节点 */
    @PutMapping("/{id}")
    public Mono<Map<String, Object>> update(@PathVariable String novelId,
                                             @PathVariable String id,
                                             @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String title = (String) body.get("title");
            String causalSentence = (String) body.get("causalSentence");
            String outlineContent = (String) body.get("outlineContent");
            Integer wordCountTarget = body.get("wordCountTarget") != null
                    ? ((Number) body.get("wordCountTarget")).intValue() : null;
            String status = (String) body.get("status");
            Integer chapterNumber = body.get("chapterNumber") != null
                    ? ((Number) body.get("chapterNumber")).intValue() : null;

            OutlineNode node = outlineNodeService.update(
                    id, title, causalSentence, outlineContent, wordCountTarget, status, chapterNumber);
            return toMap(node);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 删除节点及子孙 */
    @DeleteMapping("/{id}")
    public Mono<Map<String, Object>> delete(@PathVariable String novelId,
                                             @PathVariable String id) {
        return Mono.fromCallable(() -> {
            int count = outlineNodeService.deleteCascade(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deleted", count);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 批量更新排序 */
    @PutMapping("/reorder")
    public Mono<Void> reorder(@PathVariable String novelId,
                               @RequestBody List<Map<String, Object>> items) {
        return Mono.fromRunnable(() -> outlineNodeService.reorder(novelId, items))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** AI 自动补全下级大纲（SSE 流式） */
    @PostMapping(value = "/{id}/complete", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> complete(@PathVariable String novelId,
                                  @PathVariable String id,
                                  @RequestBody Map<String, Object> body) {
        int childLevel = ((Number) body.getOrDefault("childLevel", 2)).intValue();
        int count = ((Number) body.getOrDefault("count", 3)).intValue();
        return Flux.concat(
            // 先返回一个标记事件，告知前端开始
            Flux.just("event: start\ndata: {\"status\":\"generating\"}\n\n"),
            // 流式输出 JSON
            completionService.completeStream(novelId, id, childLevel, count)
                .map(chunk -> "data: " + chunk + "\n\n"),
            // 结束标记
            Flux.just("event: done\ndata: {}\n\n")
        );
    }

    /** 绑定大纲节点到章节 */
    @PutMapping("/{id}/bind-chapter")
    public Mono<Void> bindChapter(@PathVariable String novelId,
                                   @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
        int chapterNumber = ((Number) body.get("chapterNumber")).intValue();
        return Mono.fromRunnable(() -> outlineNodeService.bindChapter(id, chapterNumber))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Map<String, Object> toMap(OutlineNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", node.getId());
        m.put("novelId", node.getNovelId());
        m.put("parentId", node.getParentId());
        m.put("title", node.getTitle());
        m.put("level", node.getLevel());
        m.put("sequenceOrder", node.getSequenceOrder());
        m.put("causalSentence", node.getCausalSentence());
        m.put("outlineContent", node.getOutlineContent());
        m.put("structure", node.getStructure());
        m.put("wordCountTarget", node.getWordCountTarget());
        m.put("status", node.getStatus());
        m.put("chapterNumber", node.getChapterNumber());
        m.put("createdAt", node.getCreatedAt());
        return m;
    }
}
