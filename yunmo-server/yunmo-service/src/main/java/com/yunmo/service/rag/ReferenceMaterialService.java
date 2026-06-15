package com.yunmo.service.rag;

import com.yunmo.domain.entity.ReferenceMaterial;
import com.yunmo.domain.repository.ReferenceMaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ReferenceMaterialService(ReferenceMaterialRepository repo,
                                     DocumentChunker chunker,
                                     EmbeddingService embeddingService,
                                     VectorStoreService vectorStore) {
        this.repo = repo;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
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
    @Transactional
    public ReferenceMaterial upload(String novelId, String fileName, String content) {
        // 1. 创建素材记录
        ReferenceMaterial material = new ReferenceMaterial();
        material.setNovelId(novelId);
        material.setFileName(fileName);
        material.setFileSize((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        material.setStatus("indexing");
        material = repo.save(material);

        try {
            // 2. 文本分块
            List<DocumentChunker.Chunk> chunks = chunker.chunk(content);
            log.info("素材分块完成: {} → {} 块", fileName, chunks.size());

            // 3. 向量化（阿里云百炼 DashScope）
            List<String> texts = chunks.stream().map(DocumentChunker.Chunk::text).toList();
            List<float[]> vectors = embeddingService.embedBatch(texts);

            // 4. 存入向量存储
            List<VectorStoreService.VectorEntry> entries = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunker.Chunk chunk = chunks.get(i);
                float[] vector = i < vectors.size() ? vectors.get(i) : new float[0];
                entries.add(new VectorStoreService.VectorEntry(
                        UUID.randomUUID().toString(),
                        chunk.text(),
                        vector,
                        chunk.index(),
                        material.getId(),
                        System.currentTimeMillis()
                ));
            }

            vectorStore.appendEntries(novelId, entries);

            // 5. 更新素材状态
            material.setChunkCount(chunks.size());
            material.setStatus("ready");
            repo.save(material);

            log.info("素材索引完成: {}", fileName);
            return material;
        } catch (Exception e) {
            log.error("素材索引失败: {}", fileName, e);
            material.setStatus("error");
            repo.save(material);
            throw new RuntimeException("素材索引失败: " + e.getMessage(), e);
        }
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
}
