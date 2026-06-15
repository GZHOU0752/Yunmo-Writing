package com.yunmo.common.enums;

import lombok.Getter;

/**
 * 世界观元素类型
 */
@Getter
public enum WorldElementType {
    LOCATION("地点"),
    MAGIC_SYSTEM("力量体系"),
    TECHNOLOGY("科技"),
    CULTURE("文化/习俗"),
    HISTORY("历史事件"),
    POLITICS("政治格局"),
    ECONOMY("经济体系"),
    CREATURE("生物/种族"),
    ITEM("重要物品"),
    OTHER("其他");

    private final String description;

    WorldElementType(String description) {
        this.description = description;
    }
}
