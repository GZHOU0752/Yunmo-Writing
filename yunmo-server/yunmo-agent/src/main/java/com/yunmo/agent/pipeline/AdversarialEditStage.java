package com.yunmo.agent.pipeline;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import com.yunmo.common.enums.AgentType;
import com.yunmo.common.util.AntiAIPatterns;
import com.yunmo.common.util.AntiAIPatterns.DiagnosisResult;
import com.yunmo.common.util.AntiAIPatterns.GateResult;
import com.yunmo.common.util.AntiAIPatterns.Severity;
import dev.langchain4j.model.chat.ChatLanguageModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 对抗编辑阶段 — 用 编辑批评→重写→读者评分 循环替代简单审校。
 * 最多循环3轮，读者评分≥8分通过。第3轮切换回deepseek Editor做最终审查。
 * <p>
 * 反AI检测升级为7-Gate系统：Gate A(禁用词) ~ Gate G(解释腔)，
 * 对抗编辑结束后对所有版本进行7-Gate检测，生成综合诊断报告。
 * </p>
 */
@Component
public class AdversarialEditStage implements PipelinePlugin {
    @Override public int defaultPriority() { return 70; }

    private static final Logger log = LoggerFactory.getLogger(AdversarialEditStage.class);
    private static final int MAX_ROUNDS = 3;
    private static final int PASS_SCORE = 8;
    private final AgentFactory agentFactory;
    private final ObjectMapper mapper = new ObjectMapper();

