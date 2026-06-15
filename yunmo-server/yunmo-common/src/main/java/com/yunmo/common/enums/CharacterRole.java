package com.yunmo.common.enums;

import lombok.Getter;

/**
 * 角色类型
 */
@Getter
public enum CharacterRole {
    PROTAGONIST("主角"),
    ANTAGONIST("反派"),
    SUPPORTING("配角"),
    MINOR("龙套");

    private final String description;

    CharacterRole(String description) {
        this.description = description;
    }
}
