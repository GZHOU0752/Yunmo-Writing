package com.yunmo.api.controller;

import com.yunmo.common.enums.WorldElementType;
import com.yunmo.domain.entity.WorldElement;
import com.yunmo.domain.repository.WorldElementRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/world")
public class WorldController {

    private final WorldElementRepository repo;

    public WorldController(WorldElementRepository repo) { this.repo = repo; }

    @GetMapping
    public Mono<List<WorldElement>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() -> repo.findByNovelId(novelId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<WorldElement> create(@PathVariable String novelId, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            WorldElement w = new WorldElement();
            w.setNovelId(novelId);
            w.setName((String) body.get("name"));
            w.setElementType(WorldElementType.valueOf(
                    ((String) body.getOrDefault("element_type", "OTHER")).toUpperCase()));
            w.setDescription((String) body.getOrDefault("description", ""));
            return repo.save(w);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<WorldElement>> update(
            @PathVariable String novelId, @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> repo.findById(id)
                .filter(w -> w.getNovelId().equals(novelId))
                .map(w -> {
                    if (body.containsKey("name")) w.setName((String) body.get("name"));
                    if (body.containsKey("description")) w.setDescription((String) body.get("description"));
                    return ResponseEntity.ok(repo.save(w));
                }).orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String novelId, @PathVariable String id) {
        return Mono.fromCallable(() -> {
            if (repo.findById(id).filter(w -> w.getNovelId().equals(novelId)).isPresent()) {
                repo.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }
            return ResponseEntity.notFound().<Void>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
