package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 世界规则 — 追踪小说世界观中的规则体系
 * 记录规则的揭示、建立、打破和修改，防止世界观矛盾
 */
@Getter
@Setter
@Entity
@Table(name = "world_rules", indexes = {
    @Index(columnList = "novel_id, category"),
    @Index(columnList = "novel_id, status")
})
public class WorldRule extends BaseEntity {

    /** 规则分类枚举 */
    public enum RuleCategory {
        /** 力量体系 — 修炼等级/异能机制/魔法体系 */
        POWER_SYSTEM,
        /** 物理规则 — 世界特有的物理/时空法则 */
        PHYSICS,
        /** 社会规则 — 政治体制/宗门规则/法律 */
        SOCIETY,
        /** 魔法规则 — 法术/符文/阵法体系 */
        MAGIC,
        /** 其他 */
        OTHER
    }

    /** 规则状态枚举 */
    public enum RuleStatus {
        /** 生效中 */
        ACTIVE,
        /** 已打破 — 角色突破/反噬/例外 */
        BROKEN,
        /** 已修改 — 规则本身被修正/升级 */
        MODIFIED
    }

    /** 所属小说ID */
    @Column(name = "novel_id", nullable = false)
    private String novelId;

    /** 规则名 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 规则分类 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RuleCategory category = RuleCategory.OTHER;

    /** 规则内容 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 首次揭示章节 */
    @Column(name = "revealed_chapter")
    private Integer revealedChapter;

    /** 规则状态 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RuleStatus status = RuleStatus.ACTIVE;

    /** 最后修改章节 */
    @Column(name = "last_modified_chapter")
    private Integer lastModifiedChapter;
}
