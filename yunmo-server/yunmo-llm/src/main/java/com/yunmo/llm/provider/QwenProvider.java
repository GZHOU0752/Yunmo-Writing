package com.yunmo.llm.provider;

/**
 * 通义千问 Provider — 轻量检查模型 (Guardian/Custodian)
 * <p>
 * 注意：Qwen 的 OpenAI 兼容 API 不允许同时设置 temperature 和 top_p，
 * 否则返回 400 错误。因此通过 supportsTopPWithTemperature() 禁用 top_p。
 */
public class QwenProvider extends AbstractOpenAIProvider {

    public QwenProvider(String baseUrl, String apiKey, String model) {
        super(baseUrl, apiKey, model);
    }

    @Override
    public boolean supportsTools() {
        return false;
    }

    /** Qwen 兼容 API 不允许 temperature 和 top_p 同时设置 */
    @Override
    protected boolean supportsTopPWithTemperature() {
        return false;
    }

    @Override
    public String providerName() {
        return "qwen";
    }
}
