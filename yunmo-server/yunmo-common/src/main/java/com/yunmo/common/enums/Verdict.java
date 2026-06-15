package com.yunmo.common.enums;

/**
 * 流水线判定结果
 * 对应 Python generation_graph.decide_verdict()
 */
public enum Verdict {
    /** 通过 — 章节质量合格，进入下一章 */
    PASS,
    /** 重写 — 严重违规 >2，整章重写 */
    REWRITE,
    /** 重新生成 — fatal 违规，丢弃当前结果重新生成 */
    REGENERATE
}
