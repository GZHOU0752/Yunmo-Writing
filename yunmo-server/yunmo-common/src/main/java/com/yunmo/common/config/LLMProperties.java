package com.yunmo.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 提供商配置 — 替代 Python config.py LLM 相关设置
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunmo.llm")
public class LLMProperties {
    private Provider deepseek = new Provider("https://api.deepseek.com", "deepseek-v4-pro", "");
    private Provider kimi = new Provider("https://api.moonshot.cn/v1", "moonshot-v1-8k", "");
    private Provider qwen = new Provider("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "");

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
