package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 记忆快照 — 三层记忆架构的持久化实体。
 * <p>
 * 每部小说维护一个递增的快照序列，每个快照对应某个章节完成后的完整记忆状态。
 * memoryJson 以 JSON 数组形式存储所有 {@code MemoryItem} 的序列化数据。
 * </p>
 */
@Getter
@Setter
@Entity
@Table(name = "memory_snapshots")
public class MemorySnapshot extends BaseEntity {

    /** 所属小说ID */
    @Column(name = "novel_id", nullable = false)
    private String novelId;

    /** 记忆条目的 JSON 序列化（TEXT 类型，存储 MemoryItem[]） */
    @Lob
    @Column(name = "memory_json", columnDefinition = "MEDIUMTEXT")
    private String memoryJson;

    /** 记忆条目总数 */
    @Column(name = "item_count", nullable = false)
    private Integer itemCount = 0;

    /** 快照对应的章节号 */
    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber = 0;
}
