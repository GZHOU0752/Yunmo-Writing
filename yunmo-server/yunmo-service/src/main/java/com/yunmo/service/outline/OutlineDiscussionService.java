package com.yunmo.service.outline;

import com.yunmo.domain.entity.OutlineNode;
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

/**
 * AI 对话式大纲讨论服务 — 作者与 AI 以对话方式探讨剧情走向
 * SSE 流式返回 AI 建议
 */
@Service
public class OutlineDiscussionService {

    private static final Logger log = LoggerFactory.getLogger(OutlineDiscussionService.class);
    private final StreamingChatLanguageModel streamingModel;
    private final OutlineNodeService outlineNodeService;
    private final com.yunmo.domain.repository.ChapterRepository chapterRepo;
    private final com.yunmo.domain.repository.CharacterRepository characterRepo;
    private final com.yunmo.domain.repository.NovelRepository novelRepo;

    public OutlineDiscussionService(ChatModelFactory modelFactory,
                                     OutlineNodeService outlineNodeService,
                                     com.yunmo.domain.repository.ChapterRepository chapterRepo,
                                     com.yunmo.domain.repository.CharacterRepository characterRepo,
                                     com.yunmo.domain.repository.NovelRepository novelRepo) {
        this.streamingModel = modelFactory.getStreamingModel("deepseek", "deepseek-v4-pro");
        this.outlineNodeService = outlineNodeService;
        this.chapterRepo = chapterRepo;
        this.characterRepo = characterRepo;
        this.novelRepo = novelRepo;
    }

