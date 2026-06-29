package com.yunmo.llm.provider;

import com.yunmo.common.config.LLMProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatModel 工厂 — 基于 LangChain4j OpenAiChatModel
 * 替代自研 ProviderRegistry + AbstractOpenAIProvider + 3 个具体 Provider
 *
 * 所有 Provider（DeepSeek/Kimi/Qwen）均走 OpenAI 兼容协议，
 * 统一使用 LangChain4j 的 OpenAiChatModel / OpenAiStreamingChatModel
 */
@Component
public class ChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);
    private final LLMProperties properties;
    private final Map<String, ChatLanguageModel> syncModels = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatLanguageModel> streamModels = new ConcurrentHashMap<>();

    /** 已知的 Provider 名称 */
    private static final Set<String> PROVIDER_NAMES = Set.of("deepseek", "kimi", "qwen");

    public ChatModelFactory(LLMProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取同步 ChatModel（懒加载单例）
     * @param provider 提供商名称（deepseek/kimi/qwen）
     * @param model    模型名称（如 deepseek-v4-pro）
     */
    public ChatLanguageModel getSyncModel(String provider, String model) {
        String key = provider.toLowerCase() + ":" + model;
        return syncModels.computeIfAbsent(key, k -> createSyncModel(provider, model));
    }

    /**
     * 获取流式 ChatModel（懒加载单例）
     * @param provider 提供商名称
     * @param model    模型名称
     */
    public StreamingChatLanguageModel getStreamingModel(String provider, String model) {
        String key = provider.toLowerCase() + ":" + model;
        return streamModels.computeIfAbsent(key, k -> createStreamingModel(provider, model));
    }

    /** 获取所有已知 Provider 名称 */
    public Set<String> listProviders() {
        return PROVIDER_NAMES;
    }

    // ===== 内部创建方法 =====

    private ChatLanguageModel createSyncModel(String provider, String model) {
        LLMProperties.Provider cfg = getConfig(provider);
        String baseUrl = normalizeBaseUrl(cfg.getBaseUrl());

        log.info("创建同步 ChatModel: provider={}, model={}, baseUrl={}", provider, model, baseUrl);

        var builder = OpenAiChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(model)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(300))
                .logRequests(false)
                .logResponses(false);

        // Qwen 不支持同时设置 temperature 和 top_p
        if (!"qwen".equals(provider.toLowerCase())) {
            builder.topP(0.9);
        }

        return builder.build();
    }

    private StreamingChatLanguageModel createStreamingModel(String provider, String model) {
        LLMProperties.Provider cfg = getConfig(provider);
        String baseUrl = normalizeBaseUrl(cfg.getBaseUrl());

        log.info("创建流式 ChatModel: provider={}, model={}, baseUrl={}", provider, model, baseUrl);

        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(model)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(300));

        // Qwen 不支持同时设置 temperature 和 top_p
        if (!"qwen".equals(provider.toLowerCase())) {
            builder.topP(0.9);
        }

        return builder.build();
    }

    /** 获取 Provider 配置 */
    private LLMProperties.Provider getConfig(String provider) {
        return switch (provider.toLowerCase()) {
            case "deepseek" -> properties.getDeepseek();
            case "kimi" -> properties.getKimi();
            case "qwen" -> properties.getQwen();
            default -> throw new IllegalArgumentException("未知 Provider: " + provider);
        };
    }

    /**
     * 标准化 baseUrl
     * LangChain4j 的 OpenAiChatModel 会自动追加 /chat/completions，
     * 所以 baseUrl 应该只到 /v1 层级
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        // 移除尾部的 /chat/completions（如果有）
        if (baseUrl.endsWith("/chat/completions")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/chat/completions".length());
        }
        // 移除尾部斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    @PreDestroy
    public void cleanup() {
        log.info("清理 ChatModel 实例: sync={}, stream={}", syncModels.size(), streamModels.size());
        syncModels.clear();
        streamModels.clear();
    }
}
