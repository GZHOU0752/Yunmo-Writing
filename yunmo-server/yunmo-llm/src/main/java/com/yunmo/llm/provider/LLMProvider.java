package com.yunmo.llm.provider;

import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.common.dto.LLMResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LLM Provider 接口 — 替代 Python BaseLLMProvider
 */
public interface LLMProvider {

    /** 同步生成 */
    LLMResponse generate(List<LLMMessage> messages, LLMConfig config);

    /** 流式生成 — WebFlux Flux<String> token 流 */
    Flux<String> generateStream(List<LLMMessage> messages, LLMConfig config);

    /** Token 计数 */
    int countTokens(List<LLMMessage> messages, String model);

    /** 是否支持 Tool/Function Calling */
    boolean supportsTools();

    /** 提供商名称 */
    String providerName();

    /** 清理资源 */
    default void close() {}
}
