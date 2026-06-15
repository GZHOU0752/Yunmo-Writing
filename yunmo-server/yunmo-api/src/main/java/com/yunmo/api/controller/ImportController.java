package com.yunmo.api.controller;

import com.yunmo.common.config.AppProperties;
import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.Novel;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.NovelRepository;
import com.yunmo.common.enums.ChapterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);
    private final NovelRepository novelRepo;
    private final ChapterRepository chapterRepo;
    private final AppProperties appProperties;

    /** 章节标题正则：支持 "第X章"、"第X节"、"Chapter X" 等格式 */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?:第\\s*([\\d零一二三四五六七八九十百千万]+)\\s*[章节回]|Chapter\\s+([0-9]+))\\s*(.*?)(?:\\n|$)",
            Pattern.MULTILINE
    );

    public ImportController(NovelRepository novelRepo, ChapterRepository chapterRepo,
                            AppProperties appProperties) {
        this.novelRepo = novelRepo;
        this.chapterRepo = chapterRepo;
        this.appProperties = appProperties;
    }

    /**
     * 上传 TXT/Markdown 文件并解析为小说章节
     */
    @PostMapping(value = "/book", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> importBook(@RequestPart("file") FilePart file) {
        return file.content()
                .collectList()
                .flatMap(dataBuffers -> {
                    StringBuilder sb = new StringBuilder();
                    for (var buffer : dataBuffers) {
                        sb.append(StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString());
                    }
                    String fullText = sb.toString();
                    return Mono.fromCallable(() -> doImport(file.filename(), fullText))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    private Map<String, Object> doImport(String filename, String fullText) {
        // 从文件名推断书名
        String title = filename.replaceFirst("\\.(txt|md|markdown|text)$", "")
                .replaceAll("[_-]", " ")
                .trim();
        if (title.isBlank()) title = "导入小说";

        // 解析章节
        List<ParsedChapter> parsed = parseChapters(fullText);

        if (parsed.isEmpty()) {
            // 整个文件作为单章
            Novel novel = createNovel(title);
            Chapter ch = createChapter(novel.getId(), 1, "第1章", fullText, fullText.length());
            return buildResponse(novel, 1);
        }

        // 创建小说和所有章节
        Novel novel = createNovel(title);

        int totalWords = 0;
        for (int i = 0; i < parsed.size(); i++) {
            var pc = parsed.get(i);
            String chTitle = pc.title != null && !pc.title.isBlank()
                    ? String.format("第%d章 %s", i + 1, pc.title)
                    : String.format("第%d章", i + 1);
            Chapter ch = createChapter(novel.getId(), i + 1, chTitle, pc.content,
                    countChineseChars(pc.content));
            totalWords += ch.getWordCount();
        }

        // 更新小说总字数
        novel.setWordCount(totalWords);
        novel.setTotalChapters(parsed.size());
        novel.setCurrentChapter(1);
        novelRepo.save(novel);

        log.info("书籍导入完成: title={}, chapters={}, words={}", title, parsed.size(), totalWords);
        return buildResponse(novel, parsed.size());
    }

    /** 解析章节 */
    private List<ParsedChapter> parseChapters(String text) {
        List<ParsedChapter> result = new ArrayList<>();
        Matcher matcher = CHAPTER_PATTERN.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            // 保存上一个章节内容（从上一个章节标题到当前章节标题之间）
            if (!result.isEmpty() && lastEnd > 0) {
                var prev = result.get(result.size() - 1);
                prev.content = text.substring(lastEnd, matcher.start()).trim();
            } else if (matcher.start() > 0) {
                // 第一章标题之前的内容作为前言
                String preamble = text.substring(0, matcher.start()).trim();
                if (!preamble.isEmpty()) {
                    result.add(new ParsedChapter("前言", preamble));
                }
            }

            String chTitle = matcher.group(3) != null ? matcher.group(3).trim() : "";
            result.add(new ParsedChapter(chTitle, ""));
            lastEnd = matcher.end();
        }

        // 最后一个章节的内容
        if (!result.isEmpty() && lastEnd < text.length()) {
            result.get(result.size() - 1).content = text.substring(lastEnd).trim();
        }

        return result;
    }

    private Novel createNovel(String title) {
        Novel novel = new Novel();
        novel.setTitle(title);
        novel.setGenreId("xianxia");
        novel.setStatus(ChapterStatus.OUTLINE);
        novel.setUserId(appProperties.getDefaultUserId());
        return novelRepo.save(novel);
    }

    private Chapter createChapter(String novelId, int number, String title, String content, int wordCount) {
        Chapter ch = new Chapter();
        ch.setNovelId(novelId);
        ch.setChapterNumber(number);
        ch.setTitle(title);
        ch.setContent(content);
        ch.setWordCount(wordCount);
        ch.setTargetWordCount(2000);
        ch.setStatus(ChapterStatus.GENERATED);
        return chapterRepo.save(ch);
    }

    /** 中文字数统计 */
    private int countChineseChars(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                count++;
            }
        }
        return Math.max(count, text.replaceAll("\\s+", "").length() / 2);
    }

    private Map<String, Object> buildResponse(Novel novel, int chapters) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", String.format("成功导入 %d 章", chapters));
        result.put("novel_id", novel.getId());
        result.put("novel_title", novel.getTitle());
        result.put("chapter_count", chapters);
        return result;
    }

    /** 解析结果 */
    private static class ParsedChapter {
        String title;
        String content;

        ParsedChapter(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }
}
