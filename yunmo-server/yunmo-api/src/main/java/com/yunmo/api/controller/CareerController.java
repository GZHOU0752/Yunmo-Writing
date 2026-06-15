package com.yunmo.api.controller;

import com.yunmo.domain.entity.Career;
import com.yunmo.domain.repository.CareerRepository;
import com.yunmo.service.CareerService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/careers")
public class CareerController {

    private final CareerRepository repo;
    private final CareerService service;

    public CareerController(CareerRepository repo, CareerService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    public Mono<List<Career>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() -> repo.findByNovelId(novelId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/built-in")
    public Mono<Career> createBuiltIn(@PathVariable String novelId,
                                       @RequestBody Map<String, String> body) {
        return Mono.fromCallable(() ->
                service.createBuiltInCareer(novelId,
                        body.getOrDefault("genre_id", "xianxia"))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public Mono<Career> create(@PathVariable String novelId,
                                @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            Career c = new Career();
            c.setNovelId(novelId);
            c.setName((String) body.get("name"));
            c.setDescription((String) body.getOrDefault("description", ""));
            if (body.containsKey("stages") && body.get("stages") instanceof List<?> list) {
                c.setStages((List<Object>) list);
            }
            if (body.get("maxStage") instanceof Number n) c.setMaxStage(n.intValue());
            return repo.save(c);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    @PatchMapping("/{careerId}")
    public Mono<Career> update(@PathVariable String novelId,
                                @PathVariable String careerId,
                                @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            Career c = repo.findById(careerId).orElse(null);
            if (c == null || !c.getNovelId().equals(novelId)) return null;
            if (body.containsKey("name")) c.setName((String) body.get("name"));
            if (body.containsKey("description")) c.setDescription((String) body.get("description"));
            if (body.containsKey("stages") && body.get("stages") instanceof List<?> list) {
                c.setStages((List<Object>) list);
            }
            if (body.get("maxStage") instanceof Number n) c.setMaxStage(n.intValue());
            return repo.save(c);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{careerId}")
    public Mono<Void> delete(@PathVariable String novelId, @PathVariable String careerId) {
        return Mono.<Void>fromRunnable(() -> {
            repo.findById(careerId).ifPresent(c -> {
                if (c.getNovelId().equals(novelId)) repo.delete(c);
            });
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