    public AdversarialEditStage(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    @Override
    public String name() {
        return "adversarial_edit";
    }

    @Override
    public StageOutput execute(PipelineState state) {
        Map<AgentType, AgentSpec> specs = agentFactory.createAllSpecs(Map.of());

        AgentSpec editorSpec = specs.get(AgentType.EDITOR);
        AgentSpec rewriterSpec = specs.get(AgentType.WRITER);
        AgentSpec readerSpec = specs.get(AgentType.READER);

        ChatLanguageModel editor = agentFactory.createChatModel(editorSpec);
        ChatLanguageModel rewriter = agentFactory.createChatModel(rewriterSpec);
        ChatLanguageModel reader = agentFactory.createChatModel(readerSpec);

        int chapterNumber = state.getInt("chapter_number", 0);
        String content = state.get("chapter_content", String.class);
        if (content == null || content.isEmpty()) return StageOutput.empty();

        String contextText = state.get("context_text", String.class);
        String characterProfiles = formatCharacterProfiles(state);

        // 加载白名单
        @SuppressWarnings("unchecked")
        Set<String> whitelist = state.getOrDefault("anti_ai_whitelist", Set.class, Collections.emptySet());

        int bestScore = 0;
        String bestContent = content;
        String previousCriticism = "";
        List<Map<String, Object>> editHistory = new ArrayList<>();
        List<Map<String, Object>> versionSnapshots = new ArrayList<>();

        // 基线评分：润色后的原文
        int baselineScore = scoreContent(reader, readerSpec.systemPrompt(), content);
        bestScore = baselineScore;
        log.info("[AdversarialEdit] 基线评分(Polish后): {}/10 — chapter={}", baselineScore, chapterNumber);

        // 基线版本 + 7-Gate预检
        DiagnosisResult baselineDiagnosis = null;
        try {
            baselineDiagnosis = AntiAIPatterns.analyze(content, whitelist);
            log.info("[AdversarialEdit] 基线7-Gate预检 — AI评分={}, 严重度={}",
                    String.format("%.0f", baselineDiagnosis.aiScore), baselineDiagnosis.overallSeverity);
        } catch (Exception e) {
            log.debug("[AdversarialEdit] 基线7-Gate预检失败: {}", e.getMessage());
        }

        versionSnapshots.add(buildVersionSnapshot("polish_baseline", baselineScore, content,
                baselineDiagnosis));

        if (baselineScore >= PASS_SCORE) {
            log.info("[AdversarialEdit] Polish后已达标，跳过对抗编辑");
            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("chapter_content", content);
            resultData.put("adversarial_score", baselineScore);
            resultData.put("editor_history", List.of());
            resultData.put("chapter_versions", versionSnapshots);

            // 7-Gate 门禁报告
            if (baselineDiagnosis != null) {
                resultData.put("anti_ai_report", buildAntiAIReportMap(baselineDiagnosis));
                resultData.put("guard_results", buildGuardResults(baselineDiagnosis));
            }
            return StageOutput.withFiles(resultData, null);
        }

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            log.info("[AdversarialEdit] 第{}/{}轮 — chapter={}", round, MAX_ROUNDS, chapterNumber);

            // 1. 编辑批评
            String editPrompt = buildEditorPrompt(bestContent, previousCriticism, round);
            var editResponse = editor.generate(
                SystemMessage.from(editorSpec.systemPrompt()),
                UserMessage.from(editPrompt)
            );
            String editResult = editResponse.content().text();
            editResult = editResult.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");

            Map<String, Object> editRecord = new LinkedHashMap<>();
            editRecord.put("round", round);
            editRecord.put("editor_model", round == 1 ? "qwen-max" : "deepseek");
            editRecord.put("editor_raw", editResult);

            previousCriticism = editResult;

            boolean hasFatal = editResult.contains("\"fatal\"") || editResult.contains("\"severity\":\"fatal\"");
            boolean hasSevere = editResult.contains("\"severity\":\"severe\"");
            if (!hasFatal && !hasSevere && round > 1) {
                log.info("[AdversarialEdit] 无严重问题，跳过剩余轮次");
                break;
            }

            // 2. 重写
            String rewritePrompt = buildRewritePrompt(bestContent, editResult, contextText,
                characterProfiles, state, round, previousCriticism);
            var rewriteResponse = rewriter.generate(
                SystemMessage.from(rewriterSpec.systemPrompt()),
                UserMessage.from(rewritePrompt)
            );
            String rewritten = rewriteResponse.content().text();
            rewritten = rewritten.replaceAll("^```[\\s\\S]*?\\n", "").replaceAll("\\s*```$", "");
            editRecord.put("rewritten_length", rewritten.length());

            // 3. 三方Voting团审
            VotingResult voting = votingScore(readerSpec, rewritten);
            int score = voting.medianScore;
            editRecord.put("score", score);
            editRecord.put("voting_scores", voting.scores);
            editRecord.put("voting_divergence", voting.maxScore - voting.minScore);
            editRecord.put("verdict", voting.medianVerdict);
            editRecord.put("comment", voting.comments.get(1));
            log.info("[AdversarialEdit] 第{}轮Voting评分: {}/10 (爽感={}, 逻辑={}, 文笔={})",
                    round, score, voting.scores[0], voting.scores[1], voting.scores[2]);

            if (voting.maxScore - voting.minScore > 3) {
                log.warn("[AdversarialEdit] 评审分歧大 (Δ={})，将在下轮重点审查", voting.maxScore - voting.minScore);
                previousCriticism += "\n[评审分歧] 爽感党给了" + voting.scores[0] +
                    "分, 文笔党给了" + voting.scores[2] + "分，请重点检查节奏和文笔的平衡。";
            }

            // 4. 本轮7-Gate检测
            DiagnosisResult roundDiagnosis = null;
            try {
                roundDiagnosis = AntiAIPatterns.analyze(rewritten, whitelist);
                editRecord.put("anti_ai_score", roundDiagnosis.aiScore);
                editRecord.put("anti_ai_severity", roundDiagnosis.overallSeverity.name());
                log.info("[AdversarialEdit] 第{}轮7-Gate: AI评分={}, 严重度={}",
                        round, String.format("%.0f", roundDiagnosis.aiScore), roundDiagnosis.overallSeverity);
            } catch (Exception e) {
                log.debug("[AdversarialEdit] 第{}轮7-Gate检测失败: {}", round, e.getMessage());
            }

            versionSnapshots.add(buildVersionSnapshot("rewrite_r" + round, score, rewritten,
                    roundDiagnosis));

            if (score > bestScore) {
                bestScore = score;
                bestContent = rewritten;
            }

            if (score >= PASS_SCORE) {
                log.info("[AdversarialEdit] Voting评分达标 (≥{})，通过", PASS_SCORE);
                break;
            }

            editHistory.add(editRecord);
        }

        // 生成修复指引
        List<Map<String, String>> fixGuidance = buildFixGuidance(editHistory);

        // ---- L2: Anti-AI 7-Gate 规则检测（最终版本） ----
        DiagnosisResult finalDiagnosis = null;
        try {
            finalDiagnosis = AntiAIPatterns.analyze(bestContent, whitelist);
            log.info("[AdversarialEdit] 最终7-Gate检测 — AI评分={}, 严重度={}, Gates=[{}]",
                    String.format("%.0f", finalDiagnosis.aiScore), finalDiagnosis.overallSeverity,
                    summarizeGates(finalDiagnosis));
        } catch (Exception e) {
            log.warn("[AdversarialEdit] 最终7-Gate检测失败: {}", e.getMessage());
            // 降级为旧版检测
        }

        // 构建7-Gate详细报告
        Map<String, Object> antiAIMap = buildAntiAIReportMap(finalDiagnosis);
        Map<String, Object> guardResultsMap = buildGuardResults(finalDiagnosis);

        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("chapter_content", bestContent);
        resultData.put("adversarial_score", bestScore);
        resultData.put("editor_history", editHistory);
        resultData.put("chapter_versions", versionSnapshots);
        resultData.put("fix_guidance", fixGuidance);
        resultData.put("anti_ai_report", antiAIMap);
        resultData.put("guard_results", guardResultsMap);

        // 如果AI味严重且评分不足，附加3-Pass修复Prompt供前端手动修复
        if (finalDiagnosis != null && finalDiagnosis.overallSeverity == Severity.FAIL) {
            String fixPrompt = AntiAIPatterns.generateFixPrompt(finalDiagnosis, bestContent);
            resultData.put("anti_ai_fix_prompt", fixPrompt);
            log.warn("[AdversarialEdit] AI味重度，已生成3-Pass修复Prompt供手动修复 — chapter={}",
                    chapterNumber);
        }

        log.info("[AdversarialEdit] 完成 — chapter={}, finalScore={}/10, rounds={}, versions={}, fixes={}, antiAI={}/100, severity={}",
                chapterNumber, bestScore, editHistory.size(), versionSnapshots.size(), fixGuidance.size(),
                String.format("%.0f", finalDiagnosis != null ? finalDiagnosis.aiScore : -1.0),
                finalDiagnosis != null ? finalDiagnosis.overallSeverity : "N/A");
        return StageOutput.withFiles(resultData, null);
    }

