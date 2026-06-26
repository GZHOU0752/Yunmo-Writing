package com.yunmo.agent.pipeline;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import com.yunmo.common.enums.AgentType;
import com.yunmo.common.util.AntiAIPatterns;
import com.yunmo.common.util.AntiAIPatterns.DiagnosisResult;
import com.yunmo.common.util.AntiAIPatterns.Severity;
import com.yunmo.llm.adapter.MultiProviderChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 章节润色阶段 — 对话个性化 + 去AI味 + 微动作 + 生活细节。
 * 在 Writer 生成正文后、审校前执行。
 * <p>
 * 集成 AntiAIPatterns 7-Gate 检测引擎：润色前先对原始正文进行检测，
 * 将 Gate 检测结果注入润色 prompt，指导 Polisher Agent 精准修复AI痕迹。
 * </p>
 */
@Component
public class PolishChapterStage implements PipelinePlugin {
    @Override public int defaultPriority() { return 60; }

    private static final Logger log = LoggerFactory.getLogger(PolishChapterStage.class);
    private final AgentFactory agentFactory;

    public PolishChapterStage(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    @Override
    public String name() {
        return "polish_chapter";
    }

    @Override
    public StageOutput execute(PipelineState state) {
        Map<AgentType, AgentSpec> specs = agentFactory.createAllSpecs(Map.of());
        AgentSpec spec = specs.get(AgentType.POLISHER);
        MultiProviderChatModel model = agentFactory.createChatModel(spec);

        String content = state.get("chapter_content", String.class);
        if (content == null || content.isEmpty()) {
            return StageOutput.empty();
        }
        String characterProfiles = state.get("character_profiles", List.class) != null
                ? formatProfiles(state.get("character_profiles", List.class)) : "";

        Integer chapterNumberObj = state.get("chapter_number", Integer.class);
        int chapterNumber = chapterNumberObj != null ? chapterNumberObj : 0;
        log.info("[PolishChapter] 开始润色 — chapter={}, content_length={}", chapterNumber, content.length());

        // ---- Anti-AI 7-Gate 预检 ----
        DiagnosisResult antiAIResult = null;
        String antiAIContextBlock = "";
        try {
            // 尝试加载白名单
            Set<String> whitelist = loadChapterWhitelist(state);
            antiAIResult = AntiAIPatterns.analyze(content, whitelist);
            antiAIContextBlock = AntiAIPatterns.toContextBlock(antiAIResult);
            log.info("[PolishChapter] Anti-AI预检完成 — score={}, severity={}, gates=[{}]",
                    String.format("%.0f", antiAIResult.aiScore), antiAIResult.overallSeverity,
                    summarizeGates(antiAIResult));
        } catch (Exception e) {
            log.warn("[PolishChapter] Anti-AI预检失败，降级为无检测润色: {}", e.getMessage());
            // 降级：使用旧的简单检测
            antiAIContextBlock = AntiAIPatterns.toContextBlock(
                    AntiAIPatterns.legacyAnalyze(content));
        }

        // 如果AI味严重，生成3-Pass修复Prompt作为润色指导
        String fixGuidance = "";
        if (antiAIResult != null && antiAIResult.overallSeverity != Severity.PASS) {
            fixGuidance = generateTargetedFixGuidance(antiAIResult);
        }

        String prompt = buildPrompt(content, characterProfiles, antiAIContextBlock, fixGuidance);
        var genResponse = model.generate(
            SystemMessage.from(spec.systemPrompt()),
            UserMessage.from(prompt)
        );
        String polished = genResponse.content().text();

        // 去掉可能的 markdown 标记
        polished = polished.replaceAll("^```[\\s\\S]*?\\n", "").replaceAll("\\s*```$", "");

        // ---- 润色后复检 ----
        DiagnosisResult postPolishResult = null;
        try {
            Set<String> whitelist = loadChapterWhitelist(state);
            postPolishResult = AntiAIPatterns.analyze(polished, whitelist);
            log.info("[PolishChapter] 润色后复检 — score={}, severity={}, 变化={}",
                    String.format("%.0f", postPolishResult.aiScore), postPolishResult.overallSeverity,
                    String.format("%+.0f", antiAIResult != null ? postPolishResult.aiScore - antiAIResult.aiScore : 0.0));
        } catch (Exception e) {
            log.debug("[PolishChapter] 润色后复检失败: {}", e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chapter_content", polished);
        data.put("chapter_content_raw", content);

        // 将Anti-AI检测结果写入状态供后续阶段使用
        if (antiAIResult != null) {
            data.put("polish_anti_ai_score", antiAIResult.aiScore);
            data.put("polish_anti_ai_severity", antiAIResult.overallSeverity.name());
            // 传递白名单
            Set<String> whitelist = loadChapterWhitelist(state);
            if (!whitelist.isEmpty()) {
                data.put("anti_ai_whitelist", whitelist);
            }
        }
        if (postPolishResult != null) {
            data.put("polish_post_anti_ai_score", postPolishResult.aiScore);
            data.put("polish_post_anti_ai_severity", postPolishResult.overallSeverity.name());
        }

        log.info("[PolishChapter] 润色完成 — chapter={}, before={}, after={}, antiAI_before={}, antiAI_after={}",
                chapterNumber, content.length(), polished.length(),
                String.format("%.0f", antiAIResult != null ? antiAIResult.aiScore : -1.0),
                String.format("%.0f", postPolishResult != null ? postPolishResult.aiScore : -1.0));
        return StageOutput.withFiles(data, null);
    }

    /**
     * 构建集成Anti-AI检测结果的润色Prompt。
     */
    private String buildPrompt(String content, String characterProfiles,
                                String antiAIContext, String fixGuidance) {
        StringBuilder sb = new StringBuilder();
        sb.append("请润色以下章节正文，重点优化：\n\n");
        sb.append("1. 每个角色对话必须有辨识度（口头禅、句式、敬语习惯各不同）\n");
        sb.append("2. 删除AI高频词：不自觉地、缓缓地、似乎、仿佛、隐隐地、莫名地\n");
        sb.append("3. 添加人物微动作（抿嘴、攥拳、眼皮跳、喉结滚动）\n");
        sb.append("4. 添加生活化细节（茶凉了、鞋底沾泥、铜镜蒙尘）\n");
        sb.append("5. 段落长度参差不齐（1句→6句不定），避免均匀段落\n");
        sb.append("6. 每500字至少1个非视觉感官细节\n");

        // 注入Anti-AI检测结果
        if (antiAIContext != null && !antiAIContext.isEmpty()
                && !antiAIContext.contains("未检测到明显")) {
            sb.append("\n").append(antiAIContext).append("\n");
        }

        // 注入针对性修复指引
        if (fixGuidance != null && !fixGuidance.isEmpty()) {
            sb.append("\n## 针对性修复指引\n");
            sb.append(fixGuidance).append("\n");
        }

        sb.append("\n## 角色档案（用于对话个性化）\n");
        sb.append(characterProfiles).append("\n");

        sb.append("\n## 待润色正文\n");
        sb.append(content).append("\n\n");
        sb.append("直接输出润色后的正文，不要添加任何说明。");

        return sb.toString();
    }

    /**
     * 根据7-Gate诊断结果生成针对性修复指引。
     * 只针对FAIL和WARN的Gate输出具体修复方向。
     */
    private String generateTargetedFixGuidance(DiagnosisResult result) {
        StringBuilder sb = new StringBuilder();

        for (var gate : result.gateResults) {
            if (gate.severity == Severity.PASS) continue;

            String prefix = gate.severity == Severity.FAIL ? "【必须修复】" : "【建议修复】";
            sb.append(prefix).append(" ").append(gate.gateName).append(": ").append(gate.summary).append("\n");

            // 根据Gate类型生成具体指令
            if (gate.gateName.contains("Gate A") && gate.severity == Severity.FAIL) {
                sb.append("  → 立即删除所有最毒句式（\"不是...而是\"、\"眼中闪过\"、\"仿佛...一般\"等）\n");
                sb.append("  → 用具体动作和感官描写替代所有一级禁用词\n");
            }
            if (gate.gateName.contains("Gate B")) {
                sb.append("  → 打破\"不是A而是B\"句式，改用直述白描\n");
                sb.append("  → 减少转折词使用频率（然而/与此同时等），用情节自然过渡\n");
            }
            if (gate.gateName.contains("Gate C") && gate.severity == Severity.FAIL) {
                sb.append("  → 将内心独白改为身体反应或面部表情的外化描写\n");
                sb.append("  → 删除\"他觉得/感到\"等陈述，改为\"他攥紧了拳头/喉结滚动\"等具象动作\n");
            }
            if (gate.gateName.contains("Gate D")) {
                sb.append("  → 打散均匀段落，制造长短交替的呼吸感\n");
                sb.append("  → 适当使用单句段落制造节奏亮点\n");
            }
            if (gate.gateName.contains("Gate E")) {
                sb.append("  → 减少\"xxx说/道\"标签，用角色特征性动作替代\n");
                sb.append("  → 增加对话间的动作穿插和沉默留白\n");
            }
            if (gate.gateName.contains("Gate F")) {
                sb.append("  → 砍掉章末总结句，用动作或画面收尾\n");
                sb.append("  → 去掉\"他不知道的是...\"等硬预告\n");
            }
            if (gate.gateName.contains("Gate G")) {
                sb.append("  → 删除叙述者跳出来评论的句子\n");
                sb.append("  → 去掉\"显然/毕竟/毫无疑问\"等上帝视角判断词\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 汇总各Gate严重度，用于日志。
     */
    private String summarizeGates(DiagnosisResult result) {
        StringBuilder sb = new StringBuilder();
        for (var gate : result.gateResults) {
            String abbr = gateAbbreviation(gate.gateName);
            String status = gate.severity == Severity.PASS ? "P"
                    : gate.severity == Severity.WARN ? "W" : "F";
            if (!sb.isEmpty()) sb.append(",");
            sb.append(abbr).append(":").append(status);
        }
        return sb.toString();
    }

    /** 7-Gate名称缩写（Java 17兼容的if-else实现） */
    private String gateAbbreviation(String gateName) {
        if (gateName == null) return "?";
        if (gateName.contains("Gate A")) return "A";
        if (gateName.contains("Gate B")) return "B";
        if (gateName.contains("Gate C")) return "C";
        if (gateName.contains("Gate D")) return "D";
        if (gateName.contains("Gate E")) return "E";
        if (gateName.contains("Gate F")) return "F";
        if (gateName.contains("Gate G")) return "G";
        return "?";
    }

    /**
     * 从PipelineState加载章节白名单。
     * 支持从状态中传递的白名单词集合。
     */
    @SuppressWarnings("unchecked")
    private Set<String> loadChapterWhitelist(PipelineState state) {
        try {
            Set<String> whitelist = state.getOrDefault("anti_ai_whitelist", Set.class, null);
            if (whitelist != null) {
                return whitelist;
            }
        } catch (Exception e) {
            log.debug("[PolishChapter] 白名单加载失败: {}", e.getMessage());
        }
        return Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    private String formatProfiles(List<?> profiles) {
        if (profiles == null || profiles.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (Object p : profiles) {
            if (p instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) p;
                sb.append("- ").append(m.getOrDefault("name", "?")).append("：")
                  .append(m.getOrDefault("description", "")).append("\n");
            }
        }
        return sb.toString();
    }
}
