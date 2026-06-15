package com.yunmo.llm.provider;

/**
 * Kimi (月之暗面) Provider — 审校模型 (Inspector)
 */
public class KimiProvider extends AbstractOpenAIProvider {

    public KimiProvider(String baseUrl, String apiKey, String model) {
        super(baseUrl, apiKey, model);
    }

    @Override
    public boolean supportsTools() {
        return true;
    }

    @Override
    public String providerName() {
        return "kimi";
    }
}
