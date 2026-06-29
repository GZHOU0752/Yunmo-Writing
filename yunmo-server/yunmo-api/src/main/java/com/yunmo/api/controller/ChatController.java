package com.yunmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.service.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 写作助手聊天 API
 * 支持润色、续写、人物分析、逻辑检查等多种写作辅助功能
 */
@RestController
@RequestMapping("/api/v1/novels/{novelId}/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * AI 写作助手聊天（SSE 流式）
     *
     * @param novelId 小说 ID
     * @param body    请求体：{ message, chapterNumber?, history? }
     * @return SSE 流
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@PathVariable String novelId,
                             @RequestBody Map<String, Object> body) {
        log.info("[Chat] 收到聊天请求 — novel={}, body={}", novelId, body);
        // 安全提取 message（支持 String 和 Number 类型）
        Object msgObj = body.get("message");
        String message = msgObj != null ? String.valueOf(msgObj) : "";
        if (message.isBlank()) {
            log.warn("[Chat] 消息为空");
            return Flux.just(ServerSentEvent.<String>builder()
                .data("{\"error\":\"消息不能为空\"}").event("error").build());
        }

        // 安全提取 chapterNumber（支持 Number 和 String 类型）
        Integer chapterNumber = null;
        Object cnObj = body.get("chapterNumber");
        if (cnObj instanceof Number n) {
            chapterNumber = n.intValue();
        } else if (cnObj instanceof String s && !s.isBlank()) {
            try {
                chapterNumber = Integer.valueOf(s);
            } catch (NumberFormatException ignored) {
                // 非法格式 → 忽略，当作未提供
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        log.info("[Chat] 开始处理 — novelId={}, message={}, chapterNumber={}", novelId, message, chapterNumber);

        return Flux.concat(
            Flux.just(ServerSentEvent.<String>builder()
                .data("{\"status\":\"thinking\"}").event("start").build()),
            chatService.chat(novelId, message, chapterNumber, history)
                .doOnNext(chunk -> log.debug("[Chat] 收到 chunk: {}", chunk))
                .doOnError(e -> log.error("[Chat] 流式错误: {}", e.getMessage()))
                .map(chunk -> {
                    try {
                        String escaped = objectMapper.writeValueAsString(Map.of("token", chunk));
                        return ServerSentEvent.<String>builder().data(escaped).build();
                    } catch (Exception e) {
                        log.error("[Chat] 序列化失败: {}", e.getMessage());
                        return ServerSentEvent.<String>builder()
                            .data("{\"token\":\"[序列化失败]\"}").build();
                    }
                }),
            Flux.just(ServerSentEvent.<String>builder()
                .data("{}").event("done").build())
        ).doOnComplete(() -> log.info("[Chat] 聊天完成 — novelId={}", novelId));
    }
}
