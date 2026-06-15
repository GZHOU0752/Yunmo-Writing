package com.yunmo.api.controller;

import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.ChapterVersion;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.ChapterVersionRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}")
public class SearchController {

    private final ChapterRepository chapterRepo;
    private final ChapterVersionRepository versionRepo;

    public SearchController(ChapterRepository chapterRepo, ChapterVersionRepository versionRepo) {
        this.chapterRepo = chapterRepo;
        this.versionRepo = versionRepo;
    }

    @PostMapping("/search")
    public Mono<List<Map<String, Object>>> search(@PathVariable String novelId,
                                                    @RequestBody Map<String, Object> body) {
        return Mono.<List<Map<String, Object>>>fromCallable(() -> {
            String keyword = (String) body.get("keyword");
            if (keyword == null || keyword.isEmpty()) return List.of();

            List<Chapter> chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
            List<Map<String, Object>> results = new ArrayList<>();
            String searchKw = keyword.toLowerCase();

            for (Chapter ch : chapters) {
                if (ch.getContent() == null) continue;
                String content = ch.getContent();
                String searchIn = content.toLowerCase();

                // 收集所有匹配位置
                List<Integer> positions = new ArrayList<>();
                int idx = 0;
                while ((idx = searchIn.indexOf(searchKw, idx)) >= 0) {
                    positions.add(idx);
                    idx += keyword.length();
                }

                if (positions.isEmpty()) continue;

                // 合并重叠的上下文窗口（窗口半径30字，相邻匹配间隔<60字则合并）
                int radius = 30;
                List<Map<String, Object>> matches = new ArrayList<>();
                int i = 0;
                while (i < positions.size() && matches.size() < 20) {
                    int pos = positions.get(i);
                    int groupStart = Math.max(0, pos - radius);
                    int groupEnd = Math.min(content.length(), pos + keyword.length() + radius);
                    int j = i + 1;
                    // 向后扩展，合并与当前窗口重叠的后续匹配
                    while (j < positions.size()) {
                        int nextPos = positions.get(j);
                        int nextEnd = Math.min(content.length(), nextPos + keyword.length() + radius);
                        if (nextPos - radius <= groupEnd) {
                            groupEnd = nextEnd;
                            j++;
                        } else {
                            break;
                        }
                    }
                    String ctx = content.substring(groupStart, groupEnd).replace("\n", " ");
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("position", pos);
                    m.put("matchCount", j - i);
                    m.put("context", (groupStart > 0 ? "..." : "") + ctx + (groupEnd < content.length() ? "..." : ""));
                    matches.add(m);
                    i = j;
                }

                int totalMatches = positions.size();
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("chapterNumber", ch.getChapterNumber());
                r.put("title", ch.getTitle());
                r.put("matchCount", totalMatches);
                r.put("matches", matches);
                results.add(r);
            }
            return results;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/replace")
    public Mono<Map<String, Object>> replace(@PathVariable String novelId,
                                              @RequestBody Map<String, Object> body) {
        return Mono.<Map<String, Object>>fromCallable(() -> {
            String find = (String) body.get("find");
            String replace = (String) body.get("replace");
            @SuppressWarnings("unchecked")
            List<Integer> chapterNumbers = (List<Integer>) body.get("chapterNumbers");
            if (find == null || replace == null || chapterNumbers == null) {
                return Map.of("replaced", 0);
            }

            int totalReplaced = 0;
            for (int cn : chapterNumbers) {
                Chapter ch = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, cn).orElse(null);
                if (ch == null || ch.getContent() == null) continue;

                // 保存版本快照
                ChapterVersion version = new ChapterVersion();
                version.setChapterId(ch.getId());
                version.setContent(ch.getContent());
                version.setVersionNumber(versionRepo.countByChapterId(ch.getId()) + 1);
                version.setWordCount(ch.getWordCount());
                version.setChangeSummary("全文替换: " + find + " → " + replace);
                versionRepo.save(version);

                // 执行替换并精确计数
                String before = ch.getContent();
                int count = 0;
                int pos = 0;
                while ((pos = before.indexOf(find, pos)) >= 0) { count++; pos += find.length(); }
                String after = before.replace(find, replace);
                totalReplaced += count;
                ch.setContent(after);
                chapterRepo.save(ch);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("replaced", totalReplaced);
            result.put("chapterCount", chapterNumbers.size());
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
