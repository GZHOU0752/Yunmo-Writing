package com.yunmo.llm.provider;

import com.yunmo.common.config.LLMProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 注册中心 — 替代 Python registry.py
 * 懒加载单例管理 + 应用关闭生命周期
 */
@Component
public class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);
    private final Map<String, LLMProvider> instances = new ConcurrentHashMap<>();
    private final LLMProperties properties;

    public ProviderRegistry(LLMProperties properties) {
        this.properties = properties;
    }

    private static final Set<String> PROVIDER_NAMES = Set.of("deepseek", "kimi", "qwen");

    /** 根据名称获取 Provider — 懒加载单例 */
    public LLMProvider get(String name) {
        return instances.computeIfAbsent(name.toLowerCase(), this::create);
    }

    /** 获取所有 Provider 名称 */
    public Set<String> listProviders() {
        return PROVIDER_NAMES;
    }

    /** 获取所有已初始化的 Provider 实例（懒加载） */
    public Map<String, LLMProvider> all() {
        PROVIDER_NAMES.forEach(this::get);
        return Collections.unmodifiableMap(instances);
    }

    private LLMProvider create(String name) {
        LLMProperties.Provider cfg = switch (name) {
            case "deepseek" -> properties.getDeepseek();
            case "kimi" -> properties.getKimi();
            case "qwen" -> properties.getQwen();
            default -> throw new IllegalArgumentException("未知 Provider: " + name);
        };

        log.info("初始化 LLM Provider: {} (model={}, baseUrl={})", name, cfg.getModel(), cfg.getBaseUrl());
        return switch (name) {
            case "deepseek" -> new DeepSeekProvider(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel());
            case "kimi" -> new KimiProvider(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel());
            case "qwen" -> new QwenProvider(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel());
            default -> throw new IllegalStateException("Unexpected: " + name);
        };
    }

    @PreDestroy
    public void cleanup() {
        log.info("清理 {} 个 LLM Provider 连接", instances.size());
        instances.values().forEach(LLMProvider::close);
        instances.clear();
    }
}
