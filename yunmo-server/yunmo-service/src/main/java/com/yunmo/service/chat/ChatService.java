package com.yunmo.service.chat;

import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.NovelRepository;
import com.yunmo.llm.adapter.FluxStreamingAdapter;
import com.yunmo.llm.provider.ChatModelFactory;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 写作助手聊天服务 — SSE 流式返回
 * 支持润色、续写、人物分析、逻辑检查等多种写作辅助功能
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final StreamingChatLanguageModel streamingModel;
    private final ChapterRepository chapterRepo;
    private final NovelRepository novelRepo;

    public ChatService(ChatModelFactory modelFactory,
                       ChapterRepository chapterRepo,
                       NovelRepository novelRepo) {
        this.streamingModel = modelFactory.getStreamingModel("deepseek", "deepseek-chat");
        this.chapterRepo = chapterRepo;
        this.novelRepo = novelRepo;
    }

    /**
     * SSE 流式聊天
     *
     * @param novelId       小说 ID
     * @param message       用户消息
     * @param chapterNumber 章节号（可选，提供则包含该章节内容作为上下文）
     * @param history       对话历史（可选）
     * @return Flux<String> SSE 数据流
     */
    public Flux<String> chat(String novelId, String message, Integer chapterNumber, List<Map<String, String>> history) {
        return Mono.fromCallable(() -> buildContext(novelId, chapterNumber))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .flatMapMany(context -> Flux.defer(() -> {
            try {
                // 构建对话历史
                StringBuilder historyText = new StringBuilder();
                if (history != null && !history.isEmpty()) {
                    for (Map<String, String> msg : history) {
                        String role = msg.getOrDefault("role", "user");
                        String content = msg.getOrDefault("content", "");
                        if (role.equals("user")) {
                            historyText.append("用户：").append(content).append("\n\n");
                        } else if (role.equals("ai")) {
                            historyText.append("助手：").append(content).append("\n\n");
                        }
                    }
                }

                String prompt = String.format("""
                    你是一位专业的 AI 写作助手，正在帮助作者进行小说创作。
                    你可以提供以下帮助：
                    1. **润色文字**：使语言更生动自然，消除 AI 痕迹
                    2. **续写情节**：根据已有内容风格和走向续写
                    3. **分析人物**：分析角色性格、动机、关系变化
                    4. **检查逻辑**：检查情节连贯性、因果关系、时间线
                    5. **建议走向**：提供 2-3 个可能的剧情发展方向

                    ## 当前小说上下文
                    %s

                    ## 对话历史
                    %s

                    ## 用户的请求
                    %s

                    请直接回复用户，给出具体、可操作的建议或内容。
                    如果是润色或续写请求，直接输出修改后的文本。
                    如果是分析或建议请求，用清晰的要点形式回复。
                    回复控制在 500 字以内，用中文回复。
                    """, context,
                    historyText.length() > 0 ? historyText.toString() : "（首次对话）",
                    message);

                return FluxStreamingAdapter.toFlux(streamingModel, List.of(UserMessage.from(prompt)))
                .map(chunk -> {
                    if (chunk != null && !chunk.isEmpty()) {
                        return "{\"token\":\"" + escapeJson(chunk) + "\"}";
                    }
                    return "";
                }).timeout(Duration.ofMinutes(3))
                  .onErrorResume(e -> {
                      log.warn("聊天流中断：{}", e.getMessage());
                      return Mono.just("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                  });

            } catch (Exception e) {
                log.error("聊天失败：{}", e.getMessage());
                return Flux.just("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
            }));
    }

    /** 构建聊天上下文：书名 + 简介 + 章节内容 */
    private String buildContext(String novelId, Integer chapterNumber) {
        StringBuilder sb = new StringBuilder();

        // 1. 小说基本信息
        try {
            var novel = novelRepo.findById(novelId).orElse(null);
            if (novel != null) {
                sb.append("书名：").append(novel.getTitle()).append("\n");
                if (novel.getSynopsis() != null && !novel.getSynopsis().isBlank()) {
                    sb.append("简介：").append(novel.getSynopsis().substring(0, Math.min(200, novel.getSynopsis().length()))).append("\n");
                }
                if (novel.getWritingStyle() != null && !novel.getWritingStyle().isBlank()) {
                    sb.append("文风特点：").append(novel.getWritingStyle()).append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) { /* ignore */ }

        // 2. 指定章节内容（如果提供）— 使用索引查询，避免全量加载
        if (chapterNumber != null && chapterNumber > 0) {
            try {
                var chOpt = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber);
                if (chOpt.isPresent()) {
                    Chapter ch = chOpt.get();
                    String title = ch.getTitle() != null ? ch.getTitle() : "第" + ch.getChapterNumber() + "章";
                    sb.append("## 第").append(ch.getChapterNumber()).append("章 ").append(title).append("\n");
                    if (ch.getContent() != null && !ch.getContent().isBlank()) {
                        String content = ch.getContent().replaceAll("<[^>]+>", "");
                        sb.append(content.substring(0, Math.min(2000, content.length())));
                        if (content.length() > 2000) {
                            sb.append("...（内容过长，仅显示前 2000 字）");
                        }
                    }
                }
            } catch (Exception e) { /* ignore */ }
        } else {
            // 3. 最近章节摘要
            try {
                var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
                if (!chapters.isEmpty()) {
                    sb.append("## 最近章节\n");
                    int shown = 0;
                    for (int i = chapters.size() - 1; i >= 0 && shown < 3; i--) {
                        Chapter ch = chapters.get(i);
                        String title = ch.getTitle() != null ? ch.getTitle() : "第" + ch.getChapterNumber() + "章";
                        sb.append("- ").append(title).append("（").append(ch.getWordCount() != null ? ch.getWordCount() : 0).append("字）\n");
                        shown++;
                    }
                }
            } catch (Exception e) { /* ignore */ }
        }

        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
