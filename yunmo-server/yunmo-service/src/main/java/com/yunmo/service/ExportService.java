package com.yunmo.service;

import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.Novel;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.NovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多格式导出服务 — 替代 Python export_service.py
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private final NovelRepository novelRepo;
    private final ChapterRepository chapterRepo;

    public ExportService(NovelRepository novelRepo, ChapterRepository chapterRepo) {
        this.novelRepo = novelRepo;
        this.chapterRepo = chapterRepo;
    }

    /** 导出 Markdown */
    public String exportMarkdown(String novelId) {
        Novel novel = novelRepo.findById(novelId).orElseThrow();
        List<Chapter> chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(novel.getTitle()).append("\n\n");
        sb.append("> 类型: ").append(novel.getGenreId())
                .append(" | 总字数: ").append(novel.getWordCount()).append("\n\n");
        sb.append("---\n\n");

        for (var ch : chapters) {
            sb.append("## 第").append(ch.getChapterNumber()).append("章");
            if (ch.getTitle() != null && !ch.getTitle().isEmpty()) {
                sb.append(" ").append(ch.getTitle());
            }
            sb.append("\n\n");
            if (ch.getContent() != null) {
                sb.append(ch.getContent()).append("\n\n");
            }
            sb.append("---\n\n");
        }

        log.info("导出 Markdown: {} -> {} 字", novel.getTitle(), sb.length());
        return sb.toString();
    }

    /** 导出纯文本 */
    public String exportTxt(String novelId) {
        Novel novel = novelRepo.findById(novelId).orElseThrow();
        List<Chapter> chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);

        StringBuilder sb = new StringBuilder();
        sb.append(novel.getTitle()).append("\n");
        sb.append("=".repeat(40)).append("\n\n");

        for (var ch : chapters) {
            sb.append("第").append(ch.getChapterNumber()).append("章");
            if (ch.getTitle() != null && !ch.getTitle().isEmpty()) {
                sb.append(" ").append(ch.getTitle());
            }
            sb.append("\n\n");
            if (ch.getContent() != null) {
                sb.append(ch.getContent()).append("\n\n");
            }
        }

        return sb.toString();
    }

    /** 导出 HTML（直接构建，避免正则转换 Markdown 的不稳定性） */
    public String exportHtml(String novelId) {
        Novel novel = novelRepo.findById(novelId).orElseThrow();
        List<Chapter> chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>").append(escapeHtml(novel.getTitle())).append("</title>\n");
        sb.append("<style>\n");
        sb.append("body { max-width: 800px; margin: 0 auto; padding: 2em; ");
        sb.append("font-family: \"Noto Serif SC\", serif; line-height: 1.8; }\n");
        sb.append("h1 { text-align: center; }\n");
        sb.append("h2 { margin-top: 2em; border-bottom: 1px solid #ccc; }\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<h1>").append(escapeHtml(novel.getTitle())).append("</h1>\n");
        sb.append("<p>类型: ").append(escapeHtml(novel.getGenreId()))
                .append(" | 总字数: ").append(novel.getWordCount()).append("</p>\n");

        for (var ch : chapters) {
            sb.append("<h2>第").append(ch.getChapterNumber()).append("章");
            if (ch.getTitle() != null && !ch.getTitle().isEmpty()) {
                sb.append(" ").append(escapeHtml(ch.getTitle()));
            }
            sb.append("</h2>\n");
            if (ch.getContent() != null) {
                // 将段落换行转为 HTML 段落
                String[] paragraphs = ch.getContent().split("\n\n");
                for (String p : paragraphs) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) {
                        sb.append("<p>").append(escapeHtml(trimmed).replace("\n", "<br>")).append("</p>\n");
                    }
                }
            }
        }

        sb.append("</body>\n</html>");
        log.info("导出 HTML: {} -> {} 章", novel.getTitle(), chapters.size());
        return sb.toString();
    }

    /** HTML 实体转义 */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
