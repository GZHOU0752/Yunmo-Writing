package com.yunmo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 批量章节生成服务 — 逐章生成保持连续性，SSE推送进度。
 *
 * 参考: Novel-Claude 的 Batch API 离线生成模式
 */
@Service
public class BatchGenerationService {

    private static final Logger log = LoggerFactory.getLogger(BatchGenerationService.class);
    private final ChapterGenerationService generationService;

    /** 活跃的批量任务，key = batchId */
    private final ConcurrentHashMap<String, BatchJob> activeJobs = new ConcurrentHashMap<>();

    public BatchGenerationService(ChapterGenerationService generationService) {
        this.generationService = generationService;
    }

    /**
     * 批量生成进度事件
     */
    public record BatchProgressEvent(
        String batchId,
        String status,       // "started" | "chapter_done" | "chapter_error" | "completed" | "cancelled"
        int currentChapter,
        int totalChapters,
        String chapterTitle,
        int wordCount,
        int totalWordsSoFar,
        long elapsedMs,
        String message
    ) {}

    private static class BatchJob {
        final String novelId;
        final List<Integer> chapterNumbers;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final long startedAt = System.currentTimeMillis();

        BatchJob(String novelId, List<Integer> chapterNumbers) {
            this.novelId = novelId;
            this.chapterNumbers = chapterNumbers;
        }

        void cancel() { cancelled.set(true); }
        boolean isCancelled() { return cancelled.get(); }
    }

    /**
     * 启动批量生成，返回 SSE Flux
     *
     * @param batchId 批次ID（客户端生成，用于取消）
     * @param novelId 小说ID
     * @param chapterNumbers 要生成的章节号列表
     * @param genreConfig 类型配置
     * @param userFocus 用户指示（批量共用）
     */
    public Flux<BatchProgressEvent> generateBatch(
            String batchId,
            String novelId,
            List<Integer> chapterNumbers,
            Map<String, Object> genreConfig,
            String userFocus
    ) {
        if (activeJobs.containsKey(batchId)) {
            return Flux.error(new IllegalStateException("批次 " + batchId + " 已在运行中"));
        }

        BatchJob job = new BatchJob(novelId, chapterNumbers);
        activeJobs.put(batchId, job);

        int totalChapters = chapterNumbers.size();

        return Flux.fromStream(chapterNumbers.stream())
            .concatMap(chapterNum -> {
                if (job.isCancelled()) {
                    return Mono.just(new BatchProgressEvent(
                        batchId, "cancelled", chapterNum, totalChapters,
                        "", 0, 0,
                        System.currentTimeMillis() - job.startedAt,
                        "用户取消了批量生成"
                    ));
                }

                return Mono.fromCallable(() -> {
                    long chapterStart = System.currentTimeMillis();
                    log.info("[BatchGen] 开始生成第{}章 (batch={}, {}/{})",
                            chapterNum, batchId, chapterNumbers.indexOf(chapterNum) + 1, totalChapters);

                    try {
                        Map<String, Object> result = generationService.generate(
                            novelId, chapterNum, genreConfig, userFocus
                        ).block(Duration.ofMinutes(5)); // 每章最多5分钟

                        if (result == null) {
                            throw new RuntimeException("章节生成返回空结果");
                        }

                        String content = (String) result.get("content");
                        int wordCount = content != null ? countChineseChars(content) : 0;
                        long elapsed = System.currentTimeMillis() - chapterStart;

                        log.info("[BatchGen] 第{}章完成: {}字, 耗时{}ms (batch={})",
                                chapterNum, wordCount, elapsed, batchId);

                        return new BatchProgressEvent(
                            batchId, "chapter_done", chapterNum, totalChapters,
                            "第" + chapterNum + "章", wordCount, 0,
                            System.currentTimeMillis() - job.startedAt,
                            "第" + chapterNum + "章生成完成 (" + wordCount + "字)"
                        );
                    } catch (Exception e) {
                        // 清除超时等异常留下的中断标志，避免污染线程池
                        if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                            Thread.interrupted();
                        }
                        log.error("[BatchGen] 第{}章生成失败 (batch={}): {}", chapterNum, batchId, e.getMessage());
                        return new BatchProgressEvent(
                            batchId, "chapter_error", chapterNum, totalChapters,
                            "第" + chapterNum + "章", 0, 0,
                            System.currentTimeMillis() - job.startedAt,
                            "第" + chapterNum + "章生成失败: " + e.getMessage()
                        );
                    }
                }).subscribeOn(Schedulers.boundedElastic());
            })
            .concatWith(Mono.fromCallable(() -> {
                activeJobs.remove(batchId);
                long totalMs = System.currentTimeMillis() - job.startedAt;
                log.info("[BatchGen] 批量生成完成: batch={}, chapters={}, totalMs={}",
                        batchId, totalChapters, totalMs);
                return new BatchProgressEvent(
                    batchId, "completed", chapterNumbers.get(chapterNumbers.size() - 1),
                    totalChapters, "", 0, 0, totalMs,
                    "全部" + totalChapters + "章生成完成，总耗时" + (totalMs / 1000) + "秒"
                );
            }).subscribeOn(Schedulers.boundedElastic()))
            .doOnCancel(() -> {
                job.cancel();
                activeJobs.remove(batchId);
                log.info("[BatchGen] 批量生成被取消: batch={}", batchId);
            });
    }

    /** 取消指定批次 */
    public boolean cancelBatch(String batchId) {
        BatchJob job = activeJobs.get(batchId);
        if (job != null) {
            job.cancel();
            activeJobs.remove(batchId);
            log.info("[BatchGen] 批次已取消: {}", batchId);
            return true;
        }
        return false;
    }

    /** 获取活跃批次列表 */
    public Set<String> getActiveBatches() {
        return activeJobs.keySet();
    }

    private int countChineseChars(String text) {
        if (text == null) return 0;
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                count++;
            }
        }
        return Math.max(count, text.replaceAll("\\s+", "").length() / 2);
    }
}
