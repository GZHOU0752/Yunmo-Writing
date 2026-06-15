package com.yunmo.domain.repository;

import com.yunmo.domain.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, String> {
    List<AnalysisReport> findByNovelIdOrderByCreatedAtDesc(String novelId);
    Optional<AnalysisReport> findByChapterId(String chapterId);
}
