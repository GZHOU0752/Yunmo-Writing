package com.yunmo.llm.provider;

/**
 * DeepSeek Provider — 主力模型 (Writer/Architect/Supervisor)
 */
public class DeepSeekProvider extends AbstractOpenAIProvider {

    public DeepSeekProvider(String baseUrl, String apiKey, String model) {
        super(baseUrl, apiKey, model);
    }

    @Override
    public boolean supportsTools() {
        return true;
    }

    @Override
    public String providerName() {
        return "deepseek";
    }
}
