package com.yunmo.api.controller;

import com.yunmo.common.config.AppProperties;
import com.yunmo.domain.entity.Novel;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.NovelRepository;
import com.yunmo.service.NovelCascadeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * 小说 CRUD API
 */
@RestController
@RequestMapping("/api/v1/novels")
public class NovelController {

    private final NovelRepository novelRepo;
    private final ChapterRepository chapterRepo;
    private final AppProperties appProperties;
    private final NovelCascadeService cascadeService;

    public NovelController(NovelRepository novelRepo, ChapterRepository chapterRepo,
                           AppProperties appProperties, NovelCascadeService cascadeService) {
        this.novelRepo = novelRepo;
        this.chapterRepo = chapterRepo;
        this.appProperties = appProperties;
        this.cascadeService = cascadeService;
    }

    @GetMapping
    public Mono<List<Novel>> list() {
        return Mono.fromCallable(() -> {
            var novels = novelRepo.findByUserIdOrderByCreatedAtDesc(appProperties.getDefaultUserId());
            // 填充从章节计算的实际字数和章数
            for (var novel : novels) {
                var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novel.getId());
                novel.setTotalChapters(chapters.size());
                novel.setWordCount(chapters.stream().mapToInt(c ->
                    c.getWordCount() != null ? c.getWordCount() : 0).sum());
            }
            return novels;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Novel>> get(@PathVariable String id) {
        return Mono.fromCallable(() ->
                novelRepo.findById(id)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<Novel> create(@RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            Novel novel = new Novel();
            novel.setTitle((String) body.get("title"));
            novel.setGenreId((String) body.getOrDefault("genre_id", "xuanhuan"));
            novel.setSynopsis((String) body.getOrDefault("synopsis", ""));
            novel.setWritingStyle((String) body.getOrDefault("writing_style", ""));
            novel.setUserId(appProperties.getDefaultUserId());
            return novelRepo.save(novel);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Novel>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() ->
                novelRepo.findById(id)
                        .map(novel -> {
                            if (body.containsKey("title")) novel.setTitle((String) body.get("title"));
                            if (body.containsKey("synopsis")) novel.setSynopsis((String) body.get("synopsis"));
                            if (body.containsKey("writing_style")) novel.setWritingStyle((String) body.get("writing_style"));
                            if (body.containsKey("status")) {
                                try {
                                    novel.setStatus(com.yunmo.common.enums.ChapterStatus.valueOf(
                                            ((String) body.get("status")).toUpperCase()));
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("无效的小说状态: " + body.get("status"));
                                }
                            }
                            return ResponseEntity.ok(novelRepo.save(novel));
                        })
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<?>> delete(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            boolean deleted = cascadeService.cascadeDelete(id);
            if (deleted) {
                return ResponseEntity.ok(Map.<String, Object>of("deleted", true));
            }
            return ResponseEntity.notFound().build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
