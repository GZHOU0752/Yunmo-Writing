package com.yunmo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 职业体系 — 对标 Python Career 模型
 */
@Getter
@Setter
@Entity
@Table(name = "careers")
public class Career extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(nullable = false)
    private String name;

    private String description;

    /** 阶段列表 JSON: [{"name":"筑基","stage":1},{"name":"金丹","stage":2},...] */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = JsonListConverter.class)
    private List<Object> stages;

    @Column(name = "max_stage")
    private Integer maxStage;
}
