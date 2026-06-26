package com.yunmo.common.enums;

import lombok.Getter;

/**
 * 故事合同类型 — 三层合同架构
 *
 * MASTER_SETTING → VOLUME → CHAPTER → REVIEW
 * 每层合同继承并细化上层约束，形成层层递进的写作保障体系
 */
@Getter
public enum ContractType {
    /** 全书合同 — 体裁调性、核心承诺、全局禁忌 */
    MASTER("全书合同"),
    /** 卷级合同 — 卷级剧情弧、节奏控制 */
    VOLUME("卷级合同"),
    /** 章级合同 — CBN/CPNs/CEN 结构化节点 */
    CHAPTER("章级合同"),
    /** 审查合同 — 质量门禁、禁区扫描 */
    REVIEW("审查合同");

    private final String description;

    ContractType(String description) {
        this.description = description;
    }
}
