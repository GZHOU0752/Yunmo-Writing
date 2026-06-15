package com.yunmo.domain.entity;

import com.yunmo.common.enums.CharacterRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 角色 — 对标 Python Character 模型（6 层认知模型）
 *
 * Layer 1: 世界观 — 角色对世界的认知框架
 * Layer 2: 自我认同 — 角色如何定义自己
 * Layer 3: 价值观 — 角色的道德判断标准
 * Layer 4: 能力 — 角色的天赋和上限
 * Layer 5: 技能 — 角色后天习得的能力
 * Layer 6: 环境 — 角色的社会位置和人际关系
 */
@Getter
@Setter
@Entity
@Table(name = "characters")
public class Character extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CharacterRole role = CharacterRole.SUPPORTING;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ===== 6 层认知模型 =====

    @Column(name = "layer1_worldview", columnDefinition = "TEXT")
    private String layer1Worldview;

    @Column(name = "layer2_identity", columnDefinition = "TEXT")
    private String layer2Identity;

    @Column(name = "layer3_values", columnDefinition = "TEXT")
    private String layer3Values;

    @Column(name = "layer4_abilities", columnDefinition = "TEXT")
    private String layer4Abilities;

    @Column(name = "layer5_skills", columnDefinition = "TEXT")
    private String layer5Skills;

    @Column(name = "layer6_environment", columnDefinition = "TEXT")
    private String layer6Environment;

    // ===== 动态状态 =====

    /** 当前状态快照 (location, emotion, knowledge, goal) */
    @Column(name = "current_state", columnDefinition = "TEXT")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> currentState;

    /** 角色重要度 (1-10)，用于实体冷却计算 */
    @Column(nullable = false)
    private Integer importance = 5;

    /** 最后一次出场章节号 */
    @Column(name = "last_appearance_chapter")
    private Integer lastAppearanceChapter;

    /** 冷却到期章节号 */
    @Column(name = "cooldown_until_chapter")
    private Integer cooldownUntilChapter;

    /** 是否已死亡 */
    @Column(name = "is_dead")
    private Boolean isDead = false;

    // ===== 关联 =====

    @Column(name = "career_id")
    private String careerId;

    @Column(name = "organization_id")
    private String organizationId;
}
