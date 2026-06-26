package com.yunmo.api.controller;

import com.yunmo.service.marathon.MarathonJob;
import com.yunmo.service.marathon.MarathonScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 马拉松创作 REST API。
 *
 * @author yunmo
 * @since 2.0
 */
@RestController
@RequestMapping("/api/v1/novels/{novelId}/marathon")
public class MarathonController {

    private static final Logger log = LoggerFactory.getLogger(MarathonController.class);

    private final MarathonScheduler scheduler;

    public MarathonController(MarathonScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** 启动马拉松 */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(
        @PathVariable String novelId,
        @RequestBody Map<String, Object> body
    ) {
        try {
            int startChapter = body.containsKey("startChapter")
                ? ((Number) body.get("startChapter")).intValue() : 1;
            int targetChapters = body.containsKey("targetChapters")
                ? ((Number) body.get("targetChapters")).intValue() : 0;
            log.info("[MarathonAPI] 启动马拉松请求 — novel={}, startChapter={}, targetChapters={}", novelId, startChapter, targetChapters);
            MarathonJob job = scheduler.startMarathon(novelId, startChapter, targetChapters);
            log.info("[MarathonAPI] 马拉松已启动 — novel={}, state={}, currentChapter={}", novelId, job.getState(), job.getCurrentChapter());
            return ResponseEntity.ok(toJobMap(job));
        } catch (Exception e) {
            log.error("[MarathonAPI] 启动马拉松失败 — novel={}, error={}", novelId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 暂停 */
    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pause(
        @PathVariable String novelId,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        try {
            String reason = body != null && body.containsKey("reason")
                ? (String) body.get("reason") : "手动暂停";
            log.info("[MarathonAPI] 暂停马拉松请求 — novel={}, reason={}", novelId, reason);
            MarathonJob job = scheduler.pauseMarathon(novelId, reason);
            log.info("[MarathonAPI] 马拉松已暂停 — novel={}, currentChapter={}", novelId, job.getCurrentChapter());
            return ResponseEntity.ok(toJobMap(job));
        } catch (Exception e) {
            log.error("[MarathonAPI] 暂停马拉松失败 — novel={}, error={}", novelId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 恢复 */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resume(@PathVariable String novelId) {
        try {
            log.info("[MarathonAPI] 恢复马拉松请求 — novel={}", novelId);
            MarathonJob job = scheduler.resumeMarathon(novelId);
            log.info("[MarathonAPI] 马拉松已恢复 — novel={}, currentChapter={}", novelId, job.getCurrentChapter());
            return ResponseEntity.ok(toJobMap(job));
        } catch (Exception e) {
            log.error("[MarathonAPI] 恢复马拉松失败 — novel={}, error={}", novelId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 停止 */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String novelId) {
        try {
            log.info("[MarathonAPI] 停止马拉松请求 — novel={}", novelId);
            MarathonJob job = scheduler.stopMarathon(novelId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stopped", job != null);
            result.put("message", job != null ? "已停止" : "未找到活跃任务");
            log.info("[MarathonAPI] 马拉松已停止 — novel={}, stopped={}", novelId, job != null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[MarathonAPI] 停止马拉松失败 — novel={}, error={}", novelId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 查询状态 */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String novelId) {
        MarathonJob job = scheduler.getStatus(novelId);
        if (job == null) {
            log.debug("[MarathonAPI] 查询马拉松状态 — novel={}, state=IDLE", novelId);
            return ResponseEntity.ok(Map.of("state", "IDLE", "message", "无活跃任务"));
        }
        log.debug("[MarathonAPI] 查询马拉松状态 — novel={}, state={}, currentChapter={}", novelId, job.getState(), job.getCurrentChapter());
        return ResponseEntity.ok(toJobMap(job));
    }

    /** 所有活跃任务 */
    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> allJobs() {
        List<Map<String, Object>> jobs = new ArrayList<>();
        for (MarathonJob job : scheduler.getAllJobs()) {
            jobs.add(toJobMap(job));
        }
        log.debug("[MarathonAPI] 查询所有活跃任务 — count={}", jobs.size());
        return ResponseEntity.ok(jobs);
    }

    private Map<String, Object> toJobMap(MarathonJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("novelId", job.getNovelId());
        map.put("state", job.getState().name());
        map.put("currentChapter", job.getCurrentChapter());
        map.put("targetChapters", job.getTargetChapters());
        map.put("consecutiveFailures", job.getConsecutiveFailures());
        map.put("totalWritten", job.getTotalWritten());
        map.put("totalFailed", job.getTotalFailed());
        map.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
        map.put("lastChapterAt", job.getLastChapterAt() != null ? job.getLastChapterAt().toString() : null);
        map.put("pausedAt", job.getPausedAt() != null ? job.getPausedAt().toString() : null);
        map.put("pauseReason", job.getPauseReason());
        // 最近5条事件
        List<Map<String, Object>> recentEvents = new ArrayList<>();
        var events = job.getEvents();
        int start = Math.max(0, events.size() - 5);
        for (int i = start; i < events.size(); i++) {
            var e = events.get(i);
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("timestamp", e.timestamp().toString());
            em.put("eventType", e.eventType());
            em.put("chapterNumber", e.chapterNumber());
            em.put("message", e.message());
            recentEvents.add(em);
        }
        map.put("recentEvents", recentEvents);
        return map;
    }
}
