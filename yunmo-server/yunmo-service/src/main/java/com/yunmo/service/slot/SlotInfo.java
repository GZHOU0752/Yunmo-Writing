package com.yunmo.service.slot;

import java.time.LocalDateTime;

/**
 * 槽位信息 — 每个Slot对应一部独立的小说工作区。
 */
public class SlotInfo {
    /** 槽位ID (slot_001, slot_002...) */
    private String id;
    /** 标题拼音slug */
    private String slug;
    /** 小说标题 */
    private String title;
    /** 数据库文件路径 */
    private String dbPath;
    /** 工作区路径 */
    private String workspacePath;
    /** 章节数 */
    private int chapterCount;
    /** 总字数 */
    private int totalWords;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后访问时间 */
    private LocalDateTime lastAccessedAt;
    /** 状态 */
    private SlotStatus status = SlotStatus.IDLE;

    public SlotInfo() {}

    public SlotInfo(String id, String slug, String title, String workspacePath) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.workspacePath = workspacePath;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
    }

    // ---- getters/setters ----
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDbPath() { return dbPath; }
    public void setDbPath(String dbPath) { this.dbPath = dbPath; }
    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }
    public int getChapterCount() { return chapterCount; }
    public void setChapterCount(int chapterCount) { this.chapterCount = chapterCount; }
    public int getTotalWords() { return totalWords; }
    public void setTotalWords(int totalWords) { this.totalWords = totalWords; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(LocalDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
}
