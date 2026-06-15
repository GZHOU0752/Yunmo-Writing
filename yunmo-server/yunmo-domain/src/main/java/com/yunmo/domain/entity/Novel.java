package com.yunmo.domain.entity;

import com.yunmo.common.enums.ChapterStatus;
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

    @Column(name = "current_chapter")
    private Integer currentChapter = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ChapterStatus status = ChapterStatus.OUTLINE;

    @Column(name = "user_id")
    private String userId;

    /** 简介 */
    @Column(name = "synopsis", columnDefinition = "TEXT")
    private String synopsis;

    /** 封面图片路径 */
    @Column(name = "cover_image")
    private String coverImage;
}
