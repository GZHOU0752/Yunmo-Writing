package com.yunmo.service.marathon;

/**
 * 马拉松创作状态枚举。
 *
 * @author yunmo
 * @since 2.0
 */
public enum MarathonState {
    /** 空闲 — 尚未启动或已停止 */
    IDLE,
    /** 运行中 — 正在自动创作 */
    RUNNING,
    /** 已暂停 — 用户手动暂停或系统自动暂停（漂移/连续失败） */
    PAUSED,
    /** 已完成 — 自然完结或达到目标章数 */
    COMPLETED,
    /** 失败 — 连续失败超过阈值，需人工介入 */
    FAILED
}
