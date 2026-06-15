package com.yunmo.service;

import com.yunmo.domain.entity.DailyWritingStats;
import com.yunmo.domain.repository.DailyWritingStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class WritingStatsService {

    private static final Logger log = LoggerFactory.getLogger(WritingStatsService.class);
    private final DailyWritingStatsRepository repo;

    public WritingStatsService(DailyWritingStatsRepository repo) {
        this.repo = repo;
    }

    /** 记录当日字数（增量） */
    @Transactional
    public void recordWriting(String novelId, int wordCount) {
        LocalDate today = LocalDate.now();
        DailyWritingStats stats = repo.findByNovelIdAndDate(novelId, today)
                .orElseGet(() -> {
                    DailyWritingStats s = new DailyWritingStats();
                    s.setNovelId(novelId);
                    s.setDate(today);
                    s.setWordCount(0);
                    s.setTargetWordCount(2000);
                    return s;
                });
        stats.setWordCount(stats.getWordCount() + wordCount);
        repo.save(stats);
    }

    /** 设置每日目标 */
    @Transactional
    public void setTarget(String novelId, int target) {
        LocalDate today = LocalDate.now();
        DailyWritingStats stats = repo.findByNovelIdAndDate(novelId, today)
                .orElseGet(() -> {
                    DailyWritingStats s = new DailyWritingStats();
                    s.setNovelId(novelId);
                    s.setDate(today);
                    s.setWordCount(0);
                    return s;
                });
        stats.setTargetWordCount(target);
        repo.save(stats);
    }

    /** 获取统计概览 */
    public Map<String, Object> getOverview(String novelId) {
        LocalDate today = LocalDate.now();
        DailyWritingStats todayStats = repo.findByNovelIdAndDate(novelId, today).orElse(null);

        // 本周
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<DailyWritingStats> weekStats = repo.findByNovelIdAndDateBetweenOrderByDateAsc(novelId, weekStart, today);
        int weekTotal = weekStats.stream().mapToInt(DailyWritingStats::getWordCount).sum();

        // 本月
        LocalDate monthStart = today.withDayOfMonth(1);
        List<DailyWritingStats> monthStats = repo.findByNovelIdAndDateBetweenOrderByDateAsc(novelId, monthStart, today);
        int monthTotal = monthStats.stream().mapToInt(DailyWritingStats::getWordCount).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", todayStats != null ? toMap(todayStats) : emptyToday(today));
        result.put("weekTotal", weekTotal);
        result.put("monthTotal", monthTotal);
        result.put("weekDetail", weekStats.stream().map(this::toMap).toList());
        return result;
    }

    private Map<String, Object> toMap(DailyWritingStats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", s.getDate().toString());
        m.put("wordCount", s.getWordCount());
        m.put("targetWordCount", s.getTargetWordCount());
        return m;
    }

    private Map<String, Object> emptyToday(LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", today.toString());
        m.put("wordCount", 0);
        m.put("targetWordCount", 2000);
        return m;
    }
}
