package com.yunmo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 记忆规则 — 对标 Python MemoryRule 模型
 * 通过 AI 对比原稿与修改稿的 diff，提取可复用写作模式
 */
@Getter
@Setter
@Entity
@Table(name = "memory_rules")
public class MemoryRule extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    /** 规则分类: anti-ai | writer-style */
    @Column(length = 32)
    private String category;

    /** 匹配模式 */
    @Column(columnDefinition = "TEXT")
    private String pattern;

    /** 替换建议 */
    @Column(columnDefinition = "TEXT")
    private String replacement;

    /** 优先级 0-100，越高越优先 */
    @Column(nullable = false)
    private Integer priority = 50;

    /** 来源章节 ID */
    @Column(name = "source_chapter_id")
    private String sourceChapterId;

    /** 使用计数 */
    @Column(name = "use_count")
    private Integer useCount = 0;
}
