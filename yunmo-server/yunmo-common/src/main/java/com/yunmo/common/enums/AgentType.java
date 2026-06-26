package com.yunmo.common.enums;

import lombok.Getter;

/**
 * AI Agent 类型
 * 对应 Python agents.py 中 6 个 Agent
 */
@Getter
public enum AgentType {
    WRITER("writer", "prose-writer", "主笔 — 负责章节正文写作"),
    SUPERVISOR("supervisor", "supervisor", "主编 — 统筹全局、分配任务、汇总结果"),
    ARCHITECT("architect", "plot-architect", "情节架构师 — 检查因果链、伏笔状态、时间线"),
    INSPECTOR("inspector", "quality-inspector", "质检官 — 33维质量分析"),
    GUARDIAN("guardian", "genre-guardian", "类型守卫 — 扫描禁止术语、检测规避免疫"),
    CUSTODIAN("custodian", "character-custodian", "角色守护者 — 6层角色模型一致性检查"),
    PLEASURE_BEAT("pleasure_beat", "pleasure-beat-designer", "爽点设计 — 压抑→铺垫→爆发→兑现→钩子结构设计"),
    OUTLINER("outliner", "chapter-outliner", "章节细纲 — 起→承→转→合→钩子5要素拆解"),
    POLISHER("polisher", "prose-polisher", "文笔润色 — 对话个性化+去AI味+微动作+生活细节"),
    EDITOR("editor", "adversarial-editor", "对抗编辑 — 标记问题+具体批评"),
    READER("reader", "reader-judge", "读者评审 — 以读者视角评分，1-10分");

    /** 唯一标识 */
    private final String key;
    /** 角色名称（用于 prompt 中 self-identification） */
    private final String role;
    /** 职责说明 */
    private final String description;

    AgentType(String key, String role, String description) {
        this.key = key;
        this.role = role;
        this.description = description;
    }
}
