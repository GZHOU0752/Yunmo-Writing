package com.yunmo.domain.entity;

import com.yunmo.common.enums.ChapterStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 章节 — 对标 Python Chapter 模型
 * 状态机: outline → draft → generating → generated → reviewing → reviewed → finalized
 */
@Getter
@Setter
@Entity
@Table(name = "chapters", indexes = {
    @Index(columnList = "novel_id, chapter_number"),
    @Index(columnList = "novel_id, status")
})
public class Chapter extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Lob
    @Column
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ChapterStatus status = ChapterStatus.OUTLINE;

    @Column(name = "word_count")
    private Integer wordCount = 0;

    @Column(name = "target_word_count")
    private Integer targetWordCount = 2500;

    /** 写作计划（AI 生成前注入的文本指示） */
    @Column(name = "writing_plan", columnDefinition = "TEXT")
    private String writingPlan;

    /** 因果句（大纲节点对应） */
    @Column(name = "causal_sentence", columnDefinition = "TEXT")
    private String causalSentence;

    /** 钩子编排数据（transient，不持久化，生成后随 GET 接口返回） */
    @Transient
    private String hookSelectionJson;

    /** 章节控制卡数据（transient，不持久化，生成后随 GET 接口返回） */
    @Transient
    private String chapterControlCardJson;

    /** 上下文快照 ID */
    @Column(name = "context_snapshot_id")
    private String contextSnapshotId;

    /** 重试次数统计 */
    @Column(name = "retry_count")
    private Integer retryCount = 0;
}
