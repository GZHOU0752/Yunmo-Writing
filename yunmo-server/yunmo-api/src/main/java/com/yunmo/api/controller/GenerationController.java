package com.yunmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.domain.repository.NovelRepository;
import com.yunmo.service.BatchGenerationService;
import com.yunmo.service.ChapterGenerationService;
import com.yunmo.service.CheckpointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 章节生成 API — SSE 流式端点 + 非流式端点
 */
@RestController
@RequestMapping("/api/v1/novels")
public class GenerationController {

    private static final Logger log = LoggerFactory.getLogger(GenerationController.class);
    private static final int MAX_CONCURRENT_GENERATIONS = 5;
    private final ChapterGenerationService generationService;
    private final BatchGenerationService batchGenerationService;
    private final NovelRepository novelRepo;
    private final CheckpointService checkpointService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 并发生成计数器（键: novelId，值: 当前活跃生成数） */
    private final ConcurrentHashMap<String, AtomicInteger> activeGenerations = new ConcurrentHashMap<>();

    public GenerationController(ChapterGenerationService generationService,
                                 BatchGenerationService batchGenerationService,
                                 NovelRepository novelRepo,
                                 CheckpointService checkpointService) {
        this.generationService = generationService;
        this.batchGenerationService = batchGenerationService;
        this.novelRepo = novelRepo;
        this.checkpointService = checkpointService;
    }

    /** 检查并占位一个并发槽位，返回 true 表示允许生成 */
    private boolean tryAcquireSlot(String novelId) {
        AtomicInteger counter = activeGenerations.computeIfAbsent(novelId, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();
        if (current > MAX_CONCURRENT_GENERATIONS) {
            counter.decrementAndGet();
            return false;
        }
        return true;
    }

    /** 释放一个并发槽位 */
    private void releaseSlot(String novelId) {
        AtomicInteger counter = activeGenerations.get(novelId);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }

    /**
     * SSE 流式生成章节
     * GET/POST /api/v1/novels/{novelId}/chapters/{chapterNumber}/generate
     *
     * 对标 Python: POST /{novel_id}/chapters/{chapter_number}/generate
     */
    @PostMapping("/{novelId}/chapters/{chapterNumber}/generate")
    @GetMapping("/{novelId}/chapters/{chapterNumber}/generate")
    public Flux<ServerSentEvent<String>> generateChapter(
            @PathVariable String novelId,
            @PathVariable int chapterNumber,
            @RequestParam(defaultValue = "") String focus,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        log.info("[SSE] 开始流式生成: novel={}, chapter={}, focus={}", novelId, chapterNumber, focus);

        // 并发槽位保护：防止恶意或无意的大量并行生成请求
        if (!tryAcquireSlot(novelId)) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .data("{\"phase\":\"error\",\"message\":\"该小说的并发生成请求过多，请等待当前生成完成\"}")
                            .event("error")
                            .build()
            );
        }

        // 从请求体或 query param 提取参数
        String userFocus = focus;
        if (body != null && body.containsKey("focus")) {
            userFocus = (String) body.get("focus");
        }

        String finalUserFocus = userFocus;

