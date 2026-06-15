package com.yunmo.common.enums;

import lombok.Getter;

@Getter
public enum ForeshadowStatus {
    PLANNED("已规划 — 仅在大纲中标记"),
    PLANTED("已埋设 — 正文中出现"),
    REMINDED("已提示 — 后续章节中再度提及"),
    RESOLVED("已回收 — 伏笔得到解决"),
    ABANDONED("已废弃");

    private final String description;

    ForeshadowStatus(String description) {
        this.description = description;
    }
}
