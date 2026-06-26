package com.yunmo.service.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分块器 — 章节感知的分块策略。
 *
 * <h3>分块优先级</h3>
 * <ol>
 *   <li><b>章节边界</b>：检测"第X章/Chapter/Section/卷"等标记，按章节切分（最强的语义信号，零成本）</li>
 *   <li><b>段落聚合</b>：章节内按段落边界聚合到目标窗口大小，带重叠</li>
 *   <li><b>超长回退</b>：单段落超长时按固定窗口切分</li>
 * </ol>
 *
 * <p>对于用户上传的整本小说，章节检测能保证同一章的内容不会被切到不同 chunk 中；
 * 对于设定文档/角色卡等短文，段落聚合已经足够（它们本身就是按主题段落组织的）。</p>
 */
@Component
public class DocumentChunker {

    private static final int DEFAULT_CHUNK_SIZE = 1000;  // 字符
    private static final int DEFAULT_OVERLAP = 200;       // 重叠字符

    /** 章节标记正则 — 匹配中文/英文/数字章节标题 */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*" +
            "(?:第\\s*[0-9零一二三四五六七八九十百千]+\\s*[章回卷节篇]" +     // 中文：第一章、第二回
            "|Chapter\\s+\\d+" +                                          // 英文：Chapter 1
            "|Part\\s+\\d+" +                                             // Part 1
            "|Section\\s+\\d+" +                                          // Section 1
            "|第\\s*[0-9零一二三四五六七八九十百千]+\\s*[部季辑]" +        // 第一部、第二季
            "|VOLUME\\s+\\d+" +                                           // VOLUME 1
            "|#[1-9]\\d*\\s" +                                            // Markdown 标题：#1 或 ##2
            ")",
            Pattern.CASE_INSENSITIVE);

    public record Chunk(int index, String text, int startPos, int endPos, String chapterHint) {
        /** 便捷构造（无章节提示） */
        public Chunk(int index, String text, int startPos, int endPos) {
            this(index, text, startPos, endPos, null);
        }

        /** 是否检测到章节边界 */
        public boolean hasChapterHint() {
            return chapterHint != null && !chapterHint.isEmpty();
        }
    }

    /**
     * 对文本进行章节感知分块。
     */
    public List<Chunk> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<Chunk> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) return new ArrayList<>();

        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");

        // 1. 检测章节边界
        List<ChapterSegment> chapters = splitByChapters(normalized);

        // 2. 每个章节内按段落聚合分块
        List<Chunk> allChunks = new ArrayList<>();
        int globalIndex = 0;

        for (ChapterSegment ch : chapters) {
            List<Chunk> chapterChunks = chunkParagraphs(
                    ch.text, chunkSize, overlap, globalIndex, ch.title);
            allChunks.addAll(chapterChunks);
            globalIndex += chapterChunks.size();
        }

        return allChunks;
    }

    // ==================== 章节切分 ====================

    private record ChapterSegment(String title, String text) {}

    /**
     * 按章节标记切分文本。如果没检测到章节标记，整篇作为一个段。
     */
    private List<ChapterSegment> splitByChapters(String text) {
        List<ChapterSegment> chapters = new ArrayList<>();

        var matcher = CHAPTER_PATTERN.matcher(text);
        int lastEnd = 0;
        String lastTitle = null;

        while (matcher.find()) {
            int matchStart = matcher.start();
            // 跳过行中间的匹配（只接受行首或仅前导空格的匹配）
            if (matchStart > 0 && text.charAt(matchStart - 1) != '\n') {
                continue;
            }

            // 前导空白 + 标题行 = 一章的开始
            String title = matcher.group().trim();

            if (lastEnd < matchStart && lastTitle != null) {
                // 保存上一章
                String body = text.substring(lastEnd, matchStart).trim();
                if (!body.isEmpty()) {
                    chapters.add(new ChapterSegment(lastTitle, body));
                }
            }

            lastEnd = matcher.end();
            lastTitle = title;
        }

        // 最后一章 / 整个文本
        String remaining = text.substring(lastEnd).trim();
        if (lastTitle != null && !remaining.isEmpty()) {
            chapters.add(new ChapterSegment(lastTitle, remaining));
        } else if (lastTitle == null && !remaining.isEmpty()) {
            // 没检测到任何章节标记
            chapters.add(new ChapterSegment(null, remaining));
        }

        return chapters;
    }

    // ==================== 段落聚合分块 ====================

    /**
     * 在章节内按段落边界聚合到目标窗口大小。
     */
    private List<Chunk> chunkParagraphs(String text, int chunkSize, int overlap,
                                        int startIndex, String chapterHint) {
        List<Chunk> chunks = new ArrayList<>();

        String[] paragraphs = text.split("\n\n+");
        StringBuilder buffer = new StringBuilder();
        int bufferStart = 0;
        int pos = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int paraLen = trimmed.length();

            // 超长段落：固定窗口回退
            if (paraLen > chunkSize) {
                // 先输出当前 buffer
                if (buffer.length() > 0) {
                    chunks.add(buildChunk(startIndex, buffer, bufferStart, chapterHint));
                    startIndex++;
                    buffer = new StringBuilder();
                }
                // 按固定窗口切分超长段落
                int pStart = pos;
                int chunkStart = 0;
                while (chunkStart < paraLen) {
                    int end = Math.min(chunkStart + chunkSize, paraLen);
                    String piece = trimmed.substring(chunkStart, end);
                    chunks.add(new Chunk(startIndex++, piece, pStart + chunkStart, pStart + end, chapterHint));
                    chunkStart += chunkSize - overlap;
                }
                pos += paraLen + 2; // +2 for \n\n
                bufferStart = pos;
                continue;
            }

            // 如果当前段加入会超窗口，先输出当前块
            if (buffer.length() + paraLen + 2 > chunkSize && buffer.length() > 0) {
                chunks.add(buildChunk(startIndex, buffer, bufferStart, chapterHint));
                startIndex++;

                // 保留重叠
                String overlapText = buffer.length() > overlap
                        ? buffer.substring(buffer.length() - overlap)
                        : buffer.toString();
                buffer = new StringBuilder(overlapText);
                bufferStart = pos - overlapText.length();
            }

            if (buffer.length() > 0) buffer.append("\n\n");
            buffer.append(trimmed);
            pos += paraLen + 2;
        }

        // 最后一个块
        if (buffer.length() > 0) {
            chunks.add(buildChunk(startIndex, buffer, bufferStart, chapterHint));
        }

        return chunks;
    }

    private Chunk buildChunk(int index, StringBuilder buffer, int startPos, String chapterHint) {
        String t = buffer.toString().trim();
        return new Chunk(index, t, startPos, startPos + t.length(), chapterHint);
    }
}
