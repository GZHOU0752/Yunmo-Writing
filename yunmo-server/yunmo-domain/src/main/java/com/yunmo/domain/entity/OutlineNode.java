package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "outline_nodes")
public class OutlineNode extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(nullable = false)
    private String title;

    @Column(name = "sequence_order")
    private Integer sequenceOrder;

    @Column(name = "causal_sentence", columnDefinition = "TEXT")
    private String causalSentence;

    @Column(columnDefinition = "TEXT")
    private String structure;

    @Column(name = "parent_id")
    private String parentId;

    @Column(name = "chapter_number")
    private Integer chapterNumber;

    /** 大纲层级：0=总纲, 1=卷, 2=章, 3=节 */
    @Column(nullable = false)
    private Integer level;

    /** 状态：draft | confirmed | ai_generated */
    @Column(length = 32)
    private String status;

    /** 大纲详细内容（扩展文本） */
    @Column(name = "outline_content", columnDefinition = "TEXT")
    private String outlineContent;

    /** 该大纲节点的目标字数 */
    @Column(name = "word_count_target")
    private Integer wordCountTarget;
}
