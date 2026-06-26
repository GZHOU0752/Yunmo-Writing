package com.yunmo.llm.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.common.dto.LLMResponse;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容 API 抽象基类
 * DeepSeek/Kimi/Qwen 都走 OpenAI 兼容协议，共用此基类
 */
public abstract class AbstractOpenAIProvider implements LLMProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final WebClient webClient;
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final String defaultModel;

    protected AbstractOpenAIProvider(String baseUrl, String apiKey, String defaultModel) {
        this.defaultModel = defaultModel;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(300));  // 300s: qwen-max 长篇润色可能超120s

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public LLMResponse generate(List<LLMMessage> messages, LLMConfig config) {
        Map<String, Object> body = buildRequestBody(messages, config, false);
        int timeout = config.timeoutSeconds() > 0 ? config.timeoutSeconds() : 300;

        // 清除线程残留中断标志
        if (Thread.interrupted()) {
            log.warn("{} 检测到残留中断标志，已清除", providerName());
        }

        // 用 CompletableFuture 替代 Mono.block()。
        // 原因：Mono.block() 基于 CountDownLatch.await()，Reactor 取消链会中断 boundedElastic 线程，
        //       导致 block() 抛 InterruptedException。改用 CompletableFuture 后，LLM HTTP 调用完全
        //       异步进行，等待线程被打断时我们忽略中断信号继续等——LLM 请求本身不受影响。
        CompletableFuture<String> future = new CompletableFuture<>();
        webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(future::completeExceptionally)
                .subscribe(future::complete);

        String responseJson = null;
        long deadline = System.currentTimeMillis() + timeout * 1000L;
        while (responseJson == null && System.currentTimeMillis() < deadline) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                responseJson = future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // Reactor 取消链打断了等待线程，但 LLM HTTP 调用仍在进行。
                // 清除标志后继续等待——我们不关心取消信号，只关心 LLM 是否返回。
                Thread.interrupted();
                log.trace("{} 等待LLM响应时被打断，继续等待", providerName());
            } catch (java.util.concurrent.TimeoutException e) {
                break; // 总体超时
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.error("{} API 调用失败", providerName(), cause);
                throw new RuntimeException(providerName() + " API 调用失败: " + cause.getMessage(), cause);
            }
        }

        if (responseJson == null) {
            throw new RuntimeException(providerName() + " API 响应超时 (" + timeout + "s)");
        }
        return parseResponse(responseJson, config.model());
    }

    @Override
    public Flux<String> generateStream(List<LLMMessage> messages, LLMConfig config) {
        Map<String, Object> body = buildRequestBody(messages, config, true);
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .concatMap(chunk -> Flux.fromArray(chunk.split("\n")))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .concatMap(this::extractContent);
    }

    /** 从 SSE 行中提取 token 内容，兼容 data: 前缀和裸 JSON 两种格式 */
    private reactor.core.publisher.Mono<String> extractContent(String line) {
        if ("[DONE]".equals(line)) return reactor.core.publisher.Mono.empty();
        String json = line;
        if (line.startsWith("data:")) {
            json = line.substring(5).strip();
            if ("[DONE]".equals(json) || json.isEmpty()) return reactor.core.publisher.Mono.empty();
        }
        if (!json.startsWith("{")) return reactor.core.publisher.Mono.empty();
        String content = parseStreamChunk(json);
        return content != null ? reactor.core.publisher.Mono.just(content) : reactor.core.publisher.Mono.empty();
    }

    @Override
    public int countTokens(List<LLMMessage> messages, String model) {
        return messages.stream()
                .mapToInt(m -> m.content().length() / 4)
                .sum();
    }

    /**
     * 子类可覆盖此方法来控制 sampling 参数行为。
     * Qwen 的 OpenAI 兼容 API 不允许同时设置 temperature 和 top_p，会返回 400。
     */
    protected boolean supportsTopPWithTemperature() {
        return true;
    }

    protected Map<String, Object> buildRequestBody(
        List<LLMMessage> messages, LLMConfig config, boolean stream
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model() != null ? config.model() : defaultModel);
        body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());
        body.put("max_tokens", config.maxTokens());
        body.put("temperature", config.temperature());
        // Qwen 兼容 API 不允许同时设置 temperature 和 top_p
        if (supportsTopPWithTemperature()) {
            body.put("top_p", config.topP());
        }
        body.put("stream", stream);
        // 频率惩罚和存在惩罚 — 减少重复用词，增加词汇多样性
        if (config.frequencyPenalty() > 0) body.put("frequency_penalty", config.frequencyPenalty());
        if (config.presencePenalty() > 0) body.put("presence_penalty", config.presencePenalty());
        return body;
    }

    @SuppressWarnings("unchecked")
    protected LLMResponse parseResponse(String json, String model) {
        try {
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return new LLMResponse("", model, Collections.emptyMap(), "stop");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message != null ? (String) message.get("content") : "";
            Map<String, Object> usageMap = (Map<String, Object>) root.get("usage");
            Map<String, Integer> usage = new HashMap<>();
            if (usageMap != null) {
                for (String key : List.of("prompt_tokens", "completion_tokens", "total_tokens")) {
                    Object val = usageMap.get(key);
                    if (val instanceof Number n) {
                        usage.put(key, n.intValue());
                    }
                }
            }
            String finishReason = (String) choices.get(0).get("finish_reason");
            return new LLMResponse(content, (String) root.get("model"), usage, finishReason);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 LLM 响应失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    protected String parseStreamChunk(String json) {
        try {
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
            if (delta == null) return null;
            Object content = delta.get("content");
            return content != null ? content.toString() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
