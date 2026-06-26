package com.yunmo.service.memory;

import java.util.HashMap;
import java.util.Map;

/**
 * 记忆条目 — 三层记忆架构的统一数据单元。
 * <p>
 * 每一条记忆都归属于某一个层（working/episodic/semantic），
 * 通过 category + subject + field 三元组确定唯一语义键，
 * 用于去重、排序、压缩等操作。
 * </p>
 *
 * @param id            记忆唯一标识
 * @param layer         所属层: working | episodic | semantic
 * @param category      分类: character_state | relationship | story_fact | open_loop |
 *                      reader_promise | world_rule | timeline | chapter_outline |
 *                      chapter_summary | protagonist_snapshot | status_change |
 *                      relationship_change | appearance_record
 * @param subject       记忆主体（角色名/事件名/地点名）
 * @param field         记忆字段（状态/关系/事实等细粒度标识）
 * @param value         记忆值（具体内容）
 * @param payload       附加负载（可选的键值对扩展）
 * @param status        状态: active | outdated | resolved
 * @param sourceChapter 来源章节号
 * @param evidence      来源证据（原文引用片段）
 * @param updatedAt     最后更新时间戳（epoch millis）
 */
public record MemoryItem(
        String id,
        String layer,
        String category,
        String subject,
        String field,
        String value,
        Map<String, Object> payload,
        String status,
        int sourceChapter,
        String evidence,
        long updatedAt
) {

    /**
     * 工厂方法 — 创建一条活跃记忆条目
     */
    public static MemoryItem of(String id, String layer, String category,
                                String subject, String field, String value,
                                int sourceChapter, String evidence) {
        return new MemoryItem(
                id, layer, category, subject, field, value,
                new HashMap<>(), "active", sourceChapter, evidence,
                System.currentTimeMillis()
        );
    }

    /**
     * 工厂方法 — 带 payload 的完整构造
     */
    public static MemoryItem of(String id, String layer, String category,
                                String subject, String field, String value,
                                Map<String, Object> payload, String status,
                                int sourceChapter, String evidence, long updatedAt) {
        return new MemoryItem(id, layer, category, subject, field, value,
                payload != null ? new HashMap<>(payload) : new HashMap<>(),
                status, sourceChapter, evidence, updatedAt);
    }

    /**
     * 判断语义键是否匹配（category + subject + field 完全一致即为同一条记忆的版本更新）
     */
    public boolean matchesKey(MemoryItem other) {
        return this.category.equals(other.category)
                && this.subject.equals(other.subject)
                && this.field.equals(other.field);
    }

    /**
     * 语义键字符串，用于 Map key 去重
     */
    public String semanticKey() {
        return category + "::" + subject + "::" + field;
    }

    /**
     * 判断是否为过时条目（可被新版替换）
     */
    public boolean isOutdated() {
        return "outdated".equals(status);
    }

    /**
     * 判断是否为已完结条目（伏笔回收等）
     */
    public boolean isResolved() {
        return "resolved".equals(status);
    }

    /**
     * 判断是否为活跃条目
     */
    public boolean isActive() {
        return "active".equals(status);
    }

    /**
     * 复制并修改状态
     */
    public MemoryItem withStatus(String newStatus) {
        return new MemoryItem(id, layer, category, subject, field, value,
                payload, newStatus, sourceChapter, evidence, updatedAt);
    }

    /**
     * 复制并更新时间戳
     */
    public MemoryItem withUpdatedAt(long newUpdatedAt) {
        return new MemoryItem(id, layer, category, subject, field, value,
                payload, status, sourceChapter, evidence, newUpdatedAt);
    }
}
