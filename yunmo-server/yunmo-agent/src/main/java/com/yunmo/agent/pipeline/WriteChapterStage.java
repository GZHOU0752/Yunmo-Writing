package com.yunmo.agent.pipeline;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import com.yunmo.agent.hook.HookSelection;
import com.yunmo.agent.hook.HookSystem;
import com.yunmo.common.enums.AgentType;
import com.yunmo.llm.adapter.FluxStreamingAdapter;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.common.util.JsonExtractor;
import java.util.*;

/**
 * 写作阶段 — 替代 Python write_chapter_node
 * 构建完整写作 Prompt → 调用 Writer Agent 生成正文
 *
 * P1-7: 集成了悬念钩子系统（章首引子7式 + 章尾钩子13式 + 跨章悬念弧）
 */
@Component
public class WriteChapterStage implements PipelinePlugin {
    @Override public int defaultPriority() { return 50; }

    private static final Logger log = LoggerFactory.getLogger(WriteChapterStage.class);
    private final AgentFactory agentFactory;
    private final HookSystem hookSystem;
    private Map<AgentType, AgentSpec> agentSpecs;

    public WriteChapterStage(AgentFactory agentFactory, HookSystem hookSystem) {
        this.agentFactory = agentFactory;
        this.hookSystem = hookSystem;
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
        int targetWordCount = state.getOrDefault("target_word_count", Integer.class, 2500);
        int targetWordMin = state.getOrDefault("target_word_min", Integer.class, 2300);
        int targetWordMax = state.getOrDefault("target_word_max", Integer.class, 2799);

        @SuppressWarnings("unchecked")
        Map<String, Object> genreConfig = state.get("genre_config", Map.class);

        // RAG 参考素材
        String ragContext = state.getOrDefault("rag_context", String.class, "");

        // 爽点结构 + 增量记忆
        String pleasureBeat = state.getOrDefault("pleasure_beat", String.class, "");
        String incrementalMemory = state.getOrDefault("incremental_memory", String.class, "");

        // 连续性警告
        @SuppressWarnings("unchecked")
        List<String> continuityWarnings = state.getOrDefault("continuity_warnings", List.class, List.of());

        // 写作指南（按需加载的 combat/emotion/dialogue/scene 等 md 文件）
        String writingGuide = state.getOrDefault("writing_guide", String.class, "");

        // 小说基本上下文 — 书名+简介+大纲，作为 userFocus 为空时的保底
        String novelTitle = state.getOrDefault("novel_title", String.class, "");
        String novelSynopsis = state.getOrDefault("novel_synopsis", String.class, "");
        String novelOutline = state.getOrDefault("novel_outline", String.class, "");

        // Writer persona — 根据genre自动选择的写手人设
        String writerPersona = state.getOrDefault("writer_persona", String.class, "");

        // 实体生命周期 — 终结警告 + 活跃/冷却角色
        @SuppressWarnings("unchecked")
        List<String> entityWarnings = state.getOrDefault("entity_warnings", List.class, List.of());
        String entitySummary = state.getOrDefault("entity_summary", String.class, "");

        // 体裁规则包 — 核心承诺/禁用模式/追读法则
        String genreContext = state.getOrDefault("genre_context", String.class, "");

        // P1-7: 钩子选择结果
        HookSelection hookSelection = state.get("hook_selection", HookSelection.class);

        // 构建完整写作 Prompt
        String writingPrompt = buildWritingPrompt(
                contextText, chapterPlan, architectReport, guardianCheck,
                userFocus, targetWordCount, targetWordMin, targetWordMax, genreConfig, ragContext,
                pleasureBeat, incrementalMemory, continuityWarnings, writingGuide,
                novelTitle, novelSynopsis, novelOutline, writerPersona,
                entityWarnings, entitySummary, genreContext, hookSelection
        );

        // P1-7: 构建带钩子要求的 System Prompt
        String systemPrompt = buildSystemPromptWithHooks(agentSpecs.get(AgentType.WRITER).systemPrompt(),
                hookSelection);

        var writerModel = agentFactory.createChatModel(agentSpecs.get(AgentType.WRITER));
        var response = writerModel.generate(
                SystemMessage.from(systemPrompt),
                UserMessage.from(writingPrompt)
        );

        String chapterContent = response.content().text();
        int wordCount = estimateWordCount(chapterContent);

        // 字数范围检查
        if (wordCount < targetWordMin) {
            log.warn("[WriteChapter] 字数不足: {} 字 < {} 字（最低要求），建议重试", wordCount, targetWordMin);
        } else if (wordCount > targetWordMax) {
            log.warn("[WriteChapter] 字数超标: {} 字 > {} 字（上限），建议重试", wordCount, targetWordMax);
        } else {
            log.info("[WriteChapter] 章节生成完成 — {} 字 ✓ (范围{}-{})", wordCount, targetWordMin, targetWordMax);
        }

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

        // P1-7: 钩子选择结果
        HookSelection hookSelection = state.get("hook_selection", HookSelection.class);

        String writingPrompt = buildStreamPrompt(state);
        StreamingChatLanguageModel streamingModel = agentFactory.createStreamingChatModel(
                agentSpecs.get(AgentType.WRITER));
        String systemPrompt = buildSystemPromptWithHooks(
                agentSpecs.get(AgentType.WRITER).systemPrompt(), hookSelection);

        // 构建 LangChain4j 消息列表
        List<ChatMessage> chatMessages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(writingPrompt)
        );

