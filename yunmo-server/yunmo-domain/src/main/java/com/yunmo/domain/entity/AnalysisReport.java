package com.yunmo.domain.entity;

import com.yunmo.common.enums.Verdict;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "analysis_reports", indexes = {
    @Index(columnList = "novel_id, created_at"),
    @Index(columnList = "chapter_id")
})
public class AnalysisReport extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(name = "chapter_id", nullable = false)
    private String chapterId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Verdict verdict;

    @Column(name = "guardian_report", columnDefinition = "TEXT")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> guardianReport;

    @Column(name = "inspector_report", columnDefinition = "TEXT")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> inspectorReport;

    @Column(name = "score")
    private Integer score;

    /** 33维审计详情（JSON 数组） */
    @Column(name = "dimensions_json", columnDefinition = "TEXT")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> dimensionsJson;
}
