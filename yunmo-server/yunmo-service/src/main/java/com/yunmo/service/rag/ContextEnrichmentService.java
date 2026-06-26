package com.yunmo.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 写作上下文增强服务 — 在生成章节时注入 RAG 检索结果
 */
@Service
public class ContextEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ContextEnrichmentService.class);

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.6;  // 中文Embedding相似度偏低，0.6更合理

    public ContextEnrichmentService(EmbeddingService embeddingService,
                                     VectorStoreService vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    public String retrieve(String novelId, String context, int topK, double threshold) {
        try {
            float[] queryVec = embeddingService.embed(context);
            if (queryVec.length == 0) return "";

            List<VectorStoreService.SearchResult> results = vectorStore.search(
                    novelId, queryVec, topK, threshold);

            if (results.isEmpty()) {
                log.debug("RAG 检索无命中: novel={}", novelId);
                return "";
            }

            String formatted = formatForPrompt(results);
            log.info("RAG 检索命中: novel={}, {} 条片段", novelId, results.size());
            return formatted;
        } catch (Exception e) {
            log.warn("RAG 检索失败: {}", e.getMessage());
            return "";
        }
    }

    public String retrieve(String novelId, String context) {
        return retrieve(novelId, context, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
    }

    public boolean hasReference(String novelId) {
        return !vectorStore.isEmpty(novelId);
    }

    private String formatForPrompt(List<VectorStoreService.SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 参考素材（从你的素材库中检索到的相关内容，可参考其风格和写法）\n\n");

        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append(String.format("【参考片段 %d】（相似度：%.0f%%）\n",
                    i + 1, r.similarity() * 100));
            String text = r.entry().text();
            if (text.length() > 800) {
                text = text.substring(0, 800) + "...";
            }
            sb.append(text).append("\n\n");
        }

        sb.append("请参考以上素材的文风和写作手法，但不要直接复制内容。\n");
        return sb.toString();
    }
}
