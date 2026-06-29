package com.yunmo.service.outline;

import com.yunmo.domain.entity.OutlineNode;
import com.yunmo.llm.adapter.FluxStreamingAdapter;
import com.yunmo.llm.provider.ChatModelFactory;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * AI 大纲自动补全服务 — 根据上级大纲生成下级节点
 */
@Service
public class OutlineCompletionService {

    private static final Logger log = LoggerFactory.getLogger(OutlineCompletionService.class);
    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingModel;
    private final OutlineNodeService outlineNodeService;

    public OutlineCompletionService(ChatModelFactory modelFactory,
                                     OutlineNodeService outlineNodeService) {
        this.chatModel = modelFactory.getSyncModel("deepseek", "deepseek-v4-pro");
        this.streamingModel = modelFactory.getStreamingModel("deepseek", "deepseek-v4-pro");
        this.outlineNodeService = outlineNodeService;
    }

    /**
     * 流式 AI 补全大纲子节点
     *
     * @param novelId    小说 ID
     * @param parentId   上级大纲节点 ID
     * @param childLevel 子节点层级（parent.level + 1）
     * @param count      期望生成的子节点数量
     * @return 流式返回生成的 JSON —— 每个 chunk 即一段 JSON 增量
     */
    public Flux<String> completeStream(String novelId, String parentId, int childLevel, int count) {
        return Mono.fromCallable(() -> {
            var outline = outlineNodeService.getTree(novelId);
            OutlineNode parent = outline.stream()
                    .filter(n -> n.getId().equals(parentId))
                    .findFirst().orElse(null);

            String parentTitle = parent != null ? parent.getTitle() : "根大纲";
            String parentContent = parent != null && parent.getOutlineContent() != null
                    ? parent.getOutlineContent() : "";
            String causalSentence = parent != null && parent.getCausalSentence() != null
                    ? parent.getCausalSentence() : "";

            String levelName = switch (childLevel) {
                case 0 -> "总纲";
                case 1 -> "卷";
                case 2 -> "章";
                case 3 -> "节";
                default -> "节点";
            };

            List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                SystemMessage.from("你是小说大纲设计师。根据上级大纲生成简洁有力的下级" + levelName + "节点。"),
                UserMessage.from(String.format("""
                        基于以下【上级大纲】，生成 %d 个下级%s节点。
                        每个节点包含：
                        - title: 简洁标题（≤15字）
                        - causal_sentence: 因果关系句（为什么发生→导致什么结果）
                        - outline_content: 该节点的详细写作要点（50-150字）

                        上级大纲：
                        标题：%s
                        内容：%s
                        因果句：%s

                        请以 JSON 数组输出：
                        [{"title":"...", "causal_sentence":"...", "outline_content":"..."}]
                        只输出 JSON，不要其他文字。""",
                        count, levelName, parentTitle, parentContent, causalSentence))
            );

            return FluxStreamingAdapter.toFlux(streamingModel, messages);
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(flux -> flux);
    }

    /** 同步方式 AI 补全大纲（用于非流式场景） */
    public List<OutlineNode> complete(String novelId, String parentId, int childLevel, int count) {
        var outline = outlineNodeService.getTree(novelId);
        OutlineNode parent = outline.stream()
                .filter(n -> n.getId().equals(parentId))
                .findFirst().orElse(null);

        String parentTitle = parent != null ? parent.getTitle() : "根大纲";
        String parentContent = parent != null && parent.getOutlineContent() != null
                ? parent.getOutlineContent() : "";
        String causalSentence = parent != null && parent.getCausalSentence() != null
                ? parent.getCausalSentence() : "";

        String levelName = switch (childLevel) {
            case 0 -> "总纲";
            case 1 -> "卷";
            case 2 -> "章";
            case 3 -> "节";
            default -> "节点";
        };

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
            SystemMessage.from("你是小说大纲设计师。根据上级大纲生成简洁有力的下级" + levelName + "节点。"),
            UserMessage.from(String.format("""
                    基于以下【上级大纲】，生成 %d 个下级%s节点。
                    每个节点包含：
                    - title: 简洁标题（≤15字）
                    - causal_sentence: 因果关系句（为什么发生→导致什么结果）
                    - outline_content: 该节点的详细写作要点（50-150字）

                    上级大纲：
                    标题：%s
                    内容：%s
                    因果句：%s

                    请以 JSON 数组输出：
                    [{"title":"...", "causal_sentence":"...", "outline_content":"..."}]
                    只输出 JSON，不要其他文字。""",
                    count, levelName, parentTitle, parentContent, causalSentence))
        );

        var response = chatModel.generate(messages);
        return parseAndSave(novelId, parentId, childLevel, response.content().text());
    }

    /** 解析 LLM 返回的 JSON 并创建 OutlineNode */
    @SuppressWarnings("unchecked")
    private List<OutlineNode> parseAndSave(String novelId, String parentId, int childLevel, String json) {
        List<OutlineNode> result = new ArrayList<>();
        try {
            // 清理可能的 markdown 代码块包裹
            String cleanJson = json.trim();
            // 移除开头的 ```json 或 ```（可能无换行）
            if (cleanJson.startsWith("```")) {
                int firstNewline = cleanJson.indexOf('\n');
                if (firstNewline >= 0) {
                    cleanJson = cleanJson.substring(firstNewline + 1);
                } else {
                    // 无换行：```[...]```  直接剥掉首尾 ```
                    cleanJson = cleanJson.substring(3);
                }
            }
            // 移除末尾的 ```
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.lastIndexOf("```")).trim();
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> items = mapper.readValue(cleanJson, List.class);

            for (Map<String, Object> item : items) {
                String title = (String) item.get("title");
                String causal = (String) item.get("causal_sentence");
                String content = (String) item.get("outline_content");
                OutlineNode node = outlineNodeService.create(
                        novelId, parentId, title != null ? title : "未命名",
                        childLevel, causal, content, null);
                node.setStatus("ai_generated");
                result.add(node);
            }
            log.info("AI 补全大纲完成: novel={}, parent={}, 生成 {} 个节点", novelId, parentId, result.size());
        } catch (Exception e) {
            log.warn("解析 AI 大纲 JSON 失败: {}", e.getMessage());
        }
        return result;
    }
}
