package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 伏笔追踪 — 细粒度追踪每一条伏笔的生命周期
 * 与 Foreshadow 实体互补：Foreshadow 是伏笔总体规划，ForeshadowTracking 是逐章状态流转
 */
@Getter
@Setter
@Entity
@Table(name = "foreshadow_trackings", indexes = {
    @Index(columnList = "novel_id, hook_id"),
    @Index(columnList = "novel_id, status"),
    @Index(columnList = "novel_id, planted_chapter"),
    @Index(columnList = "novel_id, expected_payoff_chapter")
})
public class ForeshadowTracking extends BaseEntity {

    /** 伏笔状态枚举 */
    public enum HookStatus {
        /** 已埋设 — 正文中出现伏笔线索 */
        PLANTED,
        /** 已激活 — 后续章节中再度提及/强化 */
        ACTIVATED,
        /** 已回收 — 伏笔得到解决 */
        RESOLVED,
        /** 已过期 — 超过预期回收章节仍未处理 */
        EXPIRED
    }

    /** 伏笔重要度枚举 */
    public enum HookImportance {
        /** 核心伏笔 — 贯穿全书的主线伏笔 */
        CORE,
        /** 支线伏笔 — 单卷/单弧光的伏笔 */
        SUB,
        /** 装饰伏笔 — 单章内的小伏笔/呼应 */
        ORNAMENTAL
    }

    /** 所属小说ID */
    @Column(name = "novel_id", nullable = false)
    private String novelId;

    /** 伏笔编号（F001, F002...） */
    @Column(name = "hook_id", nullable = false, length = 32)
    private String hookId;

    /** 伏笔内容 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 埋设章节 */
    @Column(name = "planted_chapter")
    private Integer plantedChapter;

    /** 预计回收章节 */
    @Column(name = "expected_payoff_chapter")
    private Integer expectedPayoffChapter;

    /** 伏笔状态 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private HookStatus status = HookStatus.PLANTED;

    /** 伏笔重要度 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private HookImportance importance = HookImportance.SUB;
}
