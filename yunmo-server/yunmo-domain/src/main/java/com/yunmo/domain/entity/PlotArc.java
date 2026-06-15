package com.yunmo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "plot_arcs")
public class PlotArc extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_chapter")
    private Integer startChapter;

    @Column(name = "end_chapter")
    private Integer endChapter;
}