        // 用于累积完整章节内容的 StringBuilder
        StringBuilder fullContent = new StringBuilder();

        // 流式调用 — Flux<String> token 流
        return FluxStreamingAdapter.toFlux(streamingModel, chatMessages)
                .map(token -> {
                    fullContent.append(token);
                    return new StageEvent("write_chapter", "writing",
                            StageOutput.of("token", token));
                })
                // 在 onComplete 中同步写入状态，确保下游阶段读取时状态已就绪
                .doOnComplete(() -> {
                    String content = fullContent.toString();
                    int wordCount = estimateWordCount(content);
                    int targetWordMin = state.getOrDefault("target_word_min", Integer.class, 2300);
                    int targetWordMax = state.getOrDefault("target_word_max", Integer.class, 2799);
                    if (wordCount < targetWordMin) {
                        log.warn("[WriteChapter-Stream] 字数不足: {} 字 < {} 字（最低要求），建议重试",
                                wordCount, targetWordMin);
                    } else if (wordCount > targetWordMax) {
                        log.warn("[WriteChapter-Stream] 字数超标: {} 字 > {} 字（上限），建议重试",
                                wordCount, targetWordMax);
                    } else {
                        log.info("[WriteChapter-Stream] 流式输出完成 — {} 字 ✓ (范围{}-{})",
                                wordCount, targetWordMin, targetWordMax);
                    }
                    state.put("chapter_content", content);
                    state.put("chapter_word_count", wordCount);
                    state.putFile("chapter_content.md", content);
                })
                .concatWith(Flux.defer(() -> Flux.just(
                    new StageEvent("write_chapter", "writing_done",
                            StageOutput.of("chapter_word_count",
                                    state.getOrDefault("chapter_word_count", Integer.class, 0))))));
    }

    @Override
    public String name() {
        return "write_chapter";
    }

    /** 流式 Prompt 构建 — 与非流式保持同等信息层级 */
    private String buildStreamPrompt(PipelineState state) {
        String contextText = state.get("context_text", String.class);
        String chapterPlan = state.get("chapter_plan", String.class);
        String architectReport = state.get("architect_report", String.class);
        String guardianCheck = state.get("guardian_pre_check", String.class);
        String userFocus = state.getOrDefault("user_focus", String.class, "");
        String ragContext = state.getOrDefault("rag_context", String.class, "");
        String pleasureBeat = state.getOrDefault("pleasure_beat", String.class, "");
        String memoryCtx = state.getOrDefault("incremental_memory", String.class, "");
        String writingGuide = state.getOrDefault("writing_guide", String.class, "");
        int targetWordMin = state.getOrDefault("target_word_min", Integer.class, 2300);
        int targetWordMax = state.getOrDefault("target_word_max", Integer.class, 2799);
        boolean hasUserFocus = !userFocus.isEmpty();

        // 小说基本上下文 — 书名+简介+大纲
        String novelTitle = state.getOrDefault("novel_title", String.class, "");
        String novelSynopsis = state.getOrDefault("novel_synopsis", String.class, "");
        String novelOutline = state.getOrDefault("novel_outline", String.class, "");

        // Writer persona
        String writerPersona = state.getOrDefault("writer_persona", String.class, "");

        // 实体生命周期
        @SuppressWarnings("unchecked")
        List<String> entityWarnings = state.getOrDefault("entity_warnings", List.class, List.of());
        String entitySummary = state.getOrDefault("entity_summary", String.class, "");

        // 体裁规则包 — 核心承诺/禁用模式/追读法则
        String genreContext = state.getOrDefault("genre_context", String.class, "");

        @SuppressWarnings("unchecked")
        Map<String, Object> genreConfig = state.get("genre_config", Map.class);
        @SuppressWarnings("unchecked")
        List<String> continuityWarnings = state.getOrDefault("continuity_warnings", List.class, List.of());

        StringBuilder sb = new StringBuilder();

        // 写手人设
        if (!writerPersona.isEmpty()) {
            sb.append("> 本章写作人设：\n");
            for (String line : writerPersona.split("\n")) {
                String stripped = line.startsWith("> ") ? line.substring(2) : line;
                sb.append("> ").append(stripped).append("\n");
            }
            sb.append("\n");
        }

        int sn = 0;

        // 第一层：作者设定（最高优先级）
        if (hasUserFocus) {
            sb.append("## ").append(toChineseNum(++sn)).append("、作者设定（强制约束）\n");
            sb.append(userFocus).append("\n");
            sb.append("角色名和剧情方向不得自行编造。\n\n");
        } else if (!novelTitle.isEmpty() || !novelSynopsis.isEmpty() || !novelOutline.isEmpty()) {
            sb.append("## ").append(toChineseNum(++sn)).append("、作品信息\n");
            sb.append("**书名：**").append(novelTitle).append("\n\n");
            if (!novelSynopsis.isEmpty()) {
                sb.append("**简介：**").append(novelSynopsis).append("\n\n");
            }
            if (!novelOutline.isEmpty()) {
                sb.append("**全书大纲：**\n").append(novelOutline).append("\n\n");
            }
            sb.append("写作时请紧扣上述设定，不得偏离作品的基本定位。\n\n");
        }

        // 参考素材
        if (!ragContext.isEmpty()) {
            sb.append("## ").append(toChineseNum(++sn)).append("、参考材料\n").append(ragContext).append("\n");
        }
        if (!writingGuide.isEmpty()) {
            if (sn > 0) {
                sb.append("### 写作技法\n");
            } else {
                sb.append("## ").append(toChineseNum(++sn)).append("、写作技法\n");
            }
            sb.append(writingGuide).append("\n");
        }

        // 体裁规则
        if (!genreContext.isEmpty()) {
            sb.append(genreContext).append("\n");
        }

        // 写作约束
        sb.append("## ").append(toChineseNum(++sn)).append("、写作约束\n");
        sb.append("- 字数范围：").append(targetWordMin).append("-").append(targetWordMax)
                .append(" 字（严格限制，不得超出此范围）\n");
        if (genreConfig != null && genreConfig.containsKey("forbidden_terms")) {
            @SuppressWarnings("unchecked")
            List<String> terms = (List<String>) genreConfig.get("forbidden_terms");
            if (!terms.isEmpty()) sb.append("- 禁止术语：").append(String.join("、", terms)).append("\n");
        }
        if (!continuityWarnings.isEmpty()) {
            for (String w : continuityWarnings) sb.append("- ⚠️ ").append(w).append("\n");
        }
        if (!entityWarnings.isEmpty()) {
            sb.append("- 【终结实体禁止】\n");
            for (String w : entityWarnings) sb.append("  - ").append(w).append("\n");
        }
        sb.append("\n");

        // 参考信息
        if (contextText != null && !contextText.isEmpty()) {
            sb.append("## ").append(toChineseNum(++sn)).append("、前情\n").append(contextText).append("\n\n");
        }
        if (chapterPlan != null && !chapterPlan.isEmpty()) {
            sb.append("大纲：").append(chapterPlan).append("\n\n");
        }
        if (!pleasureBeat.isEmpty()) {
            sb.append("节奏建议：").append(pleasureBeat).append("\n\n");
        }
        if (!memoryCtx.isEmpty()) {
            sb.append("近期剧情：").append(memoryCtx).append("\n\n");
        }
        if (!entitySummary.isEmpty()) {
            sb.append("角色出场状态：\n").append(entitySummary).append("\n\n");
        }

        // 分析报告（仅供参考）
        if (architectReport != null && !architectReport.isEmpty()) {
            String formatted = formatArchitectReport(architectReport);
            if (!formatted.isEmpty()) sb.append(formatted).append("\n\n");
        }
        if (guardianCheck != null && !guardianCheck.isEmpty()) {
            String formatted = formatGuardianReport(guardianCheck);
            if (!formatted.isEmpty()) sb.append(formatted).append("\n\n");
        }

        // P1-7: 章首引子 + 章尾钩子 指导（流式模式）
        @SuppressWarnings("unchecked")
        HookSelection hookSelection = state.get("hook_selection", HookSelection.class);
        if (hookSelection != null) {
            sb.append("## 悬念钩子要求（铁律）\n\n");
            sb.append("【章首引子】").append(hookSelection.openingHook().chineseName()).append("\n");
            sb.append(hookSelection.openingPrompt()).append("\n\n");
            sb.append("【章尾钩子】").append(hookSelection.closingHook().chineseName()).append("\n");
            sb.append(hookSelection.closingPrompt()).append("\n\n");
            if (hookSelection.arcContext() != null && !hookSelection.arcContext().isEmpty()) {
                sb.append("【跨章悬念弧】\n").append(hookSelection.arcContext()).append("\n\n");
            }
            sb.append("**重要：每章结尾必须留下悬念钩子，这是网文写作的铁律，不得以'自然收尾'为由跳过。**\n\n");
        }

        // 结尾重申
        sb.append("---\n请开始写作。段落间空行分隔，对话单独成段。");
        if (hasUserFocus) {
            sb.append("主角必须是设定中指定的人物，不得偏离。");
        } else if (!novelTitle.isEmpty()) {
            sb.append("请紧扣「").append(novelTitle).append("」的基本定位写作。");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String buildWritingPrompt(
            String contextText, String chapterPlan,
            String architectReport, String guardianCheck,
            String userFocus, int targetWordCount,
            int targetWordMin, int targetWordMax,
            Map<String, Object> genreConfig,
            String ragContext,
            String pleasureBeat, String incrementalMemory,
            List<String> continuityWarnings,
            String writingGuide,
            String novelTitle, String novelSynopsis, String novelOutline,
            String writerPersona,
            List<String> entityWarnings, String entitySummary,
            String genreContext,
            HookSelection hookSelection
    ) {
        /*
         * Prompt 结构：首因效应（最重要→最前面）+ 近因效应（约束→最后）
         * 各层按优先级排列，动态编号
         */
        StringBuilder sb = new StringBuilder();
        boolean hasUserFocus = userFocus != null && !userFocus.isEmpty();
        boolean hasNovelContext = (!novelTitle.isEmpty() || !novelSynopsis.isEmpty() || !novelOutline.isEmpty());
        int num = 0;

        // === 写手人设（紧接作品信息之后） ===
        if (writerPersona != null && !writerPersona.isEmpty()) {
            sb.append("> 本章写作人设：\n");
            for (String line : writerPersona.split("\n")) {
                String stripped = line.startsWith("> ") ? line.substring(2) : line;
                sb.append("> ").append(stripped).append("\n");
            }
            sb.append("\n");
        }

        // === 第一层：作者设定或作品信息（最高优先级） ===
        if (hasUserFocus) {
            sb.append("## ").append(toChineseNum(++num)).append("、作者设定（最高优先级，必须严格遵守）\n\n");
            sb.append(userFocus).append("\n\n");
            sb.append("**上述设定中的角色名、世界观、剧情方向是强制约束，不得自行编造或替换。**\n\n");
        } else if (hasNovelContext) {
            sb.append("## ").append(toChineseNum(++num)).append("、作品信息\n\n");
            sb.append("**书名：**").append(novelTitle).append("\n\n");
            if (!novelSynopsis.isEmpty()) {
                sb.append("**简介：**").append(novelSynopsis).append("\n\n");
            }
            if (!novelOutline.isEmpty()) {
                sb.append("**全书大纲：**\n").append(novelOutline).append("\n\n");
            }
            sb.append("**写作时请紧扣上述设定，不得偏离作品的基本定位和方向。**\n\n");
        }

        // === 参考素材 + 写作技法 ===
        if (!ragContext.isEmpty()) {
            sb.append("## ").append(toChineseNum(++num)).append("、参考素材（来自你的素材库）\n\n");
            sb.append(ragContext).append("\n");
        }
        if (!writingGuide.isEmpty()) {
            if (num > 0) {
                sb.append("### 写作技法指导\n\n");
            } else {
                sb.append("## ").append(toChineseNum(++num)).append("、写作技法指导\n\n");
            }
            sb.append(writingGuide).append("\n");
        }

        // === 体裁规则 ===
        if (!genreContext.isEmpty()) {
            sb.append(genreContext).append("\n");
        }

        // === 写作约束 ===
        sb.append("## ").append(toChineseNum(++num)).append("、写作约束\n\n");
        sb.append("- 字数范围：").append(targetWordMin).append("-").append(targetWordMax)
                .append(" 字（严格限制，不得超出此范围）\n");

        if (genreConfig != null && genreConfig.containsKey("forbidden_terms")) {
            @SuppressWarnings("unchecked")
            List<String> terms = (List<String>) genreConfig.get("forbidden_terms");
            if (!terms.isEmpty()) {
                sb.append("- 禁止使用以下术语：").append(String.join("、", terms)).append("\n");
            }
        }

        if (!continuityWarnings.isEmpty()) {
            for (String w : continuityWarnings) {
                sb.append("- ⚠️ ").append(w).append("\n");
            }
        }
        if (!entityWarnings.isEmpty()) {
            sb.append("- 【终结实体禁止使用】\n");
            for (String w : entityWarnings) {
                sb.append("  - ").append(w).append("\n");
            }
        }
        sb.append("\n");

        // === 参考信息：前文 + 大纲（对第一章可能为空） ===
        boolean hasContext = contextText != null && !contextText.isEmpty();
        boolean hasPlan = chapterPlan != null && !chapterPlan.isEmpty();
        boolean hasPleasure = !pleasureBeat.isEmpty();
        boolean hasMemory = !incrementalMemory.isEmpty();

        if (hasContext || hasPlan || hasPleasure || hasMemory) {
            sb.append("## ").append(toChineseNum(++num)).append("、本章参考信息\n\n");

            if (hasContext) {
                sb.append("### 前情概要\n").append(contextText).append("\n\n");
            }
            if (hasPlan) {
                sb.append("### 章节大纲\n").append(chapterPlan).append("\n\n");
            }
            if (hasPleasure) {
                sb.append("### 情绪节奏建议\n").append(pleasureBeat).append("\n\n");
            }
            if (hasMemory) {
                sb.append("### 近期剧情追踪\n").append(incrementalMemory).append("\n\n");
            }
            if (!entitySummary.isEmpty()) {
                sb.append("### 角色出场状态\n").append(entitySummary).append("\n\n");
            }
        }

        // === 分析报告：仅供参考，不与作者设定冲突 ===
        boolean hasArchReport = architectReport != null && !architectReport.isEmpty();
        boolean hasGuardReport = guardianCheck != null && !guardianCheck.isEmpty();

        if (hasArchReport || hasGuardReport) {
            sb.append("## ").append(toChineseNum(++num)).append("、分析报告（仅供参考，若与「作者设定」冲突则以作者设定为准）\n\n");

            if (hasArchReport) {
                String formatted = formatArchitectReport(architectReport);
                if (!formatted.isEmpty()) {
                    sb.append(formatted).append("\n\n");
                }
            }
            if (hasGuardReport) {
                String formatted = formatGuardianReport(guardianCheck);
                if (!formatted.isEmpty()) {
                    sb.append(formatted).append("\n\n");
                }
            }
        }

        // ============================================================
        // P1-7: 章首引子 + 章尾钩子 指导
        // ============================================================
        if (hookSelection != null) {
            sb.append("## ").append(toChineseNum(++num)).append("、悬念钩子要求（铁律）\n\n");

            sb.append("【章首引子】").append(hookSelection.openingHook().chineseName()).append("\n");
            sb.append(hookSelection.openingPrompt()).append("\n\n");

            sb.append("【章尾钩子】").append(hookSelection.closingHook().chineseName()).append("\n");
            sb.append(hookSelection.closingPrompt()).append("\n\n");

            // 跨章悬念弧
            if (hookSelection.arcContext() != null && !hookSelection.arcContext().isEmpty()) {
                sb.append("【跨章悬念弧】\n").append(hookSelection.arcContext()).append("\n\n");
            }

            sb.append("**重要：每章结尾必须留下悬念钩子，这是网文写作的铁律，不得以'自然收尾'为由跳过。**\n\n");
        }

        // ============================================================
        // 结尾：重申核心约束 + 开始写作（近因效应）
        // ============================================================
        sb.append("---\n\n");
        sb.append("请开始写作本章正文。段落间用空行分隔，对话单独成段。\n");
        if (hasUserFocus) {
            sb.append("**再次强调：主角必须是作者设定中指定的人物，世界观和剧情方向必须与设定一致，不得偏离。**\n");
        } else if (hasNovelContext) {
            sb.append("**再次强调：请紧扣「").append(novelTitle).append("」的基本定位写作，保持文风和世界观一致。**\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** 数字转中文序号 (1→一, 10→十) */
    private static String toChineseNum(int n) {
        String[] nums = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
        if (n <= 10) return nums[n];
        return String.valueOf(n);
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

    // ===== Architect/Guardian JSON 结构化解析 =====

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private String formatArchitectReport(String rawJson) {
        try {
            String json = JsonExtractor.extractJson(rawJson);
            Map<String, Object> arch = jsonMapper.readValue(json, Map.class);
            StringBuilder sb = new StringBuilder();

            Boolean passed = (Boolean) arch.get("passed");
            if (passed != null && !passed) {
                sb.append("⚠️ 本节情节架构未通过预检，请在写作时特别注意以下问题：\n");
            }

            List<String> concerns = (List<String>) arch.get("concerns");
            if (concerns != null && !concerns.isEmpty()) {
                sb.append("**需要避免的问题：**\n");
                for (String c : concerns) sb.append("- ").append(c).append("\n");
            }

            List<String> suggestions = (List<String>) arch.get("suggestions");
            if (suggestions != null && !suggestions.isEmpty()) {
                sb.append("**写作建议：**\n");
                for (String s : suggestions) sb.append("- ").append(s).append("\n");
            }

            String causal = (String) arch.get("causal_chain");
            if (causal != null && !causal.isEmpty()) {
                sb.append("**因果链要求（必须自洽）：** ").append(causal).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            // JSON解析失败，回退到原文
            return rawJson.length() > 800 ? rawJson.substring(0, 800) : rawJson;
        }
    }

    @SuppressWarnings("unchecked")
    private String formatGuardianReport(String rawJson) {
        try {
            String json = JsonExtractor.extractJson(rawJson);
            Map<String, Object> guard = jsonMapper.readValue(json, Map.class);
            StringBuilder sb = new StringBuilder();

            Boolean passed = (Boolean) guard.get("passed");
            if (passed != null && !passed) {
                sb.append("⚠️ 类型预检未通过！\n");
            }

            List<Map<String, Object>> violations = (List<Map<String, Object>>) guard.get("violations");
            if (violations != null && !violations.isEmpty()) {
                for (var v : violations) {
                    String term = (String) v.getOrDefault("term", "未知");
                    String severity = (String) v.getOrDefault("severity", "minor");
                    sb.append("- [").append(severity).append("] 禁止使用：").append(term).append("\n");
                }
            }

            List<String> potential = (List<String>) guard.get("potential_violations");
            if (potential != null && !potential.isEmpty()) {
                sb.append("**禁止术语列表（本章绝对不要出现）：**\n");
                for (String p : potential) sb.append("- ").append(p).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return rawJson.length() > 500 ? rawJson.substring(0, 500) : rawJson;
        }
    }

    // ============================================================
    // P1-7: 钩子系统 — System Prompt 增强
    // ============================================================

    /**
     * 构建带钩子要求的 Writer System Prompt
     * 在基础的 Writer System Prompt 后面追加钩子铁律
     */
    private String buildSystemPromptWithHooks(String baseSystemPrompt, HookSelection hookSelection) {
        if (hookSelection == null) {
            return baseSystemPrompt;
        }

        StringBuilder sb = new StringBuilder(baseSystemPrompt);

        sb.append("\n\n");
        sb.append("=== 悬念钩子系统（铁律） ===\n\n");
        sb.append("你是网文作者，每章结尾必须留下悬念钩子。这是写作的铁律，无论章节内容如何，都不能以'自然收尾'为由跳过。\n\n");

        // 章首引子指令
        sb.append("【章首引子 ").append(hookSelection.openingHook().chineseName()).append("】\n");
        sb.append("在正文开始前，先写一段50-150字的章首引子。类型：")
                .append(hookSelection.openingHook().chineseName()).append("。\n");
        sb.append(hookSelection.openingPrompt()).append("\n\n");

        // 章尾钩子指令
        sb.append("【章尾钩子 ").append(hookSelection.closingHook().chineseName()).append("】\n");
        sb.append("在章节结尾，必须使用「").append(hookSelection.closingHook().chineseName())
                .append("」技法。\n");
        sb.append(hookSelection.closingPrompt()).append("\n\n");

        // 悬念弧管理
        sb.append("【跨章悬念弧管理】\n");
        sb.append("你需要在写作时同时运行3条悬念弧：\n");
        sb.append("1. 短弧（2-3章周期）：即时满足的小悬念，本章或下章解答。\n");
        sb.append("2. 中弧（5-8章周期）：渐进揭露的较大悬念，在卷末或高潮处解答。\n");
        sb.append("3. 长弧（贯穿全书）：全书核心悬念，每章埋一点线索但不揭示全貌。\n");

        if (hookSelection.arcContext() != null && !hookSelection.arcContext().isEmpty()) {
            sb.append("\n当前悬念弧状态：\n");
            sb.append(hookSelection.arcContext()).append("\n");
        }

        sb.append("\n记住：没有悬念的章节是失败的章节。读者翻页的唯一动力是好奇心，而钩子是好奇心的引擎。\n");

        return sb.toString();
    }

}
