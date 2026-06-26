package com.yunmo.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.common.config.LLMProperties;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 服务 — 调用阿里云百炼 DashScope Embedding API
 * 使用通义千问 text-embedding-v3 模型
 * 含本地缓存：相同文本的向量结果会被缓存，避免重复 API 调用
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    /** 通义千问 Embedding 模型 */
    private static final String EMBEDDING_MODEL = "text-embedding-v3";

    /** 嵌入向量缓存，最多 10000 条 */
    private static final int MAX_CACHE_SIZE = 10_000;
    private final ConcurrentHashMap<String, float[]> vectorCache = new ConcurrentHashMap<>();

    public EmbeddingService(LLMProperties llmProperties) {
        String baseUrl = llmProperties.getQwen().getBaseUrl();
        String apiKey = llmProperties.getQwen().getApiKey();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(120));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        log.info("Embedding 服务初始化: baseUrl={}, model={}", baseUrl, EMBEDDING_MODEL);
    }

    /**
     * 对单个文本生成向量
     */
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    /**
     * 批量生成向量（每批6个，控制 token 总量）
     * 优先从缓存获取，未命中再调 API
     */
    public List<float[]> embedBatch(List<String> texts) {
        // 1. 检查缓存
        List<float[]> results = new ArrayList<>();
        List<Integer> missedIdx = new ArrayList<>();
        List<String> missedTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String key = texts.get(i);
            float[] cached = vectorCache.get(key);
            if (cached != null) {
                results.add(cached);
            } else {
                results.add(null); // 占位
                missedIdx.add(i);
                missedTexts.add(key);
            }
        }

        if (missedTexts.isEmpty()) {
            log.debug("Embedding 全部命中缓存: {} 个文本", texts.size());
            return results;
        }

        int cacheHits = texts.size() - missedTexts.size();
        if (cacheHits > 0) {
            log.info("Embedding 缓存命中 {}/{}", cacheHits, texts.size());
        }

        // 2. 对未命中的文本调 API
        List<float[]> allEmbeddings = new ArrayList<>();
        int batchSize = 6;
        int maxTextLen = 6000;

        for (int i = 0; i < missedTexts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, missedTexts.size());
            List<String> batch = new ArrayList<>();
            for (int k = i; k < end; k++) {
                String t = missedTexts.get(k);
                batch.add(t.length() > maxTextLen ? t.substring(0, maxTextLen) : t);
            }
            try {
                List<float[]> batchResult = callEmbeddingApi(batch);
                allEmbeddings.addAll(batchResult);
                if ((i / batchSize) % 50 == 0) {
                    log.info("Embedding 进度: {}/{} 批次", i / batchSize + 1, (texts.size() + batchSize - 1) / batchSize);
                }
            } catch (Exception e) {
                log.error("Embedding 批次 {} 失败: {}", i / batchSize, e.getMessage());
                for (int k = i; k < end; k++) {
                    try {
                        List<float[]> single = callEmbeddingApi(List.of(missedTexts.get(k)));
                        allEmbeddings.addAll(single);
                    } catch (Exception ex) {
                        log.error("Embedding 单文本 {} 也失败: {}", k, ex.getMessage());
                        allEmbeddings.add(new float[1024]);
                    }
                }
            }
        }

        // 3. 写缓存 + 合并回原顺序
        for (int j = 0; j < missedTexts.size(); j++) {
            float[] vec = j < allEmbeddings.size() ? allEmbeddings.get(j) : new float[1024];
            cacheVector(missedTexts.get(j), vec);
            results.set(missedIdx.get(j), vec);
        }

        return results;
    }

    /** 写入缓存，超限时清理一半 */
    private void cacheVector(String text, float[] vector) {
        if (vectorCache.size() >= MAX_CACHE_SIZE) {
            // 随机淘汰 1/4 的缓存条目
            int toRemove = MAX_CACHE_SIZE / 4;
            var it = vectorCache.keySet().iterator();
            for (int i = 0; i < toRemove && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
            log.info("Embedding 缓存清理: 移除 {} 条，当前 {}", toRemove, vectorCache.size());
        }
        vectorCache.put(text, vector);
    }

    @SuppressWarnings("unchecked")
    private List<float[]> callEmbeddingApi(List<String> texts) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", EMBEDDING_MODEL);
        body.put("input", texts);

        String responseJson = webClient.post()
                .uri("/embeddings")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), resp ->
                    resp.bodyToMono(String.class).doOnNext(errBody ->
                        log.error("Embedding API 错误响应: status={}, body={}",
                            resp.statusCode(), errBody.length() > 500
                                ? errBody.substring(0, 500) : errBody)
                    ).then(reactor.core.publisher.Mono.error(
                        new RuntimeException("Embedding API error: " + resp.statusCode())
                    ))
                )
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(120));

        Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

        List<float[]> embeddings = new ArrayList<>();
        if (data != null) {
            for (Map<String, Object> item : data) {
                List<Double> embedding = (List<Double>) item.get("embedding");
                if (embedding != null) {
                    float[] vec = new float[embedding.size()];
                    for (int j = 0; j < embedding.size(); j++) {
                        vec[j] = embedding.get(j).floatValue();
                    }
                    embeddings.add(vec);
                }
            }
        }

        log.info("Embedding 完成: {} 个文本 → {} 个向量 (维度={})",
                texts.size(), embeddings.size(),
                embeddings.isEmpty() ? 0 : embeddings.get(0).length);
        return embeddings;
    }
}
