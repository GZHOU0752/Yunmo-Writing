package com.yunmo.domain.repository;

import com.yunmo.domain.entity.DailyWritingStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyWritingStatsRepository extends JpaRepository<DailyWritingStats, String> {
    Optional<DailyWritingStats> findByNovelIdAndDate(String novelId, LocalDate date);
    List<DailyWritingStats> findByNovelIdAndDateBetweenOrderByDateAsc(String novelId, LocalDate start, LocalDate end);
    List<DailyWritingStats> findByNovelIdOrderByDateDesc(String novelId);
}
