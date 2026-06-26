package com.yunmo.service.marathon;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 马拉松事件记录 — 不可变数据对象。
 *
 * @author yunmo
 * @since 2.0
 */
public record MarathonEvent(
    /** 事件时间戳 */
    LocalDateTime timestamp,
    /** 事件类型: CHAPTER_START/CHAPTER_COMPLETE/CHAPTER_FAILED/PAUSED/RESUMED/STOPPED/DAILY_LIMIT/COMPLETED */
    String eventType,
    /** 关联章节号（非章节事件时为0） */
    int chapterNumber,
    /** 事件描述 */
    String message,
    /** 附加元数据 */
    Map<String, Object> metadata
) {
    public static MarathonEvent of(String eventType, int chapterNumber, String message) {
        return new MarathonEvent(LocalDateTime.now(), eventType, chapterNumber, message, Map.of());
    }

    public static MarathonEvent of(String eventType, int chapterNumber, String message, Map<String, Object> metadata) {
        return new MarathonEvent(LocalDateTime.now(), eventType, chapterNumber, message, metadata);
    }
}
