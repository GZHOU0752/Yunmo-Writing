package com.yunmo.common.enums;

import lombok.Getter;

@Getter
public enum OrganizationType {
    FACTION("势力/门派"),
    GUILD("公会"),
    FAMILY("家族"),
    KINGDOM("王国"),
    COMPANY("公司/组织"),
    OTHER("其他");

    private final String description;

    OrganizationType(String description) {
        this.description = description;
    }
}
