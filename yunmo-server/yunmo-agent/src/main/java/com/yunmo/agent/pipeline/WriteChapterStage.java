package com.yunmo.agent.pipeline;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.common.enums.AgentType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 写作阶段 — 替代 Python write_chapter_node
 * 构建完整写作 Prompt → 调用 Writer Agent 生成正文
 */
@Component
public class WriteChapterStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(WriteChapterStage.class);
    private final AgentFactory agentFactory;
    private Map<AgentType, AgentSpec> agentSpecs;

    public WriteChapterStage(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    @Override
    public StageOutput execute(PipelineState state) {
        log.info("[WriteChapter] 开始生成章节...");
        ensureSpecs();

        // 从状态中提取写作所需的所有信息
        String contextText = state.get("context_text", String.class);
        String chapterPlan = state.get("chapter_plan", String.class);
        String architectReport = state.get("architect_report", String.class);
        String guardianCheck = state.get("guardian_pre_check", String.class);
        String userFocus = state.getOrDefault("user_focus", String.class, "");
        int targetWordCount = state.getOrDefault("target_word_count", Integer.class, 2000);

        @SuppressWarnings("unchecked")
        Map<String, Object> genreConfig = state.get("genre_config", Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> characterProfiles = state.get("character_profiles", List.class);

        // RAG 参考素材
        String ragContext = state.getOrDefault("rag_context", String.class, "");

        // 构建完整写作 Prompt
        String writingPrompt = buildWritingPrompt(
                contextText, chapterPlan, architectReport, guardianCheck,
                userFocus, targetWordCount, genreConfig, characterProfiles, ragContext
        );

        var writerModel = agentFactory.createChatModel(agentSpecs.get(AgentType.WRITER));
        var response = writerModel.generate(
                SystemMessage.from(agentSpecs.get(AgentType.WRITER).systemPrompt()),
                UserMessage.from(writingPrompt)
        );

        String chapterContent = response.content().text();
        int wordCount = estimateWordCount(chapterContent);

        log.info("[WriteChapter] 章节生成完成 — {} 字", wordCount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chapter_content", chapterContent);
        data.put("chapter_word_count", wordCount);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("chapter_content.md", chapterContent);
        return StageOutput.withFiles(data, files);
    }

    // ===== 流式模式 =====

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Flux<StageEvent> executeStreaming(PipelineState state) {
        log.info("[WriteChapter-Stream] 开始流式生成...");
        ensureSpecs();

        String writingPrompt = buildStreamPrompt(state);
        var writerModel = agentFactory.createChatModel(agentSpecs.get(AgentType.WRITER));
        var systemPrompt = agentSpecs.get(AgentType.WRITER).systemPrompt();

        // 构建 LLM 消息列表
        List<LLMMessage> messages = List.of(
                LLMMessage.system(systemPrompt),
                LLMMessage.user(writingPrompt)
        );

        String modelName = writerModel.modelName();
        LLMConfig config = LLMConfig.creative(modelName != null ? modelName : "deepseek-v4-pro");

        // 用于累积完整章节内容的 StringBuilder
        StringBuilder fullContent = new StringBuilder();

        // 流式调用 — Flux<String> token 流
        return writerModel.generateStream(messages, config)
                .map(token -> {
                    fullContent.append(token);
                    return new StageEvent("write_chapter", "writing",
                            StageOutput.of("token", token));
                })
                .concatWith(Flux.defer(() -> {
                    // 流式结束后，将完整内容写入 PipelineState 供后续 Review 阶段使用
                    String content = fullContent.toString();
                    int wordCount = estimateWordCount(content);
                    log.info("[WriteChapter-Stream] 流式输出完成 — {} 字", wordCount);
                    state.put("chapter_content", content);
                    state.put("chapter_word_count", wordCount);
                    state.putFile("chapter_content.md", content);
                    return Flux.just(new StageEvent("write_chapter", "writing_done",
                            StageOutput.of("chapter_word_count", wordCount)));
                }));
    }

    @Override
    public String name() {
        return "write_chapter";
    }

    /** 流式 Prompt 构建（含字数约束在 prompt 内） */
    private String buildStreamPrompt(PipelineState state) {
        String contextText = state.get("context_text", String.class);
        String chapterPlan = state.get("chapter_plan", String.class);
        String architectReport = state.get("architect_report", String.class);
        String guardianCheck = state.get("guardian_pre_check", String.class);
        String userFocus = state.getOrDefault("user_focus", String.class, "");
        String ragContext = state.getOrDefault("rag_context", String.class, "");
        int targetWordCount = state.getOrDefault("target_word_count", Integer.class, 2000);

        @SuppressWarnings("unchecked")
        Map<String, Object> genreConfig = state.get("genre_config", Map.class);

        StringBuilder sb = new StringBuilder();
        if (genreConfig != null && genreConfig.containsKey("forbidden_terms")) {
            @SuppressWarnings("unchecked")
            List<String> terms = (List<String>) genreConfig.get("forbidden_terms");
            sb.append("禁止术语: ").append(String.join("、", terms)).append("\n");
        }
        if (contextText != null && !contextText.isEmpty()) {
            sb.append("前文: ").append(contextText.length() > 1500
                    ? contextText.substring(0, 1500) + "..." : contextText).append("\n");
        }
        if (ragContext != null && !ragContext.isEmpty()) {
            sb.append(ragContext).append("\n");
        }
        if (architectReport != null && !architectReport.isEmpty()) {
            sb.append("情节建议: ").append(architectReport).append("\n");
        }
        if (chapterPlan != null && !chapterPlan.isEmpty()) {
            sb.append("大纲: ").append(chapterPlan).append("\n");
        }
        if (userFocus != null && !userFocus.isEmpty()) {
            sb.append("作者指示: ").append(userFocus).append("\n");
        }
        sb.append("目标字数: ").append(targetWordCount)
          .append("字。段落间用空行分隔，对话单独成段。请开始写作：");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String buildWritingPrompt(
            String contextText, String chapterPlan,
            String architectReport, String guardianCheck,
            String userFocus, int targetWordCount,
            Map<String, Object> genreConfig,
            List<Map<String, String>> characterProfiles,
            String ragContext
    ) {
        StringBuilder sb = new StringBuilder();

        // 1. 类型文风要求
        if (genreConfig != null) {
            sb.append("## 类型文风要求\n");
            if (genreConfig.containsKey("writing_blueprint")) {
                sb.append(genreConfig.get("writing_blueprint")).append("\n");
            }
            if (genreConfig.containsKey("forbidden_terms")) {
                List<String> terms = (List<String>) genreConfig.get("forbidden_terms");
                sb.append("**禁止术语**: ").append(String.join("、", terms)).append("\n");
            }
            sb.append("\n");
        }

        // 2. 角色档案
        if (characterProfiles != null && !characterProfiles.isEmpty()) {
            sb.append("## 登场角色\n");
            for (var profile : characterProfiles) {
                sb.append("- **").append(profile.get("name")).append("**");
                String desc = profile.getOrDefault("description", "");
                if (!desc.isBlank()) sb.append("：").append(desc);
                String state = profile.getOrDefault("state", "");
                if (!state.isBlank()) sb.append("（").append(state).append("）");
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 3. 前文锚点
        if (contextText != null && !contextText.isEmpty()) {
            sb.append("## 前文提要\n").append(contextText).append("\n\n");
        }

        // 4. RAG 参考素材
        if (ragContext != null && !ragContext.isEmpty()) {
            sb.append(ragContext).append("\n\n");
        }

        // 5. 目标字数
        sb.append("## 写作要求\n");
        sb.append("- 目标字数: ").append(targetWordCount).append(" 字左右\n");

        // 6. 情节建议 (Architect 报告)
        if (architectReport != null && !architectReport.isEmpty()) {
            sb.append("## 情节架构师建议\n").append(architectReport).append("\n\n");
        }

        // 7. 类型检查 (Guardian 报告)
        if (guardianCheck != null && !guardianCheck.isEmpty()) {
            sb.append("## 类型预检提醒\n").append(guardianCheck).append("\n\n");
        }

        // 8. 写作计划
        if (chapterPlan != null && !chapterPlan.isEmpty()) {
            sb.append("## 本章大纲\n").append(chapterPlan).append("\n\n");
        }

        // 9. 用户指示
        if (userFocus != null && !userFocus.isEmpty()) {
            sb.append("## 作者特别指示\n").append(userFocus).append("\n\n");
        }

        sb.append("段落间用空行分隔，对话单独成段。请开始写作本章正文：");
        return sb.toString();
    }

    private int estimateWordCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 中文字数估算
        int chineseChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseChars++;
            }
        }
        return Math.max(chineseChars, text.replaceAll("\\s+", "").length() / 2);
    }

    private void ensureSpecs() {
        if (agentSpecs == null) {
            agentSpecs = agentFactory.createAllSpecs(Map.of());
        }
    }
}