    /**
     * 引导式章节规划——AI主动提问，根据用户回答生成章节卡
     * 返回 SSE 流：先输出3-4个引导问题，用户回答后生成章节卡
     */
    public Flux<String> planChapter(String novelId, int chapterNumber, String userAnswers) {
        return Flux.defer(() -> {
            try {
                String context = buildOutlineContext(novelId, null);

                // 组装前文章节摘要
                StringBuilder prevChapterSummary = new StringBuilder();
                try {
                    var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
                    for (var ch : chapters) {
                        if (ch.getChapterNumber() >= chapterNumber) break;
                        String title = ch.getTitle() != null ? ch.getTitle() : "第" + ch.getChapterNumber() + "章";
                        prevChapterSummary.append(title).append(": ");
                        if (ch.getWritingPlan() != null && !ch.getWritingPlan().isBlank()) {
                            prevChapterSummary.append(ch.getWritingPlan().substring(0, Math.min(100, ch.getWritingPlan().length())));
                        }
                        prevChapterSummary.append("\n");
                    }
                } catch (Exception e) { /* ignore */ }

                String prompt;
                if (userAnswers == null || userAnswers.isBlank()) {
                    // 第一轮：生成引导问题
                    prompt = String.format("""
                        你是一个资深网文编辑，正在帮助作者规划第%d章的写作。

                        ## 当前小说上下文
                        %s

                        ## 已写章节
                        %s

                        请提出3-4个引导性问题，帮助作者明确本章写作方向。问题应覆盖：
                        1. 本章的核心冲突是什么？（承接上文哪个未决点）
                        2. 主角在本章的目标和行动是什么？
                        3. 反派/对手在本章做了什么准备或动作？
                        4. 章末应该落在哪个悬念或画面？

                        请直接输出问题，每行一个问题，以序号开头。不要加其他说明。
                        """, chapterNumber, context,
                        prevChapterSummary.length() > 0 ? prevChapterSummary.toString() : "（首章，无前文）");
                } else {
                    // 第二轮：根据用户回答生成章节卡
                    prompt = String.format("""
                        你是一个资深网文编辑。作者已经回答了你的引导问题，请基于回答生成第%d章的「章节卡」。

                        ## 小说上下文
                        %s

                        ## 作者的规划回答
                        %s

                        请生成一张章节卡，格式如下（直接输出，不要JSON包装）：

                        本章剧情：起点局面→核心事件链→章末局面（150-300字因果句）
                        冲突焦点：本章撬动什么核心矛盾
                        关键动作：至少3条（硬动作+信息动作）
                        章末钩子：下一章的未决牵引点
                        登场角色：本章出场的关键角色
                        """, chapterNumber, context, userAnswers);
                }

                return FluxStreamingAdapter.toFlux(streamingModel, List.of(UserMessage.from(prompt)))
                .map(chunk -> {
                    if (chunk != null && !chunk.isEmpty()) {
                        return "{\"token\":\"" + escapeJson(chunk) + "\"}";
                    }
                    return "";
                }).timeout(Duration.ofMinutes(2))
                  .onErrorResume(e -> {
                      log.warn("章节规划流中断: {}", e.getMessage());
                      return Mono.just("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                  });
            } catch (Exception e) {
                log.error("章节规划失败: {}", e.getMessage());
                return Flux.just("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });
    }

    /**
     * SSE 流式讨论
     *
     * @param novelId    小说 ID
     * @param nodeId     当前大纲节点 ID（可为 null）
     * @param userMessage 用户消息
     * @return Flux<String> SSE 数据流
     */
    public Flux<String> discuss(String novelId, String nodeId, String userMessage) {
        return Flux.defer(() -> {
            try {
                // 组装大纲上下文
                String outlineContext = buildOutlineContext(novelId, nodeId);

                String prompt = String.format("""
                    你是一位资深网文编辑和写作教练。作者正在与你讨论小说的剧情走向和创作思路。
                    你能看到当前的大纲结构、角色设定和已写章节。请像一个真正的编辑伙伴一样交流。

                    ## 当前小说上下文
                    %s

                    ## 作者的讨论
                    %s

                    请以对话方式回复，给出具体的、可操作的建议。可以包括：
                    - 剧情走向建议（2-3个可能的转折方向，并分析各自利弊）
                    - 人物弧光分析（角色的成长空间和内在冲突）
                    - 伏笔和呼应的铺设建议
                    - 节奏和张力的把控建议
                    - 世界观扩展的灵感（如果适用）

                    回复控制在 300 字以内，要有洞察力但不说教。用中文回复，像一个有经验的写作伙伴。
                    """, outlineContext, userMessage);

                return FluxStreamingAdapter.toFlux(streamingModel, List.of(UserMessage.from(prompt)))
                .map(chunk -> {
                    if (chunk != null && !chunk.isEmpty()) {
                        return "{\"token\":\"" + escapeJson(chunk) + "\"}";
                    }
                    return "";
                }).timeout(Duration.ofMinutes(2))
                  .onErrorResume(e -> {
                      log.warn("大纲讨论流中断: {}", e.getMessage());
                      return Mono.just("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                  });

            } catch (Exception e) {
                log.error("大纲讨论失败: {}", e.getMessage());
                return Flux.just("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });
    }

    /** 构建讨论上下文：书名+简介+全书大纲 + 大纲树 + 角色 + 章节摘要 */
    private String buildOutlineContext(String novelId, String nodeId) {
        StringBuilder sb = new StringBuilder();
        List<OutlineNode> allNodes = outlineNodeService.getTree(novelId);

        // 0. 书名 + 简介 + 全书大纲（Novel.outline）
        try {
            var novel = novelRepo.findById(novelId).orElse(null);
            if (novel != null) {
                sb.append("## 作品信息\n");
                sb.append("书名：").append(novel.getTitle()).append("\n");
                if (novel.getSynopsis() != null && !novel.getSynopsis().isBlank()) {
                    sb.append("简介：").append(novel.getSynopsis()).append("\n");
                }
                if (novel.getOutline() != null && !novel.getOutline().isBlank()) {
                    sb.append("全书大纲：\n").append(novel.getOutline()).append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) { /* ignore */ }

        // 1. 大纲树结构
        sb.append("## 大纲树\n");
        allNodes.stream()
            .filter(n -> n.getLevel() <= 2)
            .sorted((a, b) -> {
                int levelCmp = Integer.compare(a.getLevel(), b.getLevel());
                if (levelCmp != 0) return levelCmp;
                return Integer.compare(
                    a.getSequenceOrder() != null ? a.getSequenceOrder() : 0,
                    b.getSequenceOrder() != null ? b.getSequenceOrder() : 0);
            })
            .forEach(n -> {
                String indent = "  ".repeat(Math.max(0, n.getLevel() - 1));
                sb.append(indent).append("- ").append(n.getTitle());
                if (n.getOutlineContent() != null && !n.getOutlineContent().isEmpty()) {
                    sb.append(": ").append(n.getOutlineContent());
                }
                if (n.getStatus() != null) sb.append(" [").append(n.getStatus()).append("]");
                sb.append("\n");
            });

        // 2. 角色列表
        try {
            var chars = characterRepo.findByNovelIdAndIsDeadFalse(novelId);
            if (!chars.isEmpty()) {
                sb.append("\n## 角色\n");
                for (var c : chars) {
                    sb.append("- ").append(c.getName())
                        .append("（").append(c.getRole().getDescription()).append("）");
                    if (c.getDescription() != null && !c.getDescription().isEmpty()) {
                        String desc = c.getDescription().length() > 80
                            ? c.getDescription().substring(0, 80) + "…" : c.getDescription();
                        sb.append(": ").append(desc);
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) { /* ignore */ }

        // 3. 已写章节摘要
        try {
            var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
            if (!chapters.isEmpty()) {
                sb.append("\n## 已写章节\n");
                int shown = 0;
                for (var ch : chapters) {
                    if (ch.getContent() == null || ch.getContent().isEmpty()) continue;
                    if (shown >= 5) { sb.append("... 共 ").append(chapters.size()).append(" 章\n"); break; }
                    String title = ch.getTitle() != null ? ch.getTitle() : "第" + ch.getChapterNumber() + "章";
                    int wc = ch.getWordCount() != null ? ch.getWordCount() : 0;
                    sb.append("- ").append(title).append("（").append(wc).append("字）\n");
                    shown++;
                }
            }
        } catch (Exception e) { /* ignore */ }

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
