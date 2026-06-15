package com.yunmo.service.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地文件向量存储 — 每本小说独立 JSON 文件，余弦相似度检索
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storeDir;
    private final ConcurrentHashMap<String, ReentrantLock> novelLocks = new ConcurrentHashMap<>();

    public VectorStoreService() {
        this.storeDir = Path.of("./data/vectors");
        try {
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建向量存储目录", e);
        }
    }

    private ReentrantLock lock(String novelId) {
        return novelLocks.computeIfAbsent(novelId, k -> new ReentrantLock());
    }

    public record VectorEntry(String id, String text, float[] vector, int chunkIndex,
                               String materialId, long timestamp) {}

    private Path novelFile(String novelId) {
        return storeDir.resolve(novelId + ".json");
    }

    public List<VectorEntry> load(String novelId) {
        return loadInternal(novelId);
    }

    private List<VectorEntry> loadInternal(String novelId) {
        Path file = novelFile(novelId);
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    file.toFile(), new TypeReference<List<Map<String, Object>>>() {});
            List<VectorEntry> entries = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                float[] vec = objectMapper.convertValue(m.get("vector"), float[].class);
                entries.add(new VectorEntry(
                        (String) m.get("id"),
                        (String) m.get("text"),
                        vec,
                        ((Number) m.get("chunkIndex")).intValue(),
                        (String) m.get("materialId"),
                        ((Number) m.get("timestamp")).longValue()
                ));
            }
            return entries;
        } catch (IOException e) {
            log.error("加载向量文件失败: {}", novelId, e);
            return new ArrayList<>();
        }
    }

    public void appendEntries(String novelId, List<VectorEntry> newEntries) {
        ReentrantLock lk = lock(novelId);
        lk.lock();
        try {
            List<VectorEntry> all = loadInternal(novelId);
            all.addAll(newEntries);
            saveInternal(novelId, all);
            log.info("向量追加: novel={}, 新增 {} 条, 总计 {} 条", novelId, newEntries.size(), all.size());
        } finally {
            lk.unlock();
        }
    }

    public void delete(String novelId) {
        ReentrantLock lk = lock(novelId);
        lk.lock();
        try {
            Files.deleteIfExists(novelFile(novelId));
        } catch (IOException e) {
            log.warn("删除向量文件失败: {}", novelId);
        } finally {
            lk.unlock();
        }
    }

    public void deleteByMaterial(String novelId, String materialId) {
        ReentrantLock lk = lock(novelId);
        lk.lock();
        try {
            List<VectorEntry> all = loadInternal(novelId);
            List<VectorEntry> filtered = all.stream()
                    .filter(e -> !materialId.equals(e.materialId))
                    .toList();
            saveInternal(novelId, filtered);
            log.info("删除素材向量: novel={}, material={}, 删除 {} 条", novelId, materialId,
                    all.size() - filtered.size());
        } finally {
            lk.unlock();
        }
    }

    public record SearchResult(VectorEntry entry, double similarity) {}

    /** 余弦相似度检索 */
    public List<SearchResult> search(String novelId, float[] queryVec, int topK, double threshold) {
        List<VectorEntry> entries = load(novelId);
        if (entries.isEmpty()) return List.of();

        var scored = entries.stream()
                .map(e -> new SearchResult(e, cosineSimilarity(queryVec, e.vector)))
                .filter(r -> r.similarity >= threshold)
                .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                .limit(topK)
                .toList();

        log.debug("向量检索: novel={}, topK={}, threshold={}, 命中 {} 条",
                novelId, topK, threshold, scored.size());
        return scored;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public boolean isEmpty(String novelId) {
        return !Files.exists(novelFile(novelId));
    }

    private void saveInternal(String novelId, List<VectorEntry> entries) {
        try {
            List<Map<String, Object>> serialized = entries.stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.id);
                m.put("text", e.text);
                m.put("vector", e.vector);
                m.put("chunkIndex", e.chunkIndex);
                m.put("materialId", e.materialId);
                m.put("timestamp", e.timestamp);
                return m;
            }).toList();
            objectMapper.writeValue(novelFile(novelId).toFile(), serialized);
        } catch (IOException e) {
            log.error("保存向量文件失败: {}", novelId, e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("向量存储已关闭");
    }
}
