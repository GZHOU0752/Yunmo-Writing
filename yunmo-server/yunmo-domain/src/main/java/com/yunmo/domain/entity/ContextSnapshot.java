package com.yunmo.domain.entity;

import com.yunmo.domain.entity.JsonMapConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "context_snapshots")
public class ContextSnapshot extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(name = "chapter_id")
    private String chapterId;

    @Lob
    @Column
    private String layer1Bible;

    @Lob
    @Column
    private String layer2Active;

    @Lob
    @Column
    private String layer3History;

    @Column(columnDefinition = "TEXT")
    private String layer4Plan;

    @Column(name = "estimated_tokens")
    private Integer estimatedTokens;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> metadata;
}
