package com.yunmo.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用核心配置 — 替代 Python config.py Settings
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunmo.app")
public class AppProperties {
    /** JWT 签名密钥 — 必须通过环境变量或配置文件覆盖 */
    private String secretKey;
    /** ChromaDB URL */
    private String chromaUrl = "http://localhost:8000";
    /** Redis URL（非必填） */
    private String redisUrl = "redis://localhost:6379/0";
    /** 单用户模式默认用户 ID */
    private String defaultUserId = "00000000-0000-0000-0000-000000000000";
    /** 章节生成最大重试次数 */
    private int maxRetries = 3;
    /** 上下文 token 预算 */
    private int contextTokenBudget = 46000;
}
