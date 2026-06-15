package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 章节版本历史
 */
@Getter
@Setter
@Entity
@Table(name = "chapter_versions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"chapter_id", "version_number"}))
public class ChapterVersion extends BaseEntity {

    @Column(name = "chapter_id", nullable = false)
    private String chapterId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Lob
    @Column
    private String content;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "word_count")
    private Integer wordCount;
}
