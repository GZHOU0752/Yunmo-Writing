package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色状态快照 — 每章生成后记录角色的位置/境界/情绪/身体/关系/资源变化
 * 用于追踪角色弧光、避免连续性错误
 */
@Getter
@Setter
@Entity
@Table(name = "character_states", indexes = {
    @Index(columnList = "novel_id, character_id, chapter_number"),
    @Index(columnList = "novel_id, chapter_number")
})
public class CharacterState extends BaseEntity {

    /** 所属小说ID */
    @Column(name = "novel_id", nullable = false)
    private String novelId;

    /** 关联角色ID */
    @Column(name = "character_id", nullable = false)
    private String characterId;

    /** 关联章节号 */
    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    /** 当前位置 */
    @Column(length = 500)
    private String location;

    /** 当前境界/等级 */
    @Column(length = 200)
    private String realm;

    /** 情绪状态 */
    @Column(name = "emotional_state", length = 200)
    private String emotionalState;

    /** 身体状况 */
    @Column(name = "physical_state", length = 200)
    private String physicalState;

    /** 关系变化（JSON文本：{targetName: "oldRel → newRel", ...}） */
    @Column(name = "relationship_changes", columnDefinition = "TEXT")
    private String relationshipChanges;

    /** 资源/物品变化（JSON文本：{itemName: "获得/失去/消耗", ...}） */
    @Column(name = "resources", columnDefinition = "TEXT")
    private String resources;
}
