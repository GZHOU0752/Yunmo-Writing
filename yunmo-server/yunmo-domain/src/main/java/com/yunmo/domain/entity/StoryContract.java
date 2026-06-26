package com.yunmo.domain.entity;

import com.yunmo.common.enums.ContractType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 故事合同 — 三层合同架构的核心实体
 *
 * 合同层级：
 *   MASTER（全书合同）→ VOLUME（卷级合同）→ CHAPTER（章级合同）→ REVIEW（审查合同）
 *
 * 每个合同包含: 约束(constraints)、反模式(anti_patterns)、
 * 动态上下文(dynamic_context)、裁决推理(reasoning)、
 * 必须覆盖节点(must_cover_nodes)、禁区(forbidden_zones)
 *
 * 参考 webnovel-writer 的 Story System 设计
 */
@Getter
@Setter
@Entity
@Table(name = "story_contracts", indexes = {
    @Index(columnList = "novel_id, contract_type, chapter_number"),
    @Index(columnList = "novel_id, contract_type, status"),
    @Index(columnList = "novel_id, contract_type, volume_number")
})
public class StoryContract extends BaseEntity {

    /** 所属小说ID */
    @Column(name = "novel_id", nullable = false, length = 36)
    private String novelId;

    /** 合同类型: MASTER/VOLUME/CHAPTER/REVIEW */
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 20)
    private ContractType contractType;

    /** 卷号（卷级合同专用，其余类型为 null） */
    @Column(name = "volume_number")
    private Integer volumeNumber;

    /** 章号（章级/审查合同专用，其余类型为 null） */
    @Column(name = "chapter_number")
    private Integer chapterNumber;

    /** 核心约束 — JSON文本（调性/体裁规则/禁忌） */
    @Column(name = "constraints_json", columnDefinition = "TEXT")
    private String constraintsJson;

    /** 反模式 — JSON文本（AI 不能触碰的写作模式） */
    @Column(name = "anti_patterns_json", columnDefinition = "TEXT")
    private String antiPatternsJson;

    /** 动态上下文 — JSON文本（从参考表检索的写作建议） */
    @Column(name = "dynamic_context_json", columnDefinition = "TEXT")
    private String dynamicContextJson;

    /** 裁决层推理 — 为什么这份合同是有效的，推理过程记录 */
    @Column(name = "reasoning_text", columnDefinition = "TEXT")
    private String reasoningText;

    /** 必须覆盖的结构化节点 — JSON文本（CBN/CPNs/CEN） */
    @Column(name = "must_cover_nodes_json", columnDefinition = "TEXT")
    private String mustCoverNodesJson;

    /** 禁区列表 — JSON文本（明确禁止出现的情节/写法/措辞） */
    @Column(name = "forbidden_zones_json", columnDefinition = "TEXT")
    private String forbiddenZonesJson;

    /** 合同业务版本号（非JPA乐观锁，每次合同变更时递增） */
    @Column(name = "contract_version", nullable = false)
    private Integer contractVersion = 1;

    /** 合同状态: ACTIVE（当前生效）/ SUPERSEDED（已被新版本替代） */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ContractStatus status = ContractStatus.ACTIVE;

    /** 合同状态枚举 */
    public enum ContractStatus {
        /** 活跃 — 当前生效合同，生成/审查时必须遵守 */
        ACTIVE,
        /** 已取代 — 被更高版本的合同替换，仅保留用于审计追溯 */
        SUPERSEDED
    }
}
