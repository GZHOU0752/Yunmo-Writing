package com.yunmo.service.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器 — 段落边界感知的固定窗口分块
 */
@Component
public class DocumentChunker {

    private static final int DEFAULT_CHUNK_SIZE = 1000;  // 字符
    private static final int DEFAULT_OVERLAP = 200;       // 重叠字符

    public record Chunk(int index, String text, int startPos, int endPos) {}

    /**
     * 将文本切分为重叠块，优先在段落边界切分
     */
    public List<Chunk> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<Chunk> chunk(String text, int chunkSize, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        // 按段落切分
        String[] paragraphs = text.split("\\n\\n+");
        StringBuilder buffer = new StringBuilder();
        int startPos = 0;
        int index = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // 如果当前段落加入后会超过块大小，输出当前块
            if (buffer.length() + trimmed.length() > chunkSize && buffer.length() > 0) {
                chunks.add(new Chunk(index++, buffer.toString().trim(), startPos,
                        startPos + buffer.length()));
                // 保留重叠部分
                String overlapText = buffer.length() > overlap
                        ? buffer.substring(buffer.length() - overlap)
                        : buffer.toString();
                buffer = new StringBuilder(overlapText);
                startPos = startPos + buffer.length() - overlapText.length();
            }

            if (buffer.length() > 0) buffer.append("\n\n");
            buffer.append(trimmed);
        }

        // 最后一个块
        if (buffer.length() > 0) {
            chunks.add(new Chunk(index, buffer.toString().trim(), startPos,
                    startPos + buffer.length()));
        }

        return chunks;
    }
}
