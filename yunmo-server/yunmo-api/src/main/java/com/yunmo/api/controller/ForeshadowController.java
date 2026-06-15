package com.yunmo.api.controller;

import com.yunmo.domain.entity.Foreshadow;
import com.yunmo.domain.repository.ForeshadowRepository;
import com.yunmo.service.ForeshadowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/foreshadows")
public class ForeshadowController {

    private final ForeshadowRepository repo;
    private final ForeshadowService service;

    public ForeshadowController(ForeshadowRepository repo, ForeshadowService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    public Mono<List<Foreshadow>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() -> repo.findByNovelId(novelId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/reminders")
    public Mono<Map<String, List<Foreshadow>>> reminders(
            @PathVariable String novelId,
            @RequestParam(defaultValue = "0") int currentChapter) {
        return Mono.fromCallable(() -> service.getReminders(novelId, currentChapter))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<Foreshadow> create(@PathVariable String novelId, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            Foreshadow f = new Foreshadow();
            f.setNovelId(novelId);
            f.setTitle((String) body.get("title"));
            f.setContent((String) body.getOrDefault("content", ""));
            f.setKeywords((String) body.getOrDefault("keywords", ""));
            f.setUrgency(body.containsKey("urgency")
                    ? ((Number) body.get("urgency")).intValue() : 5);
            f.setStableId(service.generateStableId(f.getTitle()));
            return repo.save(f);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Foreshadow>> update(
            @PathVariable String novelId, @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> repo.findById(id)
                .filter(f -> f.getNovelId().equals(novelId))
                .map(f -> {
                    if (body.containsKey("status"))
                        f.setStatus(com.yunmo.common.enums.ForeshadowStatus.valueOf(
                                ((String) body.get("status")).toUpperCase()));
                    if (body.containsKey("resolved_chapter"))
                        f.setResolvedChapter(((Number) body.get("resolved_chapter")).intValue());
                    return ResponseEntity.ok(repo.save(f));
                }).orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String novelId, @PathVariable String id) {
        return Mono.fromCallable(() -> {
            if (repo.findById(id).filter(f -> f.getNovelId().equals(novelId)).isPresent()) {
                repo.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }
            return ResponseEntity.notFound().<Void>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