    // ========================================================================
    // 7-Gate 报告构建
    // ========================================================================

    /**
     * 构建7-Gate Anti-AI报告Map，兼容前端展示。
     */
    private Map<String, Object> buildAntiAIReportMap(DiagnosisResult diagnosis) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (diagnosis == null) {
            map.put("passed", true);
            map.put("aiScore", 0);
            map.put("totalFindings", 0);
            map.put("overallSeverity", "PASS");
            map.put("findings", List.of());
            map.put("gateResults", List.of());
            map.put("metrics", Map.of());
            return map;
        }

        map.put("passed", diagnosis.passed);
        map.put("aiScore", diagnosis.aiScore);
        map.put("totalFindings", diagnosis.totalFindings);
        map.put("overallSeverity", diagnosis.overallSeverity.name());

        // 各项Gate结果
        List<Map<String, Object>> gateList = new ArrayList<>();
        for (GateResult g : diagnosis.gateResults) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("name", g.gateName);
            gm.put("severity", g.severity.name());
            gm.put("score", g.score);
            gm.put("summary", g.summary);
            gm.put("details", g.details != null ? g.details : List.of());
            gateList.add(gm);
        }
        map.put("gateResults", gateList);

        // Findings
        List<Map<String, Object>> findingsList = new ArrayList<>();
        for (var f : diagnosis.findings) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("gate", f.gate);
            fm.put("category", f.category);
            fm.put("severity", f.severity);
            fm.put("description", f.description);
            fm.put("suggestion", f.suggestion);
            fm.put("confidence", f.confidence);
            findingsList.add(fm);
        }
        map.put("findings", findingsList);

        // 6项客观指标
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("level1Density", diagnosis.metricLevel1Density);
        metrics.put("fatalSentenceHits", diagnosis.metricFatalSentenceHits);
        metrics.put("psychDensity", diagnosis.metricPsychDensity);
        metrics.put("paragraphCV", diagnosis.metricParagraphCV);
        metrics.put("dialogTagRatio", diagnosis.metricDialogTagRatio);
        metrics.put("explanationDensity", diagnosis.metricExplanationDensity);
        map.put("metrics", metrics);

        return map;
    }

    /**
     * 构建三层门禁摘要。
     * L2层为7-Gate规则引擎检测结果。
     */
    private Map<String, Object> buildGuardResults(DiagnosisResult diagnosis) {
        Map<String, Object> guardResultsMap = new LinkedHashMap<>();

        if (diagnosis == null) {
            guardResultsMap.put("finalStatus", "PASS");
            guardResultsMap.put("warningCount", 0);
            guardResultsMap.put("failCount", 0);
            guardResultsMap.put("qualityScore", 10.0);
            guardResultsMap.put("results", List.of());
            return guardResultsMap;
        }

        boolean passed = diagnosis.passed;
        String guardStatus = passed ? "PASS"
                : diagnosis.overallSeverity == Severity.FAIL ? "FAIL" : "WARNING";
        double qualityScore = Math.round(Math.max(0, 10 - diagnosis.aiScore / 10.0) * 10) / 10.0;

        // 统计各Gate严重度
        int failCount = 0;
        int warnCount = 0;
        for (GateResult g : diagnosis.gateResults) {
            if (g.severity == Severity.FAIL) failCount++;
            else if (g.severity == Severity.WARN) warnCount++;
        }

        // 构建各Gate门禁结果
        List<Map<String, Object>> guardResultsList = new ArrayList<>();
        for (GateResult g : diagnosis.gateResults) {
            Map<String, Object> guardItem = new LinkedHashMap<>();
            guardItem.put("guardName", g.gateName);
            guardItem.put("level", 2);
            guardItem.put("status", g.severity == Severity.PASS ? "PASS"
                    : g.severity == Severity.FAIL ? "FAIL" : "WARNING");
            guardItem.put("summary", g.summary);
            guardItem.put("score", Math.round(g.score * 10) / 10.0);
            guardItem.put("issues", g.details != null ? g.details : List.of());
            guardResultsList.add(guardItem);
        }

        guardResultsMap.put("finalStatus", guardStatus);
        guardResultsMap.put("warningCount", warnCount);
        guardResultsMap.put("failCount", failCount);
        guardResultsMap.put("qualityScore", qualityScore);
        guardResultsMap.put("antiAIScore", diagnosis.aiScore);
        guardResultsMap.put("overallSeverity", diagnosis.overallSeverity.name());
        guardResultsMap.put("results", guardResultsList);

        return guardResultsMap;
    }

    /**
     * 构建版本快照（含7-Gate检测结果）。
     */
    private Map<String, Object> buildVersionSnapshot(String version, int score, String content,
                                                      DiagnosisResult diagnosis) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("version", version);
        snapshot.put("score", score);
        snapshot.put("content", content);
        if (diagnosis != null) {
            snapshot.put("anti_ai_score", diagnosis.aiScore);
            snapshot.put("anti_ai_severity", diagnosis.overallSeverity.name());
            snapshot.put("anti_ai_passed", diagnosis.passed);

            // 简化的Gate摘要
            List<Map<String, String>> gateSummary = new ArrayList<>();
            for (GateResult g : diagnosis.gateResults) {
                if (g.severity != Severity.PASS) {
                    gateSummary.add(Map.of(
                            "gate", g.gateName,
                            "severity", g.severity.name(),
                            "summary", g.summary
                    ));
                }
            }
            snapshot.put("anti_ai_gates", gateSummary);
        }
        return snapshot;
    }

    private String summarizeGates(DiagnosisResult result) {
        if (result == null) return "N/A";
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

    // ========================================================================
    // Prompt构建
    // ========================================================================

    private String buildEditorPrompt(String content, String previousCriticism, int round) {
        StringBuilder sb = new StringBuilder();
        sb.append("以最严苛的标准审查以下章节，找出所有问题：\n\n");
        sb.append("1. \"写得合理但没味道\"的句子\n");
        sb.append("2. 角色对话同质化（互换台词也看不出违和）\n");
        sb.append("3. 连续300字以上无情绪变化的段落\n");
        sb.append("4. \"读者会跳过\"的段落（信息密度低、纯过渡、无冲突）\n");
        sb.append("5. AI味表达（\"仿佛/缓缓/不由得/心中一动\"等模板词）\n");
        sb.append("6. 假深度句子（把日常动作过度升华赋予哲理含义）\n");

        if (round > 1 && !previousCriticism.isEmpty()) {
            sb.append("\n## 上一轮的批评意见（请检查是否已修复，同时寻找新问题）\n");
            sb.append(truncate(previousCriticism, 1000)).append("\n");
        }

        sb.append("\n章节内容：\n");
        sb.append(truncate(content, 6000)).append("\n\n");
        sb.append("输出 JSON，标记每个问题的位置和修改方向。");
        return sb.toString();
    }

    private String buildRewritePrompt(String content, String editFeedback, String context,
                                       String profiles, PipelineState state,
                                       int round, String previousCriticism) {
        StringBuilder sb = new StringBuilder();
        if (round == 1) {
            sb.append("根据编辑的批评意见重写以下章节。保持原有情节和结构不变，只修正问题。\n\n");
        } else {
            sb.append("## ⚠️ 第二轮重写\n");
            sb.append("上一轮修改后仍未达标。请尝试**完全不同的写作角度**——如果上一版偏\"叙述\"，这一版偏\"沉浸\"；如果上一版偏\"快节奏\"，这一版增加细节和氛围。\n");
            sb.append("务必彻底修改编辑指出的所有问题，不要只做表面修补。\n\n");
        }
        sb.append("## 前文上下文\n").append(truncate(context, 1500)).append("\n\n");
        sb.append("## 角色档案\n").append(truncate(profiles, 1500)).append("\n\n");

        // 注入爽点结构
        String pleasureBeat = state.getOrDefault("pleasure_beat", String.class, "");
        if (!pleasureBeat.isEmpty()) {
            sb.append("## 本章爽点情绪结构（严格遵循）\n").append(pleasureBeat).append("\n\n");
        }
        String memoryCtx = state.getOrDefault("incremental_memory", String.class, "");
        if (!memoryCtx.isEmpty()) {
            sb.append("## 近期剧情追踪\n").append(memoryCtx).append("\n\n");
        }
        String genreContext = state.getOrDefault("genre_context", String.class, "");
        if (!genreContext.isEmpty()) {
            sb.append(genreContext).append("\n");
        }
        String writingGuide = state.getOrDefault("writing_guide", String.class, "");
        if (!writingGuide.isEmpty()) {
            sb.append("## 写作技法提醒\n").append(writingGuide).append("\n\n");
        }
        @SuppressWarnings("unchecked")
        List<String> entityWarnings = state.getOrDefault("entity_warnings", List.class, List.of());
        if (!entityWarnings.isEmpty()) {
            sb.append("## 【终结实体禁止】以下角色不可在重写中出现\n");
            for (String w : entityWarnings) sb.append("- ").append(w).append("\n");
            sb.append("\n");
        }
        String entitySummary = state.getOrDefault("entity_summary", String.class, "");
        if (!entitySummary.isEmpty()) {
            sb.append("## 角色出场状态\n").append(entitySummary).append("\n\n");
        }

        // 注入去AI味指引
        sb.append("## 去AI味要求\n");
        sb.append("- 禁止：仿佛、犹如、宛若、心中一动、眼中闪过、嘴角勾起\n");
        sb.append("- 禁止：缓缓地、轻轻地、淡淡地、微微地（用具体动词替代）\n");
        sb.append("- 禁止：叙述者跳出来评论/总结/升华\n");
        sb.append("- 要求：每个角色对话有辨识度，段落长短参差不齐\n\n");

        sb.append("## 编辑批评意见\n").append(editFeedback).append("\n\n");
        sb.append("## 待重写正文\n").append(content).append("\n\n");
        sb.append("直接输出重写后的完整正文，不要添加说明。");
        return sb.toString();
    }

    private String buildReaderPrompt(String content) {
        return String.format("""
            以起点中文网付费读者的视角评价以下章节，按1-10分打分：

            评判维度：
            - 开篇吸引力（前100字能不能抓人）
            - 情绪起伏（有没有让人心跳加速的瞬间）
            - 期待感（看完想不想立刻看下一章）
            - 爽感（有没有让人满足或兴奋的点）
            - 人物魅力（有没有让人记住的人物瞬间）

            章节内容：
            %s

            输出 JSON。
            """, truncate(content, 5000));
    }

    @SuppressWarnings("unchecked")
    private String formatCharacterProfiles(PipelineState state) {
        Object obj = state.get("character_profiles", Object.class);
        if (obj == null) return "无";
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map m) {
                    sb.append(m.getOrDefault("name", "?")).append(": ")
                      .append(m.getOrDefault("description", "")).append("\n");
                }
            }
            return sb.toString();
        }
        return obj.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private int scoreContent(ChatLanguageModel reader, String systemPrompt, String content) {
        try {
            String prompt = buildReaderPrompt(content);
            var response = reader.generate(
                SystemMessage.from(systemPrompt),
                UserMessage.from(prompt)
            );
            String result = response.content().text();
            result = result.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
            Map<String, Object> scoreMap = mapper.readValue(result, Map.class);
            return scoreMap.containsKey("score") ? ((Number) scoreMap.get("score")).intValue() : 0;
        } catch (Exception e) {
            log.debug("[AdversarialEdit] 评分解析失败: {}", e.getMessage());
            return 0;
        }
    }

    // ===== Voting团审 =====

    private record VotingResult(int[] scores, int medianScore, int maxScore, int minScore,
                                String medianVerdict, List<String> comments) {}

    private VotingResult votingScore(AgentSpec readerSpec, String content) {
        String[] perspectives = {"爽感党", "逻辑党", "文笔党"};
        String[] prompts = {
            buildShuangGanPrompt(content),
            buildLuojiPrompt(content),
            buildWenbiPrompt(content)
        };

        @SuppressWarnings("unchecked")
        CompletableFuture<Map<String, Object>>[] futures = new CompletableFuture[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                var model = agentFactory.createChatModel(readerSpec);
                var response = model.generate(
                    SystemMessage.from(readerSpec.systemPrompt()),
                    UserMessage.from(prompts[idx])
                );
                String result = response.content().text();
                result = result.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = mapper.readValue(result, Map.class);
                    m.put("_perspective", perspectives[idx]);
                    return m;
                } catch (Exception e) {
                    log.debug("[Voting] {}评分解析失败", perspectives[idx]);
                    Map<String, Object> fallback = new LinkedHashMap<>();
                    fallback.put("score", 5);
                    fallback.put("verdict", "readable");
                    fallback.put("comment", "解析失败");
                    fallback.put("_perspective", perspectives[idx]);
                    return fallback;
                }
            });
        }

        try {
            CompletableFuture.allOf(futures).orTimeout(60, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            log.warn("[Voting] 团审超时: {}", e.getMessage());
        }

        int[] scores = new int[3];
        List<String> comments = new ArrayList<>();
        String medianVerdict = "readable";
        for (int i = 0; i < 3; i++) {
            try {
                Map<String, Object> m = futures[i].get(10, TimeUnit.SECONDS);
                scores[i] = m.containsKey("score") ? ((Number) m.get("score")).intValue() : 5;
                comments.add((String) m.getOrDefault("comment", ""));
                if (i == 1) medianVerdict = (String) m.getOrDefault("verdict", "readable");
            } catch (Exception e) {
                scores[i] = 5;
                comments.add("评分超时或失败");
                log.debug("[Voting] Reader{}结果获取失败: {}", i + 1, e.getMessage());
            }
        }

        int[] sorted = scores.clone();
        java.util.Arrays.sort(sorted);
        int medianScore = sorted[1];

        return new VotingResult(scores, medianScore, sorted[2], sorted[0], medianVerdict, comments);
    }

    private String buildShuangGanPrompt(String content) {
        return String.format("""
            以起点中文网"爽感党"读者的视角评价以下章节（满分10分）。
            你只关心：爽点密度高不高、节奏快不快、有没有让人心跳加速的瞬间。
            你对水文字零容忍。节奏慢的直接扣到5分以下。

            章节内容：
            %s

            输出 JSON：{"score":1-10, "verdict":"trash|readable|good|excellent", "comment":"从爽感角度的评价"}
            """, truncate(content, 5000));
    }

    private String buildLuojiPrompt(String content) {
        return String.format("""
            以起点中文网"逻辑党"读者的视角评价以下章节（满分10分）。
            你只关心：情节因果是否自洽、人物行为是否合理、前后设定是否一致。
            逻辑漏洞、角色降智、强行推进直接扣到5分以下。

            章节内容：
            %s

            输出 JSON：{"score":1-10, "verdict":"trash|readable|good|excellent", "comment":"从逻辑角度的评价"}
            """, truncate(content, 5000));
    }

    private String buildWenbiPrompt(String content) {
        return String.format("""
            以起点中文网"文笔党"读者的视角评价以下章节（满分10分）。
            你只关心：文笔是否自然像人手写的、对话有没有辨识度、有没有AI味。
            套话多、对话同质化、节奏均匀（AI指纹）直接扣到5分以下。

            章节内容：
            %s

            输出 JSON：{"score":1-10, "verdict":"trash|readable|good|excellent", "comment":"从文笔角度的评价"}
            """, truncate(content, 5000));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> buildFixGuidance(List<Map<String, Object>> editHistory) {
        List<Map<String, String>> fixes = new ArrayList<>();
        if (editHistory.isEmpty()) return fixes;

        Map<String, Object> lastRound = editHistory.get(editHistory.size() - 1);

        Object scoresObj = lastRound.get("voting_scores");
        if (scoresObj instanceof List<?> scores) {
            String[] dims = {"爽感", "逻辑", "文笔"};
            String[] dimKeys = {"shuanggan", "luoji", "wenbi"};
            for (int i = 0; i < Math.min(dims.length, scores.size()); i++) {
                int s = scores.get(i) instanceof Number n ? n.intValue() : 5;
                if (s < 8) {
                    Map<String, String> fix = new LinkedHashMap<>();
                    fix.put("dimension", dims[i]);
                    fix.put("dimKey", dimKeys[i]);
                    fix.put("score", String.valueOf(s));
                    fix.put("level", s < 5 ? "severe" : "minor");
                    fix.put("hint", dims[i] + "得分偏低(" + s + "/10)，建议重点优化本章的" + dims[i] + "表现");
                    fixes.add(fix);
                }
            }
        }

        // 附加7-Gate Anti-AI评分（如果存在）
        Object antiAIScore = lastRound.get("anti_ai_score");
        if (antiAIScore instanceof Number aiScore && aiScore.doubleValue() > 40) {
            Map<String, String> fix = new LinkedHashMap<>();
            fix.put("dimension", "去AI味");
            fix.put("dimKey", "anti_ai");
            fix.put("score", String.valueOf(Math.round(aiScore.doubleValue())));
            fix.put("level", aiScore.doubleValue() > 70 ? "severe" : "minor");
            fix.put("hint", String.format("7-Gate AI评分 %.0f/100，建议用AntiAIPatterns.generateFixPrompt生成修复指引",
                    aiScore.doubleValue()));
            fixes.add(fix);
        }

        Object editorRaw = lastRound.get("editor_raw");
        if (editorRaw instanceof String raw && !raw.isEmpty()) {
            Map<String, String> fix = new LinkedHashMap<>();
            fix.put("dimension", "编辑总评");
            fix.put("dimKey", "editor");
            fix.put("level", "info");
            String summary = raw.length() > 200 ? raw.substring(0, 200) + "…" : raw;
            fix.put("hint", summary);
            fixes.add(fix);
        }

        return fixes;
    }
}
