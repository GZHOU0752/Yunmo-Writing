package com.yunmo.service.marathon;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 马拉松任务 — 追踪单个小说的自动创作状态。
 *
 * @author yunmo
 * @since 2.0
 */
public class MarathonJob {
    /** 小说ID */
    private String novelId;
    /** 当前状态 */
    private volatile MarathonState state = MarathonState.IDLE;
    /** 当前章节号 */
    private int currentChapter;
    /** 目标章节数（0 = 自然完结） */
    private int targetChapters;
    /** 连续失败次数 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    /** 总完成章数 */
    private final AtomicInteger totalWritten = new AtomicInteger(0);
    /** 总失败章数 */
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    /** 启动时间 */
    private LocalDateTime startedAt;
    /** 上一章完成时间 */
    private LocalDateTime lastChapterAt;
    /** 暂停时间 */
    private LocalDateTime pausedAt;
    /** 暂停原因 */
    private String pauseReason;
    /** 事件日志（最多保留200条） */
    private List<MarathonEvent> events = new ArrayList<>();

    public MarathonJob() {}

    public MarathonJob(String novelId, int startChapter, int targetChapters) {
        this.novelId = novelId;
        this.currentChapter = startChapter;
        this.targetChapters = targetChapters;
        this.startedAt = LocalDateTime.now();
        this.state = MarathonState.RUNNING;
    }

    /** 记录事件（自动截断到200条） */
    public void addEvent(MarathonEvent event) {
        events.add(event);
        if (events.size() > 200) {
            events = new ArrayList<>(events.subList(events.size() - 100, events.size()));
        }
    }

    /** 是否可继续（未达目标且未失败） */
    public boolean canContinue() {
        if (state != MarathonState.RUNNING) return false;
        if (targetChapters > 0 && currentChapter > targetChapters) return false;
        return true;
    }

    // ---- getters / setters ----
    public String getNovelId() { return novelId; }
    public void setNovelId(String novelId) { this.novelId = novelId; }
    public MarathonState getState() { return state; }
    public void setState(MarathonState state) { this.state = state; }
    public int getCurrentChapter() { return currentChapter; }
    public void setCurrentChapter(int currentChapter) { this.currentChapter = currentChapter; }
    public int getTargetChapters() { return targetChapters; }
    public void setTargetChapters(int targetChapters) { this.targetChapters = targetChapters; }
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures.set(consecutiveFailures); }
    public int getTotalWritten() { return totalWritten.get(); }
    public void setTotalWritten(int totalWritten) { this.totalWritten.set(totalWritten); }
    public int getTotalFailed() { return totalFailed.get(); }
    public void setTotalFailed(int totalFailed) { this.totalFailed.set(totalFailed); }

    /** 原子递增已完成章数 */
    public int incrementTotalWritten() { return totalWritten.incrementAndGet(); }
    /** 原子递增失败章数 */
    public int incrementTotalFailed() { return totalFailed.incrementAndGet(); }
    /** 原子递增连续失败次数 */
    public int incrementConsecutiveFailures() { return consecutiveFailures.incrementAndGet(); }
    /** 原子重置连续失败次数 */
    public void resetConsecutiveFailures() { consecutiveFailures.set(0); }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getLastChapterAt() { return lastChapterAt; }
    public void setLastChapterAt(LocalDateTime lastChapterAt) { this.lastChapterAt = lastChapterAt; }
    public LocalDateTime getPausedAt() { return pausedAt; }
    public void setPausedAt(LocalDateTime pausedAt) { this.pausedAt = pausedAt; }
    public String getPauseReason() { return pauseReason; }
    public void setPauseReason(String pauseReason) { this.pauseReason = pauseReason; }
    public List<MarathonEvent> getEvents() { return events; }
    public void setEvents(List<MarathonEvent> events) { this.events = events; }
}
