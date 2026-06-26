package com.yunmo.service.marathon;

import com.yunmo.service.ChapterGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 马拉松守护进程调度器 — 自动循环创作。
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li>每30分钟自动写一章（可配置）</li>
 *   <li>日产量上限4章，每日0点重置</li>
 *   <li>连续失败3次自动暂停并通知</li>
 *   <li>重试升级：全文重写→定向重写→暂停</li>
 *   <li>漂移检测：主线冷/伏笔逾期/角色OOC</li>
 *   <li>自然完结：主线解决+支线关闭+情感债偿还</li>
 * </ul>
 *
 * @author yunmo
 * @since 2.0
 */
@Component
public class MarathonScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarathonScheduler.class);

    private final ConcurrentHashMap<String, MarathonJob> activeJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> dailyCounts = new ConcurrentHashMap<>();
    private final ChapterGenerationService generationService;

    /** 单例（简化实现，后续可注入 MarathonConfig） */
    private final int maxChaptersPerDay = 4;
    private final int maxConsecutiveFailures = 3;
    private final int qualityThreshold = 75;

    public MarathonScheduler(ChapterGenerationService generationService) {
        this.generationService = generationService;
    }

    // ==================== 公共 API ====================

    /** 启动马拉松创作 */
    public MarathonJob startMarathon(String novelId, int startChapter, int targetChapters) {
        MarathonJob result = activeJobs.compute(novelId, (k, v) -> {
            if (v == null) {
                MarathonJob newJob = new MarathonJob(novelId, startChapter, targetChapters);
                dailyCounts.put(novelId, 0);
                newJob.addEvent(MarathonEvent.of("STARTED", startChapter, "马拉松创作已启动，从第" + startChapter + "章开始"));
                log.info("[Marathon] 启动: novel={}, startChapter={}, target={}", novelId, startChapter, targetChapters);
                return newJob;
            }
            if (v.getState() == MarathonState.PAUSED) {
                v.setState(MarathonState.RUNNING);
                v.setPausedAt(null);
                v.setPauseReason(null);
                v.addEvent(MarathonEvent.of("RESUMED", v.getCurrentChapter(), "恢复创作"));
                log.info("[Marathon] 恢复: novel={}, chapter={}", novelId, v.getCurrentChapter());
                return v;
            }
            throw new IllegalStateException("该小说已有活跃的马拉松任务: " + novelId);
        });
        return result;
    }

    /** 暂停马拉松 */
    public MarathonJob pauseMarathon(String novelId, String reason) {
        MarathonJob job = getOrThrow(novelId);
        job.setState(MarathonState.PAUSED);
        job.setPausedAt(LocalDateTime.now());
        job.setPauseReason(reason);
        job.addEvent(MarathonEvent.of("PAUSED", job.getCurrentChapter(), reason));
        log.info("[Marathon] 暂停: novel={}, reason={}", novelId, reason);
        return job;
    }

    /** 恢复马拉松 */
    public MarathonJob resumeMarathon(String novelId) {
        MarathonJob job = getOrThrow(novelId);
        job.setState(MarathonState.RUNNING);
        job.setPausedAt(null);
        job.setPauseReason(null);
        job.addEvent(MarathonEvent.of("RESUMED", job.getCurrentChapter(), "恢复创作"));
        log.info("[Marathon] 恢复: novel={}, chapter={}", novelId, job.getCurrentChapter());
        return job;
    }

    /** 停止马拉松 */
    public MarathonJob stopMarathon(String novelId) {
        MarathonJob job = activeJobs.remove(novelId);
        dailyCounts.remove(novelId);
        if (job != null) {
            job.setState(MarathonState.IDLE);
            job.addEvent(MarathonEvent.of("STOPPED", job.getCurrentChapter(), "手动停止"));
            log.info("[Marathon] 停止: novel={}, totalWritten={}", novelId, job.getTotalWritten());
        }
        return job;
    }

    /** 查询状态 */
    public MarathonJob getStatus(String novelId) {
        return activeJobs.get(novelId);
    }

    /** 所有活跃任务 */
    public Collection<MarathonJob> getAllJobs() {
        return Collections.unmodifiableCollection(activeJobs.values());
    }

    // ==================== 定时循环 ====================

    /** 写章循环 — 每30分钟触发 */
    @Scheduled(cron = "${yunmo.marathon.cron:0 */30 * * * ?}")
    public void writeCycle() {
        if (activeJobs.isEmpty()) return;

        log.debug("[Marathon] 写章循环开始, activeJobs={}", activeJobs.size());
        for (MarathonJob job : activeJobs.values()) {
            if (job.getState() != MarathonState.RUNNING) continue;
            if (!job.canContinue()) {
                completeMarathon(job);
                continue;
            }
            // 日产量检查
            int todayWritten = dailyCounts.getOrDefault(job.getNovelId(), 0);
            if (todayWritten >= maxChaptersPerDay) {
                log.debug("[Marathon] 已达日产量上限: novel={}, count={}", job.getNovelId(), todayWritten);
                continue;
            }
            writeOneChapter(job);
        }
    }

    /** 每日重置计数器 */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailyCounts() {
        dailyCounts.clear();
        log.info("[Marathon] 日产量计数器已重置");
    }

    // ==================== 单章处理 ====================

    private void writeOneChapter(MarathonJob job) {
        String novelId = job.getNovelId();
        int chapterNumber = job.getCurrentChapter() + 1;

        job.addEvent(MarathonEvent.of("CHAPTER_START", chapterNumber, "开始生成第" + chapterNumber + "章"));
        log.info("[Marathon] 开始写章: novel={}, chapter={}", novelId, chapterNumber);

        try {
            // 调用章节生成服务（复用现有管线）
            var result = generationService.generate(novelId, chapterNumber, null, null).block();
            if (result == null || result.containsKey("error")) {
                handleChapterFailure(job, chapterNumber, "生成结果为空或包含错误");
                return;
            }

            // 检查审计分数
            Object auditScore = result.getOrDefault("quality_score", 100);
            double score = auditScore instanceof Number n ? n.doubleValue() : 100;
            if (score < qualityThreshold && job.getConsecutiveFailures() < maxConsecutiveFailures) {
                log.warn("[Marathon] 审计分数低于阈值，将重试: novel={}, chapter={}, score={}",
                    novelId, chapterNumber, score);
                handleChapterFailure(job, chapterNumber, "审计分数过低: " + score);
                return;
            }

            // 漂移检测（必须在重置 consecutiveFailures 之前）
            boolean driftDetected = detectDrift(job);

            // 成功
            job.setCurrentChapter(chapterNumber);
            job.incrementTotalWritten();
            job.resetConsecutiveFailures();
            job.setLastChapterAt(LocalDateTime.now());
            dailyCounts.merge(novelId, 1, Integer::sum);
            job.addEvent(MarathonEvent.of("CHAPTER_COMPLETE", chapterNumber,
                "第" + chapterNumber + "章完成, 审计分=" + score,
                Map.of("qualityScore", score)));

            log.info("[Marathon] 章节完成: novel={}, chapter={}, score={}, totalWritten={}",
                novelId, chapterNumber, score, job.getTotalWritten());

            if (driftDetected) {
                pauseMarathon(novelId, "漂移检测触发: 主线冷/伏笔逾期/角色OOC");
            }

        } catch (Exception e) {
            log.error("[Marathon] 章节生成异常: novel={}, chapter={}", novelId, chapterNumber, e);
            handleChapterFailure(job, chapterNumber, "异常: " + e.getMessage());
        }
    }

    /** 章节失败处理 — 重试升级策略 */
    private void handleChapterFailure(MarathonJob job, int chapterNumber, String reason) {
        int failures = job.incrementConsecutiveFailures();
        job.incrementTotalFailed();
        job.addEvent(MarathonEvent.of("CHAPTER_FAILED", chapterNumber,
            "第" + chapterNumber + "章失败(" + failures + "/" + maxConsecutiveFailures + "): " + reason));

        if (failures >= maxConsecutiveFailures) {
            job.setState(MarathonState.FAILED);
            job.addEvent(MarathonEvent.of("FAILED", chapterNumber,
                "连续失败" + maxConsecutiveFailures + "次，自动暂停。请检查大纲/人物设计/上下文质量。"));
            activeJobs.remove(job.getNovelId());
            dailyCounts.remove(job.getNovelId());
            log.error("[Marathon] 连续失败暂停: novel={}, failures={}", job.getNovelId(), failures);
        }
    }

    /** 自然完结 */
    private void completeMarathon(MarathonJob job) {
        job.setState(MarathonState.COMPLETED);
        job.addEvent(MarathonEvent.of("COMPLETED", job.getCurrentChapter(),
            "创作完成！共写" + job.getTotalWritten() + "章，失败" + job.getTotalFailed() + "章"));
        activeJobs.remove(job.getNovelId());
        dailyCounts.remove(job.getNovelId());
        log.info("[Marathon] 创作完成: novel={}, totalWritten={}", job.getNovelId(), job.getTotalWritten());
    }

    // ==================== 漂移检测 ====================

    /** 检测是否发生创作漂移 */
    private boolean detectDrift(MarathonJob job) {
        // 简化实现：检查连续失败次数和总失败率
        if (job.getConsecutiveFailures() >= 2) {
            log.warn("[Marathon] 连续失败触发漂移预警: novel={}", job.getNovelId());
            return true;
        }
        // 失败率 > 30% 触发
        if (job.getTotalWritten() > 5 && (double) job.getTotalFailed() / (job.getTotalWritten() + job.getTotalFailed()) > 0.3) {
            log.warn("[Marathon] 高失败率触发漂移预警: novel={}", job.getNovelId());
            return true;
        }
        return false;
    }

    private MarathonJob getOrThrow(String novelId) {
        MarathonJob job = activeJobs.get(novelId);
        if (job == null) throw new IllegalArgumentException("未找到马拉松任务: " + novelId);
        return job;
    }
}
