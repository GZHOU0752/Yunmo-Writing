package com.yunmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.ChapterVersion;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.ChapterVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/chapters")
public class ChapterController {

    private static final Logger log = LoggerFactory.getLogger(ChapterController.class);
    private static final String CACHE_PREFIX = "chapter:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final ChapterRepository chapterRepo;
    private final ChapterVersionRepository versionRepo;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public ChapterController(ChapterRepository chapterRepo,
                              ChapterVersionRepository versionRepo,
                              StringRedisTemplate redis) {
        this.chapterRepo = chapterRepo;
        this.versionRepo = versionRepo;
        this.redis = redis;
    }

    @GetMapping
    public Mono<List<Chapter>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() ->
                chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<Chapter> create(@PathVariable String novelId,
                                 @RequestBody(required = false) Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            long count = chapterRepo.countByNovelId(novelId);
            int chapterNumber = (int) count + 1;
            // 允许请求体指定章节号
            if (body != null && body.get("chapterNumber") instanceof Number n) {
                chapterNumber = n.intValue();
            }
            // 检查是否已存在同名章节
            if (chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber).isPresent()) {
                log.debug("[Chapter] 章节已存在 — novel={}, chapter={}", novelId, chapterNumber);
                return chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber).get();
            }
            Chapter ch = new Chapter();
            ch.setNovelId(novelId);
            ch.setChapterNumber(chapterNumber);
            ch.setTitle("第" + chapterNumber + "章");
            ch.setWordCount(0);
            ch.setStatus(com.yunmo.common.enums.ChapterStatus.OUTLINE);
            Chapter saved = chapterRepo.save(ch);
            log.info("[Chapter] 章节已创建 — novel={}, chapter={}, id={}", novelId, chapterNumber, saved.getId());
            return saved;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{chapterNumber}")
    public Mono<ResponseEntity<Chapter>> get(
            @PathVariable String novelId, @PathVariable int chapterNumber) {
        return Mono.fromCallable(() -> {
            var ch = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber);
            if (ch.isPresent()) {
                return ResponseEntity.ok(ch.get());
            }
            return ResponseEntity.notFound().<Chapter>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{chapterNumber}")
    public Mono<ResponseEntity<Chapter>> update(
            @PathVariable String novelId, @PathVariable int chapterNumber,
            @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() ->
                chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                        .map(ch -> {
                            boolean contentChanged = body.containsKey("content");
                            if (body.containsKey("title")) ch.setTitle((String) body.get("title"));
                            if (contentChanged) {
                                String newContent = (String) body.get("content");
                                // 保存版本历史（仅查询最新版本号，避免加载全部历史）
                                ChapterVersion ver = new ChapterVersion();
                                ver.setChapterId(ch.getId());
                                ver.setContent(ch.getContent());
                                ver.setVersionNumber(
                                        versionRepo.findTopByChapterIdOrderByVersionNumberDesc(ch.getId())
                                                .map(v -> v.getVersionNumber() + 1).orElse(1));
                                ver.setWordCount(ch.getWordCount());
                                versionRepo.save(ver);

                                ch.setContent(newContent);
                                // 中文字数估算（以 CJK 字符数为主）
                                int cjkCount = 0;
                                for (char c : newContent.toCharArray()) {
                                    if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                                        cjkCount++;
                                    }
                                }
                                ch.setWordCount(Math.max(cjkCount, newContent.replaceAll("\\s+", "").length() / 2));
                            }
                            if (body.containsKey("writing_plan")) {
                                ch.setWritingPlan((String) body.get("writing_plan"));
                            }
                            if (body.containsKey("status")) {
                                try {
                                    ch.setStatus(com.yunmo.common.enums.ChapterStatus.valueOf(
                                            ((String) body.get("status")).toUpperCase()));
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("无效的章节状态: " + body.get("status"));
                                }
                            }
                            Chapter saved = chapterRepo.save(ch);
                            invalidateCache(novelId, chapterNumber);
                            log.info("[Chapter] 章节已更新 — novel={}, chapter={}, contentChanged={}, words={}",
                                    novelId, chapterNumber, contentChanged, saved.getWordCount());
                            return ResponseEntity.ok(saved);
                        })
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{chapterNumber}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable String novelId, @PathVariable int chapterNumber) {
        return Mono.fromCallable(() -> {
            var ch = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber);
            if (ch.isPresent()) {
                // 先清理关联的版本记录，避免孤儿数据
                var versions = versionRepo.findByChapterIdOrderByVersionNumberDesc(ch.get().getId());
                if (!versions.isEmpty()) versionRepo.deleteAll(versions);
                chapterRepo.delete(ch.get());
                invalidateCache(novelId, chapterNumber);
                log.info("[Chapter] 章节已删除 — novel={}, chapter={}", novelId, chapterNumber);
                return ResponseEntity.noContent().<Void>build();
            }
            return ResponseEntity.notFound().<Void>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 获取章节版本历史列表 */
    @GetMapping("/{chapterNumber}/versions")
    public Mono<List<Map<String, Object>>> listVersions(
            @PathVariable String novelId, @PathVariable int chapterNumber) {
        return Mono.fromCallable(() -> {
            var ch = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                    .orElse(null);
            if (ch == null) return Collections.<Map<String, Object>>emptyList();

            return versionRepo.findByChapterIdOrderByVersionNumberDesc(ch.getId())
                    .stream()
                    .map(v -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("id", v.getId());
                        map.put("versionNumber", v.getVersionNumber());
                        map.put("wordCount", v.getWordCount());
                        map.put("createdAt", v.getCreatedAt());
                        // 预览前 200 字
                        String preview = v.getContent() != null && v.getContent().length() > 200
                                ? v.getContent().substring(0, 200) + "..." : v.getContent();
                        map.put("preview", preview);
                        return map;
                    })
                    .toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 回滚到指定版本 */
    @PostMapping("/{chapterNumber}/versions/{versionId}/restore")
    public Mono<ResponseEntity<Chapter>> restoreVersion(
            @PathVariable String novelId, @PathVariable int chapterNumber,
            @PathVariable String versionId) {
        return Mono.fromCallable(() -> {
            var ch = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                    .orElse(null);
            if (ch == null) return ResponseEntity.notFound().<Chapter>build();

            var ver = versionRepo.findById(versionId).orElse(null);
            if (ver == null || !ver.getChapterId().equals(ch.getId())) {
                return ResponseEntity.notFound().<Chapter>build();
            }

            // 保存当前版本作为历史
            ChapterVersion currentVer = new ChapterVersion();
            currentVer.setChapterId(ch.getId());
            currentVer.setContent(ch.getContent());
            currentVer.setVersionNumber(
                    versionRepo.findTopByChapterIdOrderByVersionNumberDesc(ch.getId())
                            .map(v -> v.getVersionNumber() + 1).orElse(1));
            currentVer.setWordCount(ch.getWordCount());
            currentVer.setChangeSummary("回滚前自动保存");
            versionRepo.save(currentVer);

            // 恢复历史版本内容
            ch.setContent(ver.getContent());
            ch.setWordCount(ver.getWordCount());
            chapterRepo.save(ch);

            log.info("[Chapter] 版本已回滚 — novel={}, chapter={}, version={}", novelId, chapterNumber, ver.getVersionNumber());
            return ResponseEntity.ok(ch);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 从指定版本创建叙事分支 */
    @PostMapping("/{chapterNumber}/fork")
    public Mono<ResponseEntity<Map<String, Object>>> fork(
            @PathVariable String novelId, @PathVariable int chapterNumber,
            @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            var ch = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                    .orElse(null);
            if (ch == null) return ResponseEntity.notFound().<Map<String, Object>>build();

            String branchName = (String) body.getOrDefault("branchName", "分支");
            // 保存当前内容为新分支的一个版本
            ChapterVersion branchVer = new ChapterVersion();
            branchVer.setChapterId(ch.getId());
            branchVer.setContent(ch.getContent());
            branchVer.setVersionNumber(1);
            branchVer.setWordCount(ch.getWordCount());
            branchVer.setBranchName(branchName);
            branchVer.setChangeSummary("创建分支: " + branchName);
            versionRepo.save(branchVer);

            log.info("[Chapter] 叙事分支已创建 — novel={}, chapter={}, branch={}", novelId, chapterNumber, branchName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("branchName", branchName);
            result.put("versionId", branchVer.getId());
            result.put("message", "分支已创建");
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 列出章节的所有分支 */
    @GetMapping("/{chapterNumber}/branches")
    public Mono<List<Map<String, Object>>> listBranches(
            @PathVariable String novelId, @PathVariable int chapterNumber) {
        return Mono.fromCallable(() -> {
            var ch = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                    .orElse(null);
            if (ch == null) return Collections.<Map<String, Object>>emptyList();

            // 收集所有分支名
            Set<String> branchNames = new LinkedHashSet<>();
            branchNames.add("main");
            List<ChapterVersion> allVersions = versionRepo.findByChapterIdOrderByVersionNumberDesc(ch.getId());
            for (ChapterVersion v : allVersions) {
                if (v.getBranchName() != null && !v.getBranchName().isEmpty()) {
                    branchNames.add(v.getBranchName());
                }
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (String bn : branchNames) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("branchName", bn);
                item.put("versionCount", versionRepo
                    .findByChapterIdAndBranchNameOrderByVersionNumberDesc(ch.getId(), bn).size());
                // 最新版本预览
                var latest = versionRepo
                    .findByChapterIdAndBranchNameOrderByVersionNumberDesc(ch.getId(), bn)
                    .stream().findFirst().orElse(null);
                item.put("latestPreview", latest != null && latest.getContent() != null
                    ? (latest.getContent().length() > 100
                        ? latest.getContent().substring(0, 100) + "..." : latest.getContent())
                    : "");
                result.add(item);
            }
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ===== Redis 缓存辅助 =====

    private String cacheKey(String novelId, int chapterNumber) {
        return CACHE_PREFIX + novelId + ":" + chapterNumber;
    }

    private Chapter getCachedChapter(String novelId, int chapterNumber) {
        try {
            String json = redis.opsForValue().get(cacheKey(novelId, chapterNumber));
            if (json != null && !json.isEmpty()) {
                return mapper.readValue(json, Chapter.class);
            }
        } catch (Exception e) {
            log.debug("Redis 读取缓存失败: {}", e.getMessage());
        }
        return null;
    }

    private void cacheChapter(Chapter ch) {
        try {
            String json = mapper.writeValueAsString(ch);
            redis.opsForValue().set(cacheKey(ch.getNovelId(), ch.getChapterNumber()), json, CACHE_TTL);
        } catch (Exception e) {
            log.debug("Redis 写入缓存失败: {}", e.getMessage());
        }
    }

    private void invalidateCache(String novelId, int chapterNumber) {
        try {
            redis.delete(cacheKey(novelId, chapterNumber));
        } catch (Exception e) {
            log.debug("Redis 删除缓存失败: {}", e.getMessage());
        }
    }
}
