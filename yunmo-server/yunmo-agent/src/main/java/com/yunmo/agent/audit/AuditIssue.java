package com.yunmo.agent.audit;

import java.time.Instant;

/**
 * 审计发现 — 单条质量问题的结构化记录
 *
 * <p>由Inspector Agent对37个维度逐一评估后，对不达标的维度生成对应的AuditIssue。
 * 每条Issue包含问题描述、修改建议、原文证据和阻断判定。</p>
 *
 * <h3>严重度等级</h3>
 * <ul>
 *   <li><b>S1_CRITICAL</b> — 致命：结构性缺陷，必须解决才能入库</li>
 *   <li><b>S2_MAJOR</b> — 重要：显著影响质量，强烈建议修改</li>
 *   <li><b>S3_MINOR</b> — 轻微：可优化，不修改不影响阅读</li>
 *   <li><b>S4_ADVISORY</b> — 建议：锦上添花的优化方向</li>
 * </ul>
 *
 * <h3>修复范围</h3>
 * <ul>
 *   <li><b>LOCAL</b> — 局部修改（几句话/几个词范围内）</li>
 *   <li><b>STRUCTURAL</b> — 结构修改（段落/场景级别调整）</li>
 *   <li><b>GLOBAL</b> — 全局修改（贯穿整章/多章的修改）</li>
 * </ul>
 *
 * @param dimension   所属审计维度
 * @param severity    严重度等级
 * @param description 问题描述（面向作者的说明）
 * @param suggestion  修改建议（可操作的改进方向）
 * @param evidence    原文引用作为证据
 * @param repairScope 修复范围
 * @param blocking    是否阻断入库（由维度的tier和严重度共同决定）
 * @param detectedAt  检测时间戳
 */
public record AuditIssue(
        AuditDimension dimension,
        Severity severity,
        String description,
        String suggestion,
        String evidence,
        RepairScope repairScope,
        boolean blocking,
        Instant detectedAt
) {

    /**
     * 创建一个非阻断的建议级Issue
     */
    public static AuditIssue advisory(AuditDimension dimension, String description, String suggestion) {
        return new AuditIssue(
                dimension, Severity.S4_ADVISORY,
                description, suggestion,
                "", RepairScope.LOCAL,
                false, Instant.now()
        );
    }

    /**
     * 创建一个轻微级Issue
     */
    public static AuditIssue minor(AuditDimension dimension, String description, String suggestion, String evidence) {
        return new AuditIssue(
                dimension, Severity.S3_MINOR,
                description, suggestion,
                evidence, RepairScope.LOCAL,
                false, Instant.now()
        );
    }

    /**
     * 创建一个重要级Issue
     */
    public static AuditIssue major(AuditDimension dimension, String description, String suggestion, String evidence) {
        return new AuditIssue(
                dimension, Severity.S2_MAJOR,
                description, suggestion,
                evidence, RepairScope.STRUCTURAL,
                false, Instant.now()
        );
    }

    /**
     * 创建一个致命级阻断Issue
     */
    public static AuditIssue critical(AuditDimension dimension, String description, String suggestion, String evidence) {
        return new AuditIssue(
                dimension, Severity.S1_CRITICAL,
                description, suggestion,
                evidence, RepairScope.STRUCTURAL,
                true, Instant.now()
        );
    }

    /**
     * 根据维度层级和评分自动创建对应严重度的Issue
     *
     * @param dimension 审计维度
     * @param score     0-10分评分
     * @param comment   评语（从LLM解析获得）
     * @param rawText   原文证据
     * @return 根据tier规则创建的Issue，若评分通过(≥6)则返回null
     */
    public static AuditIssue fromScore(AuditDimension dimension, double score, String comment, String rawText) {
        if (score >= 6.0) return null; // 通过，不生成Issue

        return switch (dimension.tier()) {
            case L1_CRASHED_GUARD -> {
                if (score <= 1.0) {
                    yield new AuditIssue(dimension, Severity.S1_CRITICAL,
                            comment, "该维度为L1硬阻塞项，必须彻底修复。建议重写相关段落。",
                            rawText, RepairScope.STRUCTURAL, true, Instant.now());
                } else if (score <= 3.0) {
                    yield new AuditIssue(dimension, Severity.S2_MAJOR,
                            comment, "该维度评分严重偏低，建议重点修改。",
                            rawText, RepairScope.STRUCTURAL, true, Instant.now());
                } else {
                    yield new AuditIssue(dimension, Severity.S3_MINOR,
                            comment, "该维度有优化空间，建议针对性修改。",
                            rawText, RepairScope.LOCAL, false, Instant.now());
                }
            }
            case L2_QUALITY_WARN -> {
                if (score <= 2.0) {
                    yield new AuditIssue(dimension, Severity.S2_MAJOR,
                            comment, "该维度质量明显偏低，建议优先优化。（L2 fail-open：不阻断但需关注）",
                            rawText, RepairScope.STRUCTURAL, false, Instant.now());
                } else {
                    yield new AuditIssue(dimension, Severity.S3_MINOR,
                            comment, "该维度有轻微质量隐患，可考虑优化。",
                            rawText, RepairScope.LOCAL, false, Instant.now());
                }
            }
            case L3_COMPLIANCE -> {
                yield new AuditIssue(dimension, Severity.S1_CRITICAL,
                        comment, "合规维度不通过，必须修正后方可入库。",
                        rawText, RepairScope.GLOBAL, true, Instant.now());
            }
        };
    }

    // ==================== 严重度枚举 ====================

    public enum Severity {
        /** 致命 — 结构性缺陷，必须解决 */
        S1_CRITICAL("致命"),
        /** 重要 — 显著影响质量 */
        S2_MAJOR("重要"),
        /** 轻微 — 可优化 */
        S3_MINOR("轻微"),
        /** 建议 — 锦上添花 */
        S4_ADVISORY("建议");

        private final String displayName;

        Severity(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    // ==================== 修复范围枚举 ====================

    public enum RepairScope {
        /** 局部 — 几句话/几个词 */
        LOCAL("局部"),
        /** 结构 — 段落/场景级别 */
        STRUCTURAL("结构"),
        /** 全局 — 贯穿整章/多章 */
        GLOBAL("全局");

        private final String displayName;

        RepairScope(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }
}
