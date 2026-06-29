package com.yunmo.domain.entity;

import com.yunmo.common.enums.ForeshadowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 伏笔 — 对标 Python Foreshadow 模型
 */
@Getter
@Setter
@Entity
@Table(name = "foreshadows", indexes = {
    @Index(columnList = "novel_id, status"),
    @Index(columnList = "stable_id")
})
public class Foreshadow extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 关键词列表（JSON 数组字符串或逗号分隔） */
    @Column(length = 1024)
    private String keywords;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ForeshadowStatus status = ForeshadowStatus.PLANNED;

    /** 稳定 ID (MD5)，用于跨章节去重 */
    @Column(name = "stable_id", unique = true, length = 32)
    private String stableId;

    /** 埋设章节号 */
    @Column(name = "planted_chapter")
    private Integer plantedChapter;

    /** 预期回收章节号 */
    @Column(name = "expected_resolve_chapter")
    private Integer expectedResolveChapter;

    /** 实际回收章节号 */
    @Column(name = "resolved_chapter")
    private Integer resolvedChapter;

    /** 紧急度 0-10，>=7 为必须回收 */
    @Column(name = "urgency")
    private Integer urgency = 5;
}
