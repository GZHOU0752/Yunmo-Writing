package com.yunmo.llm.adapter;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 将 LangChain4j 的回调式流式生成转为 Reactor Flux
 *
 * LangChain4j StreamingChatLanguageModel 使用 StreamingResponseHandler 回调，
 * 而 Pipeline 的 Flux<StageEvent> 需要 Reactor Flux。
 * 此适配器桥接两者。
 */
public class FluxStreamingAdapter {

    private static final Logger log = LoggerFactory.getLogger(FluxStreamingAdapter.class);

    /**
     * 将 StreamingChatLanguageModel 的回调式流式输出转为 Flux<String>
     *
     * @param model    流式 ChatModel
     * @param messages 消息列表
     * @return Flux<String> token 流
     */
    public static Flux<String> toFlux(StreamingChatLanguageModel model, List<ChatMessage> messages) {
        return Flux.create(sink -> {
            model.generate(messages, new dev.langchain4j.model.chat.response.StreamingResponseHandler<>() {

                @Override
                public void onPartialResponse(String token) {
                    sink.next(token);
                }

                @Override
                public void onCompleteResponse(Response response) {
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    log.error("流式生成错误: {}", error.getMessage(), error);
                    sink.error(error);
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }
}
