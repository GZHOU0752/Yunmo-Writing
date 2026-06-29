package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "daily_writing_stats", indexes = {
    @Index(columnList = "novel_id, date", unique = true)
})
public class DailyWritingStats extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "word_count")
    private int wordCount;

    @Column(name = "target_word_count")
    private int targetWordCount = 2000;
}
