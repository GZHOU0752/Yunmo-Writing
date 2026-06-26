package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 小说
 */
@Getter
@Setter
@Entity
@Table(name = "novels", indexes = {
    @Index(columnList = "user_id, created_at"),
    @Index(columnList = "genre_id")
})
public class Novel extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(name = "genre_id", nullable = false)
    private String genreId;

    @Column(name = "writing_style", columnDefinition = "TEXT")
    private String writingStyle;

    @Column(name = "word_count")
    private Integer wordCount = 0;

    @Column(name = "total_chapters")
    private Integer totalChapters = 0;

    /** 全书目标总字数（默认150万字，AI规划大纲时以此为最低约束） */
    @Column(name = "target_total_words")
    private Integer targetTotalWords = 1_500_000;

    @Column(name = "current_chapter")
    private Integer currentChapter = 0;

    @Column(name = "user_id")
    private String userId;

    /** 简介（短文案，书封展示用） */
    @Column(name = "synopsis", columnDefinition = "TEXT")
    private String synopsis;

    /** 全书大纲（长文本，网文作者的大纲笔记） */
    @Column(name = "outline", columnDefinition = "TEXT")
    private String outline;

    /** 封面图片路径 */
    @Column(name = "cover_image")
    private String coverImage;
}
