package com.yunmo.api.controller;

import com.yunmo.domain.entity.*;
import com.yunmo.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * Dashboard 监控面板 API — 只读数据接口。
 *
 * @author yunmo
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/novels/{novelId}/monitor")
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    private final ChapterRepository chapterRepo;
    private final NovelRepository novelRepo;
    private final CharacterRepository characterRepo;
    private final ForeshadowTrackingRepository foreshadowRepo;
    private final CharacterStateRepository characterStateRepo;

    public MonitorController(ChapterRepository chapterRepo,
                             NovelRepository novelRepo,
                             CharacterRepository characterRepo,
                             ForeshadowTrackingRepository foreshadowRepo,
                             CharacterStateRepository characterStateRepo) {
        this.chapterRepo = chapterRepo;
        this.novelRepo = novelRepo;
        this.characterRepo = characterRepo;
        this.foreshadowRepo = foreshadowRepo;
        this.characterStateRepo = characterStateRepo;
    }

    /** 统计概览 */
    @GetMapping("/stats")
    public Mono<ResponseEntity<Map<String, Object>>> stats(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            log.info("[MonitorAPI] 请求统计概览 — novel={}", novelId);
            var novel = novelRepo.findById(novelId).orElse(null);
            var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
            var foreshadows = foreshadowRepo.findByNovelId(novelId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("novelId", novelId);
            result.put("title", novel != null ? novel.getTitle() : "");
            result.put("totalChapters", chapters.size());
            result.put("totalWords", chapters.stream().mapToInt(c -> c.getWordCount() != null ? c.getWordCount() : 0).sum());
            result.put("activeHooks", foreshadows.stream().filter(f -> "ACTIVATED".equals(f.getStatus().name()) || "PLANTED".equals(f.getStatus().name())).count());
            result.put("resolvedHooks", foreshadows.stream().filter(f -> "RESOLVED".equals(f.getStatus().name())).count());

            List<Map<String, Object>> trend = new ArrayList<>();
            for (var ch : chapters) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("chapterNumber", ch.getChapterNumber());
                point.put("wordCount", ch.getWordCount());
                point.put("title", ch.getTitle());
                trend.add(point);
            }
            result.put("chapterTrend", trend);
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 伏笔列表 */
    @GetMapping("/foreshadows")
    public Mono<ResponseEntity<List<Map<String, Object>>>> foreshadows(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            log.info("[MonitorAPI] 请求伏笔列表 — novel={}", novelId);
            var list = foreshadowRepo.findByNovelId(novelId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (var f : list) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("hookId", f.getHookId());
                item.put("content", f.getContent());
                item.put("plantedChapter", f.getPlantedChapter());
                item.put("expectedPayoffChapter", f.getExpectedPayoffChapter());
                item.put("status", f.getStatus().name());
                item.put("importance", f.getImportance().name());
                item.put("updatedAt", f.getUpdatedAt() != null ? f.getUpdatedAt().toString() : null);
                result.add(item);
            }
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 审计历史 */
    @GetMapping("/audits")
    public Mono<ResponseEntity<List<Map<String, Object>>>> auditHistory(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            log.info("[MonitorAPI] 请求审计历史 — novel={}", novelId);
            var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (var ch : chapters) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("chapterNumber", ch.getChapterNumber());
                item.put("title", ch.getTitle());
                item.put("status", ch.getStatus() != null ? ch.getStatus().name() : "OUTLINE");
                item.put("wordCount", ch.getWordCount());
                result.add(item);
            }
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 角色关系图数据 */
    @GetMapping("/characters/graph")
    public Mono<ResponseEntity<Map<String, Object>>> characterGraph(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            log.info("[MonitorAPI] 请求角色关系图 — novel={}", novelId);
            var characters = characterRepo.findByNovelIdAndIsDeadFalse(novelId);
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            for (var c : characters) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", c.getId());
                node.put("name", c.getName());
                node.put("role", c.getRole() != null ? c.getRole().name() : "SUPPORTING");
                node.put("importance", c.getImportance());

                var states = characterStateRepo.findTop1ByNovelIdAndCharacterIdOrderByChapterNumberDesc(novelId, c.getId());
                if (!states.isEmpty()) {
                    var latest = states.get(0);
                    node.put("currentState", latest.getEmotionalState());
                    node.put("location", latest.getLocation());
                    node.put("realm", latest.getRealm());
                }
                nodes.add(node);
            }

            Map<String, Object> graph = new LinkedHashMap<>();
            graph.put("nodes", nodes);
            graph.put("edges", edges);
            return ResponseEntity.ok(graph);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
