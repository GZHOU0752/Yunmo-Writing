package com.yunmo.common.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 提供商配置 — 替代 Python config.py LLM 相关设置
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunmo.llm")
public class LLMProperties {

    private static final Logger log = LoggerFactory.getLogger(LLMProperties.class);

    private Provider deepseek = new Provider("https://api.deepseek.com", "deepseek-v4-pro", "");
    private Provider kimi = new Provider("https://api.moonshot.cn/v1", "moonshot-v1-8k", "");
    private Provider qwen = new Provider("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "");

    @PostConstruct
    public void validate() {
        checkKey("deepseek", deepseek);
        checkKey("kimi", kimi);
        checkKey("qwen", qwen);
    }

    private void checkKey(String name, Provider provider) {
        if (provider == null) return;
        String key = provider.getApiKey();
        if (key == null || key.isBlank()) {
            log.warn("[LLM] ⚠️ {} API Key 未配置（环境变量: {}_API_KEY），相关 LLM 功能将不可用",
                    name.toUpperCase(), name.toUpperCase());
        } else {
            log.info("[LLM] ✅ {} API Key 已配置 ({}...{})", name.toUpperCase(),
                    key.substring(0, Math.min(4, key.length())),
                    key.substring(Math.max(0, key.length() - 4)));
        }
    }

    @Data
    public static class Provider {
        private String baseUrl;
        private String model;
        private String apiKey;

        public Provider() {}
        public Provider(String baseUrl, String model, String apiKey) {
            this.baseUrl = baseUrl;
            this.model = model;
            this.apiKey = apiKey;
        }
    }
}
