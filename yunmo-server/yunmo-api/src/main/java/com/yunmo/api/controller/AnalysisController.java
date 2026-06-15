package com.yunmo.api.controller;

import com.yunmo.domain.entity.AnalysisReport;
import com.yunmo.domain.repository.AnalysisReportRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/analysis")
public class AnalysisController {

    private final AnalysisReportRepository repo;

    public AnalysisController(AnalysisReportRepository repo) { this.repo = repo; }

    @GetMapping
    public Mono<List<AnalysisReport>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() ->
                repo.findByNovelIdOrderByCreatedAtDesc(novelId)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/chapters/{chapterId}")
    public Mono<AnalysisReport> getByChapter(
            @PathVariable String novelId, @PathVariable String chapterId) {
        return Mono.fromCallable(() ->
                repo.findByChapterId(chapterId).orElse(null)
        ).subscribeOn(Schedulers.boundedElastic());
    }
}
