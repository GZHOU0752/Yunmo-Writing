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

/**
 * Embedding 服务 — 调用阿里云百炼 DashScope Embedding API
 * 使用通义千问 text-embedding-v3 模型
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    /** 通义千问 Embedding 模型 */
    private static final String EMBEDDING_MODEL = "text-embedding-v3";

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
     * 批量生成向量（每批最多16个）
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> allEmbeddings = new ArrayList<>();
        int batchSize = 16;

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            try {
                List<float[]> batchResult = callEmbeddingApi(batch);
                allEmbeddings.addAll(batchResult);
            } catch (Exception e) {
                log.error("Embedding 批次 {} 失败: {}", i / batchSize, e.getMessage());
                for (int j = 0; j < batch.size(); j++) {
                    allEmbeddings.add(new float[1024]); // text-embedding-v3 默认 1024 维
                }
            }
        }

        return allEmbeddings;
    }

    @SuppressWarnings("unchecked")
    private List<float[]> callEmbeddingApi(List<String> texts) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", EMBEDDING_MODEL);
        body.put("input", texts);
        // 通义千问 embedding 参数
        Map<String, String> params = new LinkedHashMap<>();
        params.put("text_type", "document");
        body.put("parameters", params);

        String responseJson = webClient.post()
                .uri("/embeddings")
                .bodyValue(body)
                .retrieve()
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
