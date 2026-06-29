package com.yunmo.domain.entity;

import com.yunmo.common.enums.WorldElementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 世界观元素 — 对标 Python WorldElement 模型
 */
@Getter
@Setter
@Entity
@Table(name = "world_elements")
public class WorldElement extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", length = 32)
    private WorldElementType elementType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "chapter_introduced")
    private Integer chapterIntroduced;
}
