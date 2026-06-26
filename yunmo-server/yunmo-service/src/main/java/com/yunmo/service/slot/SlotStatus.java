package com.yunmo.service.slot;

/**
 * 槽位状态枚举。
 */
public enum SlotStatus {
    /** 当前活跃（正在编辑的小说） */
    ACTIVE,
    /** 空闲（已创建但未激活） */
    IDLE,
    /** 已归档 */
    ARCHIVED
}