        // 从数据库查 Novel 的 genreId → 获取完整的 genreConfig（含 writing_blueprint 和 forbidden_terms）
        return Mono.fromCallable(() -> {
            var novel = novelRepo.findById(novelId).orElse(null);
            Map<String, Object> genreConfig = Collections.emptyMap();
            if (novel != null && novel.getGenreId() != null) {
                genreConfig = GenreController.GENRES.stream()
                        .filter(g -> g.get("id").equals(novel.getGenreId()))
                        .findFirst()
                        .map(g -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> config = (Map<String, Object>) g.get("genre_config");
                            return config != null ? config : Collections.<String, Object>emptyMap();
                        })
                        .orElse(Collections.emptyMap());
            }
            return genreConfig;
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(genreConfig ->
            generationService.generateStream(novelId, chapterNumber, genreConfig, finalUserFocus)
                .map(event -> {
                    try {
                        String ssePhase = "error".equals(event.phase())
                            ? "error" : mapPhase(event.stageName());
                        String dataJson = objectMapper.writeValueAsString(Map.of(
                                "phase", ssePhase,
                                "stage", event.stageName(),
                                "data", event.output() != null ? event.output().data() : Collections.emptyMap()
                        ));
                        return ServerSentEvent.<String>builder()
                                .data(dataJson)
                                .event(mapPhase(event.stageName()))
                                .build();
                    } catch (Exception e) {
                        // 使用 Jackson 序列化错误消息，避免 JSON 注入
                        String errorJson;
                        try {
                            errorJson = objectMapper.writeValueAsString(Map.of(
                                    "phase", "error",
                                    "message", "生成过程发生内部错误，请重试"
                            ));
                        } catch (Exception ex) {
                            errorJson = "{\"phase\":\"error\",\"message\":\"内部错误\"}";
                        }
                        return ServerSentEvent.<String>builder()
                                .data(errorJson)
                                .event("error")
                                .build();
                    }
                })
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .data("{\"phase\":\"done\"}")
                                .event("done")
                                .build()
                ))
                .doFinally(signalType -> releaseSlot(novelId))
        );
    }

    /**
     * 非流式生成 — 简单场景
     */
    @PostMapping("/{novelId}/chapters/{chapterNumber}/generate-simple")
    public Mono<Map<String, Object>> generateSimple(
            @PathVariable String novelId,
            @PathVariable int chapterNumber,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String userFocus = body != null ? (String) body.getOrDefault("focus", "") : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> genreConfig = body != null
                ? (Map<String, Object>) body.getOrDefault("genre_config", Collections.emptyMap())
                : Collections.emptyMap();

        log.info("[生成] 非流式生成请求 — novel={}, chapter={}, focus={}", novelId, chapterNumber,
                userFocus != null && !userFocus.isEmpty() ? userFocus.substring(0, Math.min(50, userFocus.length())) : "");
        return generationService.generate(novelId, chapterNumber, genreConfig, userFocus);
    }

    /**
     * 批量生成 — SSE流式推送进度
     * POST /api/v1/novels/{novelId}/chapters/batch-generate
     */
    @PostMapping("/{novelId}/chapters/batch-generate")
    public Flux<ServerSentEvent<String>> batchGenerate(
            @PathVariable String novelId,
            @RequestBody Map<String, Object> body
    ) {
        @SuppressWarnings("unchecked")
        List<Integer> chapterNumbers = (List<Integer>) body.getOrDefault("chapter_numbers", List.of());
        String userFocus = (String) body.getOrDefault("focus", "");
        String batchId = UUID.randomUUID().toString().substring(0, 8);

        if (chapterNumbers.isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder()
                .data("{\"error\":\"chapter_numbers不能为空\"}")
                .event("error")
                .build());
        }

        // 排序确保按章节顺序生成
        List<Integer> sorted = new ArrayList<>(chapterNumbers);
        Collections.sort(sorted);

        log.info("[BatchGen] 启动批量生成: novel={}, chapters={}, batch={}", novelId, sorted, batchId);

        // 加载 genreConfig
        return Mono.fromCallable(() -> {
            var novel = novelRepo.findById(novelId).orElse(null);
            Map<String, Object> genreConfig = Collections.emptyMap();
            if (novel != null && novel.getGenreId() != null) {
                genreConfig = GenreController.GENRES.stream()
                    .filter(g -> g.get("id").equals(novel.getGenreId()))
                    .findFirst()
                    .map(g -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> config = (Map<String, Object>) g.get("genre_config");
                        return config != null ? config : Collections.<String, Object>emptyMap();
                    })
                    .orElse(Collections.emptyMap());
            }
            return genreConfig;
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(genreConfig ->
            batchGenerationService.generateBatch(batchId, novelId, sorted, genreConfig, userFocus)
                .map(event -> {
                    try {
                        String json = objectMapper.writeValueAsString(Map.of(
                            "batch_id", event.batchId(),
                            "status", event.status(),
                            "current_chapter", event.currentChapter(),
                            "total_chapters", event.totalChapters(),
                            "chapter_title", event.chapterTitle(),
                            "word_count", event.wordCount(),
                            "total_words", event.totalWordsSoFar(),
                            "elapsed_ms", event.elapsedMs(),
                            "message", event.message()
                        ));
                        return ServerSentEvent.<String>builder()
                            .data(json)
                            .event(event.status())
                            .build();
                    } catch (Exception e) {
                        return ServerSentEvent.<String>builder()
                            .data("{\"status\":\"error\",\"message\":\"序列化失败\"}")
                            .event("error")
                            .build();
                    }
                })
                .concatWith(Mono.just(ServerSentEvent.<String>builder()
                    .data("{\"status\":\"stream_closed\",\"batch_id\":\"" + batchId + "\"}")
                    .event("done")
                    .build()))
        );
    }

    /** 取消批量生成 */
    @PostMapping("/{novelId}/chapters/batch-cancel/{batchId}")
    public Mono<Map<String, Object>> cancelBatch(
            @PathVariable String novelId,
            @PathVariable String batchId
    ) {
        boolean cancelled = batchGenerationService.cancelBatch(batchId);
        return Mono.just(Map.of(
            "batch_id", batchId,
            "cancelled", cancelled,
            "message", cancelled ? "已取消" : "批次不存在或已完成"
        ));
    }

    /** 清除断点 */
    @DeleteMapping("/{novelId}/chapters/{chapterNumber}/checkpoint")
    public Mono<Map<String, Object>> clearCheckpoint(@PathVariable String novelId,
                                                      @PathVariable int chapterNumber) {
        return Mono.fromCallable(() -> {
            checkpointService.clear(novelId, chapterNumber);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将阶段名称映射为 SSE phase
     */
    private String mapPhase(String stageName) {
        return switch (stageName) {
            case "assemble_context", "debate_outline" -> "preflight";
            case "preflight", "pleasure_beat" -> "preflight";
            case "checkpoint_found" -> "preflight";
            case "write_chapter" -> "writing";
            case "polish_chapter", "adversarial_edit" -> "review";
            case "decide_verdict" -> "deciding";
            case "save_chapter" -> "save";
            default -> stageName;
        };
    }
}
