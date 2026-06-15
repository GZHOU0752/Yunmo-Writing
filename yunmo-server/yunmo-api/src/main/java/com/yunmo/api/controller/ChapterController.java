package com.yunmo.api.controller;

import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.ChapterVersion;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.ChapterVersionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/chapters")
public class ChapterController {

    private final ChapterRepository chapterRepo;
    private final ChapterVersionRepository versionRepo;

    public ChapterController(ChapterRepository chapterRepo, ChapterVersionRepository versionRepo) {
        this.chapterRepo = chapterRepo;
        this.versionRepo = versionRepo;
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
                return chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber).get();
            }
            Chapter ch = new Chapter();
            ch.setNovelId(novelId);
            ch.setChapterNumber(chapterNumber);
            ch.setTitle("第" + chapterNumber + "章");
            ch.setWordCount(0);
            ch.setStatus(com.yunmo.common.enums.ChapterStatus.OUTLINE);
            return chapterRepo.save(ch);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{chapterNumber}")
    public Mono<ResponseEntity<Chapter>> get(
            @PathVariable String novelId, @PathVariable int chapterNumber) {
        return Mono.fromCallable(() ->
                chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{chapterNumber}")
    public Mono<ResponseEntity<Chapter>> update(
            @PathVariable String novelId, @PathVariable int chapterNumber,
            @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() ->
                chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                        .map(ch -> {
                            if (body.containsKey("title")) ch.setTitle((String) body.get("title"));
                            if (body.containsKey("content")) {
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
                            if (body.containsKey("status")) {
                                try {
                                    ch.setStatus(com.yunmo.common.enums.ChapterStatus.valueOf(
                                            ((String) body.get("status")).toUpperCase()));
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("无效的章节状态: " + body.get("status"));
                                }
                            }
                            return ResponseEntity.ok(chapterRepo.save(ch));
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
                chapterRepo.delete(ch.get());
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

            return ResponseEntity.ok(ch);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
