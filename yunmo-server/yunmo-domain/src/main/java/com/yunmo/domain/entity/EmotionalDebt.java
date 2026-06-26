package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 情感债/承诺追踪 — 追踪角色间的承诺、恩怨、情感债务
 * 确保"说过的誓言不会被遗忘，欠下的恩仇终将清算"
 */
@Getter
@Setter
@Entity
@Table(name = "emotional_debts", indexes = {
    @Index(columnList = "novel_id, debtor"),
    @Index(columnList = "novel_id, creditor"),
    @Index(columnList = "novel_id, status"),
    @Index(columnList = "novel_id, debt_type")
})
public class EmotionalDebt extends BaseEntity {

    /** 债务类型枚举 */
    public enum DebtType {
        /** 承诺 — 角色明确做出的承诺 */
        PROMISE,
        /** 怨恨 — 角色间的仇恨/敌意 */
        GRUDGE,
        /** 感恩 — 受恩未报 */
        GRATITUDE,
        /** 愧疚 — 角色内心的愧疚 */
        GUILT,
        /** 爱慕 — 暗恋/未表达的情感 */
        LOVE
    }

    /** 债务状态枚举 */
    public enum DebtStatus {
        /** 未偿还 */
        OUTSTANDING,
        /** 已偿还/已解决 */
        RESOLVED,
        /** 已被遗忘/忽略 */
        FORGOTTEN
    }

    /** 所属小说ID */
    @Column(name = "novel_id", nullable = false)
    private String novelId;

    /** 债务人（角色名） */
    @Column(nullable = false, length = 100)
    private String debtor;

    /** 债权人（角色名） */
    @Column(nullable = false, length = 100)
    private String creditor;

    /** 债务类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "debt_type", length = 32)
    private DebtType debtType = DebtType.PROMISE;

    /** 债务内容 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 产生章节 */
    @Column(name = "created_chapter")
    private Integer createdChapter;

    /** 预期解决章节 */
    @Column(name = "expected_resolution_chapter")
    private Integer expectedResolutionChapter;

    /** 实际解决章节 */
    @Column(name = "resolved_chapter")
    private Integer resolvedChapter;

    /** 债务状态 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private DebtStatus status = DebtStatus.OUTSTANDING;
}
