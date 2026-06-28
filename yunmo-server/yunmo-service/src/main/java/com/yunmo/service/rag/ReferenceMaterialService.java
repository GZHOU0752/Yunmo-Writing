package com.yunmo.service.rag;

import com.yunmo.domain.entity.ReferenceMaterial;
import com.yunmo.domain.repository.ReferenceMaterialRepository;
import com.yunmo.service.style.StyleAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * 参考素材管理服务
 */
@Service
public class ReferenceMaterialService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceMaterialService.class);
    private final ReferenceMaterialRepository repo;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;
    private final StyleAnalysisService styleAnalysisService;

    public ReferenceMaterialService(ReferenceMaterialRepository repo,
                                     DocumentChunker chunker,
                                     EmbeddingService embeddingService,
                                     VectorStoreService vectorStore,
                                     StyleAnalysisService styleAnalysisService) {
        this.repo = repo;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.styleAnalysisService = styleAnalysisService;
    }

    /** 列出小说的所有素材 */
    public List<ReferenceMaterial> list(String novelId) {
        return repo.findByNovelIdOrderByCreatedAtDesc(novelId);
    }

    /**
     * 上传并索引素材
     *
     * @param novelId 小说 ID
     * @param fileName 原始文件名
     * @param content  txt 文件内容
     * @return 创建的 ReferenceMaterial
     */
    public ReferenceMaterial upload(String novelId, String fileName, String content) {
        // 1. 先保存素材记录（独立事务，嵌入失败也不回滚）
        ReferenceMaterial material = new ReferenceMaterial();
        material.setNovelId(novelId);
        material.setFileName(fileName);
        material.setFileSize((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        material.setStatus("indexing");
        material.setChunkCount(0);
        material = repo.save(material);
        final String materialId = material.getId();

        // 2. 异步嵌入（失败不影响素材记录保存）
        try {
            List<DocumentChunker.Chunk> chunks = chunker.chunk(content);
            log.info("素材分块完成: {} → {} 块", fileName, chunks.size());

            // 重新获取受管entity，避免脱管entity merge冲突
            ReferenceMaterial managed = repo.findById(materialId).orElseThrow();
            managed.setChunkCount(chunks.size());
            repo.save(managed);

            List<String> texts = chunks.stream().map(DocumentChunker.Chunk::text).toList();
            List<float[]> vectors = embeddingService.embedBatch(texts);

            List<VectorStoreService.VectorEntry> entries = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunker.Chunk chunk = chunks.get(i);
                float[] vector = i < vectors.size() ? vectors.get(i) : new float[1024];
                entries.add(new VectorStoreService.VectorEntry(
                        UUID.randomUUID().toString(),
                        chunk.text(),
                        vector,
                        chunk.index(),
                        materialId,
                        System.currentTimeMillis()
                ));
            }
            vectorStore.appendEntries(novelId, entries);

            managed = repo.findById(materialId).orElseThrow();
            managed.setStatus("ready");
            repo.save(managed);
            log.info("素材索引完成: {}", fileName);

            // 3. 自动提取文风特征 — 上传素材即分析，无需手动触发
            try {
                styleAnalysisService.analyzeStyle(novelId, content)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                        result -> log.info("文风自动分析完成: novel={}, file={}", novelId, fileName),
                        err -> log.warn("文风自动分析失败: file={}, error={}", fileName, err.getMessage())
                    );
            } catch (Exception e) {
                log.warn("文风自动分析触发失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("素材嵌入失败（素材已保存，可稍后重新索引）: {}", fileName, e);
            ReferenceMaterial managed = repo.findById(materialId).orElseThrow();
            managed.setStatus("error");
            repo.save(managed);
        }

        return material;
    }

    /** 删除素材及其向量 */
    @Transactional
    public void delete(String materialId) {
        ReferenceMaterial material = repo.findById(materialId).orElse(null);
        if (material == null) return;

        vectorStore.deleteByMaterial(material.getNovelId(), materialId);
        repo.delete(material);
        log.info("素材已删除: {}", material.getFileName());
    }

    /** 获取小说的素材数量 */
    public long count(String novelId) {
        return repo.countByNovelId(novelId);
    }

    /**
     * 获取当前章节应激活的素材（基于触发规则）
     * - MANUAL: 不自动激活
     * - AUTO: 始终激活（遵守冷却）
     * - KEYWORD: 章节内容匹配触发关键词时激活（遵守冷却）
     */
    public List<ReferenceMaterial> getActiveMaterials(String novelId, int chapterNumber, String chapterContent) {
        List<ReferenceMaterial> all = repo.findByNovelIdOrderByCreatedAtDesc(novelId);
        List<ReferenceMaterial> active = new ArrayList<>();

        for (ReferenceMaterial m : all) {
            if (!"ready".equals(m.getStatus())) continue;

            String mode = m.getTriggerMode() != null ? m.getTriggerMode() : "MANUAL";

            // 检查冷却
            if (m.getCooldownChapters() != null && m.getCooldownChapters() > 0
                && m.getLastActivatedChapter() != null) {
                if (chapterNumber - m.getLastActivatedChapter() < m.getCooldownChapters()) {
                    continue;
                }
            }

            boolean shouldActivate = switch (mode) {
                case "AUTO" -> true;
                case "KEYWORD" -> matchKeywords(m, chapterContent);
                default -> false; // MANUAL
            };

            if (shouldActivate) {
                active.add(m);
                // 更新最后激活章节
                m.setLastActivatedChapter(chapterNumber);
                repo.save(m);
            }
        }

        // 按优先级降序排列
        active.sort((a, b) -> Integer.compare(
            b.getPriority() != null ? b.getPriority() : 0,
            a.getPriority() != null ? a.getPriority() : 0));
        return active;
    }

    /** 检查素材的关键词是否在内容中命中 */
    private boolean matchKeywords(ReferenceMaterial material, String content) {
        String keywords = material.getTriggerKeywords();
        if (keywords == null || keywords.isBlank()) return false;
        if (content == null || content.isEmpty()) return false;

        String lowerContent = content.toLowerCase();
        for (String kw : keywords.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty() && lowerContent.contains(trimmed.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** 更新素材的触发配置 */
    public ReferenceMaterial updateTriggerConfig(String materialId, String triggerMode,
                                                  String triggerKeywords,
                                                  Integer cooldownChapters, Integer priority) {
        ReferenceMaterial m = repo.findById(materialId).orElse(null);
        if (m == null) return null;
        if (triggerMode != null) m.setTriggerMode(triggerMode);
        if (triggerKeywords != null) m.setTriggerKeywords(triggerKeywords);
        if (cooldownChapters != null) m.setCooldownChapters(cooldownChapters);
        if (priority != null) m.setPriority(priority);
        return repo.save(m);
    }
}
