package com.yunmo.api.controller;

import com.yunmo.service.WritingStatsService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/stats")
public class WritingStatsController {

    private final WritingStatsService statsService;

    public WritingStatsController(WritingStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public Mono<Map<String, Object>> overview(@PathVariable String novelId) {
        return Mono.fromCallable(() -> statsService.getOverview(novelId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/history")
    public Mono<List<Map<String, Object>>> history(@PathVariable String novelId,
                                                    @RequestParam(defaultValue = "30") int days) {
        return Mono.fromCallable(() -> statsService.getHistory(novelId, Math.min(days, 365)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/target")
    public Mono<Void> setTarget(@PathVariable String novelId, @RequestBody Map<String, Object> body) {
        int target = ((Number) body.get("targetWordCount")).intValue();
        return Mono.fromRunnable(() -> statsService.setTarget(novelId, target))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }
}
