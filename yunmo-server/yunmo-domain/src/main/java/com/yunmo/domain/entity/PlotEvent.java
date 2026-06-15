package com.yunmo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "plot_events")
public class PlotEvent extends BaseEntity {

    @Column(name = "plot_arc_id", nullable = false)
    private String plotArcId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "chapter_number")
    private Integer chapterNumber;

    @Column(name = "sequence_order")
    private Integer sequenceOrder;
}
