package com.yunmo.common.enums;

import lombok.Getter;

/**
 * 章节状态机
 * 对应 Python Chapter.status
 */
@Getter
public enum ChapterStatus {
    OUTLINE("大纲 — 仅框架，无正文"),
    DRAFT("草稿 — 用户手动编写中"),
    GENERATING("生成中 — AI 正在写作"),
    GENERATED("已生成 — AI 完成，待审核"),
    REVIEWING("审核中 — Inspector/Guardian 检查中"),
    REVIEWED("已审核 — 质量报告已生成"),
    FINALIZED("定稿 — 用户确认通过");

    private final String description;

    ChapterStatus(String description) {
        this.description = description;
    }
}
