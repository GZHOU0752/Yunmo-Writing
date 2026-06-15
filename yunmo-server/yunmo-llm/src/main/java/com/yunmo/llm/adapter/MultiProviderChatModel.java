package com.yunmo.llm.adapter;

import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.common.dto.LLMResponse;
import com.yunmo.llm.provider.LLMProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 多 Provider ChatModel — 替代 Python NovelWriterChatModel
 * 将自建 LLMProvider 包装为统一调用接口
 */
public class MultiProviderChatModel {

    private final LLMProvider provider;
    private final String model;

    public MultiProviderChatModel(LLMProvider provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    /**
     * 同步生成 — Pipeline Stage 内部调用
     */
    public Response<AiMessage> generate(ChatMessage... messages) {
        return generate(List.of(messages));
    }

    /**
     * 同步生成 — 批量消息
     */
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        List<LLMMessage> llmMessages = toLLMMessages(messages);
        LLMConfig config = LLMConfig.defaultConfig(model);
        LLMResponse response = provider.generate(llmMessages, config);
        return Response.from(AiMessage.from(response.content()));
    }

    /**
     * 同步生成 — 带自定义配置
     */
    public LLMResponse generateRaw(List<LLMMessage> messages, LLMConfig config) {
        return provider.generate(messages, config);
    }

    /**
     * 流式生成 — Flux<String> token 流
     */
    public Flux<String> generateStream(List<LLMMessage> messages, LLMConfig config) {
        return provider.generateStream(messages, config);
    }

    public LLMProvider provider() { return provider; }
    public String modelName() { return model; }
    public boolean supportsTools() { return provider.supportsTools(); }

    /** 转换 LangChain4j ChatMessage → 自定义 LLMMessage */
    private List<LLMMessage> toLLMMessages(List<ChatMessage> messages) {
        return messages.stream().map(m -> {
            if (m instanceof SystemMessage s) {
                return LLMMessage.system(s.text());
            } else if (m instanceof UserMessage u) {
                return LLMMessage.user(u.text());
            } else if (m instanceof AiMessage a) {
                return LLMMessage.assistant(a.text());
            }
            // 未知消息类型：记录警告并作为 user 消息兜底
            log.warn("未知 ChatMessage 类型: {}，作为 user 消息处理", m.type());
            return LLMMessage.user(m.toString());
        }).collect(Collectors.toList());
    }

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(MultiProviderChatModel.class);

    /** 工厂方法 */
    public static MultiProviderChatModel create(LLMProvider provider, String model) {
        return new MultiProviderChatModel(provider, model);
    }
}
