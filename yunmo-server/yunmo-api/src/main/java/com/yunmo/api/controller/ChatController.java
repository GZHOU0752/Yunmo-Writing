package com.yunmo.api.controller;

import com.yunmo.service.chat.ChatService;
import org.springframework.http.MediaType;
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

    private final ChatService chatService;

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
    public Flux<String> chat(@PathVariable String novelId,
                             @RequestBody Map<String, Object> body) {
        // 安全提取 message（支持 String 和 Number 类型）
        Object msgObj = body.get("message");
        String message = msgObj != null ? String.valueOf(msgObj) : "";
        if (message.isBlank()) {
            return Flux.just("data: {\"error\":\"消息不能为空\"}\n\n");
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

        return Flux.concat(
            Flux.just("event: start\ndata: {\"status\":\"thinking\"}\n\n"),
            chatService.chat(novelId, message, chapterNumber, history)
                .map(chunk -> "data: " + chunk + "\n\n"),
            Flux.just("event: done\ndata: {}\n\n")
        );
    }
}
