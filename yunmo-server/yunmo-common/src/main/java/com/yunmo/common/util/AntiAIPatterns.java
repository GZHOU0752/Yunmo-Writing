package com.yunmo.common.util;

import com.yunmo.common.slop.AntiSlopConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 去AI味量化检测引擎 — 7 Gate 完整体系。
 * <p>
 * 对标 novel-pipeline-write-engine 的 anti_ai_patterns.py，升级为7门检测：
 * </p>
 *
 * <h3>Gate A - 禁用词检测</h3>
 * <ul>
 *   <li>一级禁用词密度（每千字出现次数）</li>
 *   <li>最毒句式命中次数</li>
 *   <li>严重度：一级词 &gt;5次/千字=重度，&gt;3次=中度</li>
 * </ul>
 *
 * <h3>Gate B - 句式套路检测</h3>
 * <ul>
 *   <li>"不是A而是B"句式</li>
 *   <li>公式化转折词重复</li>
 *   <li>列表式结构（连续≥3句相同开头）</li>
 *   <li>排比句式检测</li>
 * </ul>
 *
 * <h3>Gate C - 心理外化检测</h3>
 * <ul>
 *   <li>情绪陈述词密度（觉得/感到/认为/想/知道/明白）</li>
 *   <li>抽象心理词（心中/内心/心底/心头）</li>
 *   <li>判定：&gt;8次/千字=重度</li>
 * </ul>
 *
 * <h3>Gate D - 节奏检测</h3>
 * <ul>
 *   <li>段落句数变异系数（CV&lt;0.15=AI特征）</li>
 *   <li>连续5段等长段落→警告</li>
 *   <li>平均句长分布</li>
 * </ul>
 *
 * <h3>Gate E - 对话检测</h3>
 * <ul>
 *   <li>对话标签密度（"xxx说"/"xxx道"占对话行比例 &gt;50%=AI味）</li>
 *   <li>禁止的对话前后缀模式</li>
 * </ul>
 *
 * <h3>Gate F - 结尾检测</h3>
 * <ul>
 *   <li>章末总结体</li>
 *   <li>哲理收尾</li>
 *   <li>伏笔式预告</li>
 * </ul>
 *
 * <h3>Gate G - 解释腔检测</h3>
 * <ul>
 *   <li>叙述者下结论密度</li>
 *   <li>元叙事侵入</li>
 *   <li>上帝感全知评论</li>
 * </ul>
 *
 * @author yunmo
 * @since 2.0
 */
public final class AntiAIPatterns {

    private static final Logger log = LoggerFactory.getLogger(AntiAIPatterns.class);

    private AntiAIPatterns() {
        // 工具类，禁止实例化
    }

    // ========================================================================
    // 1. "不是A而是B" 句式模式（Gate B 基础）
    // ========================================================================

    private static final Pattern[] NOT_A_B_PATTERNS = {
            Pattern.compile("不是.{1,40}而是"),
            Pattern.compile("并非.{1,40}而是"),
            Pattern.compile("与其说.{1,40}不如说"),
    };

    // ========================================================================
    // 2. 实物证据关键词 —— "不是A而是B"命中时如果句子含这些词，扣分减半
    // ========================================================================

    private static final Set<String> EVIDENCE_KEYWORDS = Set.of(
            "水缸", "柴刀", "石", "碗", "树皮", "木牌", "炭笔", "馒头", "粥",
            "劈", "砍", "推", "搬", "抬", "抓", "按", "压", "砸",
            "画", "刻", "磨", "擦", "洗", "煮", "烧", "踹", "踢", "摔", "扔",
            "裂开", "渗血", "破皮", "流鼻血", "肿包", "手抖", "腿软", "冷汗",
            "痉挛", "铁锈味", "水沫子", "碎", "断", "折", "塌", "倒"
    );

    // ========================================================================
    // 3. AI 套话（保留原检测 + 从 AntiSlopConfig 扩展）
    // ========================================================================

    private static final Pattern[] AI_CLICHE_PATTERNS = {
            Pattern.compile("那一刻[，,]?[^。]{0,20}(终于|忽然|突然)"),
            Pattern.compile("(终于明白|从未想过|终于意识到)"),
            Pattern.compile("沉默了几秒"),
            Pattern.compile("心头一震|瞳孔骤缩|空气仿佛凝固"),
            Pattern.compile("不由得|不禁|顿时|瞬间|猛然|竟"),
    };

    // ========================================================================
    // 诊断结果类型
    // ========================================================================

    /** 严重度枚举 */
    public enum Severity {
        PASS("通过", "无明显AI痕迹"),
        WARN("警告", "存在中度AI痕迹，建议修改"),
        FAIL("不通过", "重度AI痕迹，必须修改");

        public final String label;
        public final String description;

        Severity(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** 单项Gate检测结果 */
    public static class GateResult {
        public String gateName;       // Gate A ~ G
        public Severity severity;     // 该Gate的严重度
        public double score;          // 该Gate的AI评分 0-100
        public String summary;        // 人类可读摘要
        public List<String> details;  // 具体命中项
    }

    /** 单项发现 */
    public static class Finding {
        public String gate;           // 所属Gate
        public String category;       // 细分类别
        public String severity;       // warning / severe / info
        public String description;
        public String suggestion;
        public double confidence;     // 0.0~1.0
    }

    /**
     * 综合诊断结果 — 6项客观指标自动定档。
     * <p>
     * 综合判定规则：任一重度→FAIL, ≥3项中度→WARN, 否则→PASS。
     * </p>
     */
    public static class DiagnosisResult {
        /** 综合AI评分 0-100 */
        public double aiScore;

        /** 综合严重度 */
        public Severity overallSeverity;

        /** 是否通过检测 */
        public boolean passed;

        /** 总发现数 */
        public int totalFindings;

        /** 各项Gate结果 */
        public List<GateResult> gateResults = new ArrayList<>();

        /** 全部发现项 */
        public List<Finding> findings = new ArrayList<>();

        // --- 6项客观指标 ---

        /** 指标1: 一级禁用词密度（每千字） */
        public double metricLevel1Density;

        /** 指标2: 最毒句式命中次数 */
        public int metricFatalSentenceHits;

        /** 指标3: 心理外化词密度（每千字） */
        public double metricPsychDensity;

        /** 指标4: 段落句数变异系数 */
        public double metricParagraphCV;

        /** 指标5: 对话标签占比 */
        public double metricDialogTagRatio;

        /** 指标6: 解释腔词密度（每千字） */
        public double metricExplanationDensity;

        /** 正文长度（字符数） */
        public int contentLength;
    }

    // ========================================================================
    // 主入口：对章节内容运行全部7 Gate检测
    // ========================================================================

    /**
     * 对章节内容运行全部7 Gate Anti-AI检测。
     *
     * @param content 章节正文（纯文本）
     * @return 综合诊断结果
     */
    public static DiagnosisResult analyze(String content) {
        return analyze(content, Collections.emptySet());
    }

    /**
     * 对章节内容运行全部7 Gate Anti-AI检测（带白名单）。
     *
     * @param content  章节正文（纯文本）
     * @param whitelist 白名单词集合
     * @return 综合诊断结果
     */
    public static DiagnosisResult analyze(String content, Set<String> whitelist) {
        DiagnosisResult result = new DiagnosisResult();
        if (content == null || content.isBlank()) {
            result.passed = true;
            result.overallSeverity = Severity.PASS;
            result.contentLength = 0;
            return result;
        }

        result.contentLength = content.length();
        Set<String> effectiveWhitelist = buildEffectiveWhitelist(whitelist);

        // ---- Gate A: 禁用词检测 ----
        GateResult gateA = gateA_BannedWords(content, effectiveWhitelist);
        result.gateResults.add(gateA);
        result.findings.addAll(extractFindings(gateA, "A"));

        // ---- Gate B: 句式套路检测 ----
        GateResult gateB = gateB_SentencePatterns(content, effectiveWhitelist);
        result.gateResults.add(gateB);
        result.findings.addAll(extractFindings(gateB, "B"));

        // ---- Gate C: 心理外化检测 ----
        GateResult gateC = gateC_PsychExternalization(content, effectiveWhitelist);
        result.gateResults.add(gateC);
        result.findings.addAll(extractFindings(gateC, "C"));

        // ---- Gate D: 节奏检测 ----
        GateResult gateD = gateD_Rhythm(content);
        result.gateResults.add(gateD);
        result.findings.addAll(extractFindings(gateD, "D"));

        // ---- Gate E: 对话检测 ----
        GateResult gateE = gateE_Dialog(content, effectiveWhitelist);
        result.gateResults.add(gateE);
        result.findings.addAll(extractFindings(gateE, "E"));

        // ---- Gate F: 结尾检测 ----
        GateResult gateF = gateF_Ending(content, effectiveWhitelist);
        result.gateResults.add(gateF);
        result.findings.addAll(extractFindings(gateF, "F"));

        // ---- Gate G: 解释腔检测 ----
        GateResult gateG = gateG_ExplanationTone(content, effectiveWhitelist);
        result.gateResults.add(gateG);
        result.findings.addAll(extractFindings(gateG, "G"));

        // 汇总计算
        result.totalFindings = result.findings.size();
        result.aiScore = computeOverallScore(result);
        result.overallSeverity = determineOverallSeverity(result);
        result.passed = result.overallSeverity == Severity.PASS;

        // 提取6项客观指标
        extractMetrics(content, result);

        return result;
    }

    // ========================================================================
    // Gate A - 禁用词检测
    // ========================================================================

    private static GateResult gateA_BannedWords(String content, Set<String> whitelist) {
        GateResult r = new GateResult();
        r.gateName = "Gate A - 禁用词检测";
        r.details = new ArrayList<>();

        int totalLength = content.length();
        double perKFactor = (double) AntiSlopConfig.PER_K_CHARS / Math.max(totalLength, 1);

        // 一级禁用词统计
        int level1Count = 0;
        Map<String, Integer> level1Hits = new LinkedHashMap<>();
        for (String word : AntiSlopConfig.ALL_LEVEL1_BANNED) {
            if (whitelist.contains(word)) continue;
            int cnt = countOccurrences(content, word);
            if (cnt > 0) {
                level1Count += cnt;
                level1Hits.put(word, cnt);
            }
        }
        double level1PerK = level1Count * perKFactor;

        // 最毒句式统计
        int fatalHits = 0;
        List<String> fatalMatches = new ArrayList<>();
        for (Pattern p : AntiSlopConfig.FATAL_SENTENCE_PATTERNS) {
            Matcher m = p.matcher(content);
            while (m.find()) {
                String snippet = extractSnippet(content, m.start(), 60);
                // 白名单过滤：如果匹配片段含白名单词则跳过
                if (whitelist.stream().anyMatch(snippet::contains)) continue;
                fatalHits++;
                if (fatalMatches.size() < 10) {
                    fatalMatches.add(truncate(snippet, 60));
                }
            }
        }

        // 二级禁用词统计
        int level2Count = 0;
        for (String word : AntiSlopConfig.LEVEL2_BANNED) {
            if (whitelist.contains(word)) continue;
            level2Count += countOccurrences(content, word);
        }
        double level2PerK = level2Count * perKFactor;

        // 定档
        if (level1PerK > AntiSlopConfig.LEVEL1_SEVERE_PER_K || fatalHits >= 3) {
            r.severity = Severity.FAIL;
        } else if (level1PerK > AntiSlopConfig.LEVEL1_MODERATE_PER_K || fatalHits >= 1) {
            r.severity = Severity.WARN;
        } else {
            r.severity = Severity.PASS;
        }

        // 评分
        double l1Score = Math.min(100, level1PerK * 15);
        double fatalScore = Math.min(100, fatalHits * 25);
        double l2Score = Math.min(100, level2PerK * 5);
        r.score = Math.min(100, l1Score + fatalScore + l2Score);

        // 摘要
        StringBuilder detail = new StringBuilder();
        if (!level1Hits.isEmpty()) {
            detail.append(String.format("一级禁用词 %.1f次/千字", level1PerK));
            // 列出TOP5高频词
            level1Hits.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> r.details.add(String.format("[%s] \"%s\" ×%d",
                            AntiSlopConfig.categoryOf(e.getKey()), e.getKey(), e.getValue())));
        }
        if (fatalHits > 0) {
            detail.append(String.format("；最毒句式 %d处", fatalHits));
            fatalMatches.forEach(m -> r.details.add("最毒句式: " + m));
        }
        if (level2Count > 0) {
            detail.append(String.format("；二级禁用词 %d次", level2Count));
        }
        r.summary = detail.length() > 0 ? detail.toString() : "未命中禁用词";

        return r;
    }

    // ========================================================================
    // Gate B - 句式套路检测
    // ========================================================================

    private static GateResult gateB_SentencePatterns(String content, Set<String> whitelist) {
        GateResult r = new GateResult();
        r.gateName = "Gate B - 句式套路检测";
        r.details = new ArrayList<>();

        int issueCount = 0;
        double totalConfidence = 0;

        // 1. "不是A而是B"句式检测
        int notABCount = 0;
        for (Pattern p : NOT_A_B_PATTERNS) {
            Matcher m = p.matcher(content);
            while (m.find()) {
                String snippet = extractSnippet(content, m.start(), 80);
                if (whitelist.stream().anyMatch(snippet::contains)) continue;
                notABCount++;
                boolean hasEvidence = hasPhysicalEvidence(snippet);
                r.details.add(String.format("AI句式 \"%s\"%s",
                        truncate(snippet, 50),
                        hasEvidence ? " [含实物动作，AI味较轻]" : ""));
                issueCount++;
                totalConfidence += hasEvidence ? 0.35 : 0.70;
            }
        }

        // 2. 公式化转折词重复检测
        for (String word : AntiSlopConfig.TRANSITION_WORDS) {
            int cnt = countOccurrences(content, word);
            if (cnt >= 3) {
                r.details.add(String.format("转折词重复: \"%s\" ×%d", word, cnt));
                issueCount++;
                totalConfidence += 0.6;
            }
        }

        // 3. 列表式结构（连续≥3句相同开头）
        String[] sentences = content.split("[。！？\\n]");
        List<String> validSentences = new ArrayList<>();
        for (String s : sentences) {
            String t = s.trim();
            if (t.length() > 3) validSentences.add(t);
        }
        if (validSentences.size() >= AntiSlopConfig.LIST_CONSECUTIVE_THRESHOLD) {
            int maxConsecutive = 1;
            int consecutive = 1;
            String patternStart = "";
            for (int i = 1; i < validSentences.size(); i++) {
                String prev = validSentences.get(i - 1).length() >= 2
                        ? validSentences.get(i - 1).substring(0, 2) : "";
                String curr = validSentences.get(i).length() >= 2
                        ? validSentences.get(i).substring(0, 2) : "";
                if (prev.equals(curr)) {
                    consecutive++;
                    if (consecutive > maxConsecutive) {
                        maxConsecutive = consecutive;
                        patternStart = prev;
                    }
                } else {
                    consecutive = 1;
                }
            }
            if (maxConsecutive >= AntiSlopConfig.LIST_CONSECUTIVE_THRESHOLD) {
                r.details.add(String.format("连续%d句以\"%s\"开头，呈列表式结构", maxConsecutive, patternStart));
                issueCount++;
                totalConfidence += 0.45;
            }
        }

        // 4. 排比句式检测
        for (Pattern p : AntiSlopConfig.PARALLEL_PATTERNS) {
            Matcher m = p.matcher(content);
            while (m.find()) {
                String snippet = extractSnippet(content, m.start(), 60);
                r.details.add("排比句式: " + truncate(snippet, 50));
                issueCount++;
                totalConfidence += 0.55;
            }
        }

        // 定档
        if (issueCount >= 5) {
            r.severity = Severity.FAIL;
        } else if (issueCount >= 2) {
            r.severity = Severity.WARN;
        } else {
            r.severity = Severity.PASS;
        }

        r.score = Math.min(100, totalConfidence * 30);
        r.summary = issueCount > 0
                ? String.format("发现%d处句式套路问题", issueCount)
                : "未发现句式套路";

        return r;
    }

    // ========================================================================
    // Gate C - 心理外化检测
    // ========================================================================

    private static GateResult gateC_PsychExternalization(String content, Set<String> whitelist) {
        GateResult r = new GateResult();
        r.gateName = "Gate C - 心理外化检测";
        r.details = new ArrayList<>();

        double perKFactor = (double) AntiSlopConfig.PER_K_CHARS / Math.max(content.length(), 1);

        // 情绪陈述词统计
        int emotionCount = 0;
        Map<String, Integer> emotionHits = new LinkedHashMap<>();
        for (String word : AntiSlopConfig.EMOTION_STATEMENT_WORDS) {
            if (whitelist.contains(word)) continue;
            int cnt = countOccurrences(content, word);
            if (cnt > 0) {
                emotionCount += cnt;
                emotionHits.put(word, cnt);
            }
        }

        // 抽象心理词统计
        int psychCount = 0;
        Map<String, Integer> psychHits = new LinkedHashMap<>();
        for (String word : AntiSlopConfig.ABSTRACT_PSYCH_WORDS) {
            if (whitelist.contains(word)) continue;
            int cnt = countOccurrences(content, word);
            if (cnt > 0) {
                psychCount += cnt;
                psychHits.put(word, cnt);
            }
        }

        double totalPerK = (emotionCount + psychCount) * perKFactor;

        // 记录详情
        emotionHits.forEach((w, c) -> r.details.add(String.format("情绪陈述 \"%s\" ×%d", w, c)));
        psychHits.forEach((w, c) -> r.details.add(String.format("抽象心理词 \"%s\" ×%d", w, c)));

        // 定档
        if (totalPerK > AntiSlopConfig.PSYCH_SEVERE_PER_K) {
            r.severity = Severity.FAIL;
        } else if (totalPerK > AntiSlopConfig.PSYCH_SEVERE_PER_K * 0.5) {
            r.severity = Severity.WARN;
        } else {
            r.severity = Severity.PASS;
        }

        r.score = Math.min(100, totalPerK * 10);
        r.summary = String.format("心理外化词 %.1f次/千字（情绪陈述%d + 抽象心理%d）",
                totalPerK, emotionCount, psychCount);

        return r;
    }

    // ========================================================================
    // Gate D - 节奏检测
    // ========================================================================

    private static GateResult gateD_Rhythm(String content) {
        GateResult r = new GateResult();
        r.gateName = "Gate D - 节奏检测";
        r.details = new ArrayList<>();

        // 按段落分割
        String[] paragraphs = content.split("\\n\\s*\\n|\\n(?=\\S{10,})");
        List<Integer> paraSentenceCounts = new ArrayList<>();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            // 按句号、问号、感叹号分句
            String[] paraSentences = trimmed.split("[。！？]");
            int count = 0;
            for (String s : paraSentences) {
                if (s.trim().length() > 1) count++;
            }
            if (count > 0) paraSentenceCounts.add(count);
        }

        if (paraSentenceCounts.size() < 5) {
            r.severity = Severity.PASS;
            r.score = 0;
            r.summary = "段落数不足，跳过节奏检测";
            return r;
        }

        // 计算段落句数变异系数 (CV = 标准差 / 均值)
        double mean = paraSentenceCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = paraSentenceCounts.stream()
                .mapToDouble(c -> Math.pow(c - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double cv = mean > 0 ? stdDev / mean : 0;

        // 检测连续等长段落（连续5段句数相同）
        int maxConsecutiveEqual = 1;
        int consecutiveEqual = 1;
        for (int i = 1; i < paraSentenceCounts.size(); i++) {
            if (Math.abs(paraSentenceCounts.get(i) - paraSentenceCounts.get(i - 1)) <= 1) {
                consecutiveEqual++;
                maxConsecutiveEqual = Math.max(maxConsecutiveEqual, consecutiveEqual);
            } else {
                consecutiveEqual = 1;
            }
        }

        // 平均句长分布
        double avgSentenceLength = 0;
        int totalSentences = 0;
        for (String para : paragraphs) {
            String[] paraSentences = para.trim().split("[。！？]");
            for (String s : paraSentences) {
                String t = s.trim();
                if (t.length() > 1) {
                    avgSentenceLength += t.length();
                    totalSentences++;
                }
            }
        }
        avgSentenceLength = totalSentences > 0 ? avgSentenceLength / totalSentences : 0;

        // 记录详情
        r.details.add(String.format("段落句数变异系数 CV=%.3f（<%.2f为AI特征）",
                cv, AntiSlopConfig.CV_AI_THRESHOLD));
        if (maxConsecutiveEqual >= 5) {
            r.details.add(String.format("连续%d段等长段落（AI均匀分段特征）", maxConsecutiveEqual));
        }
        r.details.add(String.format("平均句长 %.1f字", avgSentenceLength));

        // 定档
        int issues = 0;
        if (cv < AntiSlopConfig.CV_AI_THRESHOLD) issues++;
        if (maxConsecutiveEqual >= 5) issues++;
        if (avgSentenceLength > 50) issues++;

        if (issues >= 2) {
            r.severity = Severity.FAIL;
        } else if (issues >= 1) {
            r.severity = Severity.WARN;
        } else {
            r.severity = Severity.PASS;
        }

        double cvPenalty = cv < AntiSlopConfig.CV_AI_THRESHOLD
                ? (AntiSlopConfig.CV_AI_THRESHOLD - cv) * 400 : 0;
        double equalPenalty = maxConsecutiveEqual >= 5 ? (maxConsecutiveEqual - 4) * 10 : 0;
        r.score = Math.min(100, cvPenalty + equalPenalty);
        r.summary = String.format("CV=%.3f, 平均句长=%.1f字, 连续等长段落=%d段",
                cv, avgSentenceLength, maxConsecutiveEqual);

        return r;
    }

    // ========================================================================
    // Gate E - 对话检测
    // ========================================================================

    private static GateResult gateE_Dialog(String content, Set<String> whitelist) {
        GateResult r = new GateResult();
        r.gateName = "Gate E - 对话检测";
        r.details = new ArrayList<>();

        // 识别对话行（以引号开头或含引号的行，或含"说/道/问"的行）
        String[] lines = content.split("\\n");
        int dialogLineCount = 0;
        int taggedDialogCount = 0;
        int bannedPrefixCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 判断是否为对话行
            boolean isDialog = trimmed.contains("\"") || trimmed.contains("\"")
                    || trimmed.contains("「") || trimmed.contains("」")
                    || trimmed.contains("'") || trimmed.contains("'");
            if (!isDialog) {
                // 也可以用说/道/问等判断
                isDialog = trimmed.contains("说") || trimmed.contains("道")
                        || trimmed.contains("问") || trimmed.contains("答");
            }
            if (!isDialog) continue;

            dialogLineCount++;

            // 检查对话标签
            Matcher tagMatcher = AntiSlopConfig.DIALOG_TAG_PATTERN.matcher(trimmed);
            if (tagMatcher.find()) {
                taggedDialogCount++;
            }

            // 检查禁止的对话前后缀
            for (Pattern p : AntiSlopConfig.DIALOG_PREFIX_PATTERNS) {
                Matcher pm = p.matcher(trimmed);
                if (pm.find()) {
                    bannedPrefixCount++;
                    r.details.add("禁止对话前后缀: " + truncate(trimmed, 50));
                    break; // 每行只计一次
                }
            }
        }

        double tagRatio = dialogLineCount > 0
                ? (double) taggedDialogCount / dialogLineCount : 0;

        // 定档
        int issues = 0;
        if (tagRatio > AntiSlopConfig.DIALOG_TAG_AI_RATIO) issues++;
        if (bannedPrefixCount > 0) issues += bannedPrefixCount;

        if (bannedPrefixCount >= 3 || (tagRatio > 0.7 && dialogLineCount > 5)) {
            r.severity = Severity.FAIL;
        } else if (tagRatio > AntiSlopConfig.DIALOG_TAG_AI_RATIO || bannedPrefixCount > 0) {
            r.severity = Severity.WARN;
        } else {
            r.severity = Severity.PASS;
        }

        r.details.add(String.format("对话行%d, 带标签%d (%.0f%%), 禁止前缀%d处",
                dialogLineCount, taggedDialogCount, tagRatio * 100, bannedPrefixCount));
        r.score = Math.min(100, tagRatio * 80 + bannedPrefixCount * 15);
        r.summary = String.format("对话标签占比 %.0f%%（>%.0f%%为AI味）",
                tagRatio * 100, AntiSlopConfig.DIALOG_TAG_AI_RATIO * 100);

        return r;
    }

    // ========================================================================
    // Gate F - 结尾检测
    // ========================================================================

    private static GateResult gateF_Ending(String content, Set<String> whitelist) {
        GateResult r = new GateResult();
        r.gateName = "Gate F - 结尾检测";
        r.details = new ArrayList<>();

        // 取最后 N 字
        int checkLen = AntiSlopConfig.ENDING_CHECK_CHARS;
        String ending = content.length() > checkLen
                ? content.substring(content.length() - checkLen)
                : content;

        // 取最后一段
        String[] paragraphs = content.split("\\n\\s*\\n|\\n(?=\\S)");
        String lastParagraph = paragraphs.length > 0
                ? paragraphs[paragraphs.length - 1].trim() : "";

        int issues = 0;

        // 1. 章末总结体
        for (Pattern p : AntiSlopConfig.ENDING_SUMMARY_PATTERNS) {
            Matcher m = p.matcher(ending);
            while (m.find()) {
                String snippet = extractSnippet(ending, m.start(), 50);
                r.details.add("章末总结体: " + truncate(snippet, 50));
                issues++;
            }
        }

        // 2. 升华式感叹
        for (Pattern p : AntiSlopConfig.ENDING_ELEVATION_PATTERNS) {
            Matcher m = p.matcher(lastParagraph);
            while (m.find()) {
                String snippet = extractSnippet(lastParagraph, m.start(), 50);
                r.details.add("哲理收尾: " + truncate(snippet, 50));
                issues++;
            }
        }

        // 3. 伏笔式预告
        for (Pattern p : AntiSlopConfig.ENDING_FORESHADOW_PATTERNS) {
            Matcher m = p.matcher(ending);
            while (m.find()) {
                String snippet = extractSnippet(ending, m.start(), 60);
                r.details.add("伏笔式预告: " + truncate(snippet, 60));
                issues++;
            }
        }

        // 定档
        if (issues >= 2) {
            r.severity = Severity.FAIL;
        } else if (issues >= 1) {
            r.severity = Severity.WARN;
        } else {
            r.severity = Severity.PASS;
        }

        r.score = Math.min(100, issues * 40);
        r.summary = issues > 0
                ? String.format("章末发现%d处AI污染句式", issues)
                : "章末未发现AI污染";

        return r;
    }

    // ========================================================================
    // Gate G - 解释腔检测
    // ========================================================================

    private static GateResult gateG_ExplanationTone(String content, Set<String> whitelist) {
        GateResult r = new GateResult();
        r.gateName = "Gate G - 解释腔检测";
        r.details = new ArrayList<>();

        double perKFactor = (double) AntiSlopConfig.PER_K_CHARS / Math.max(content.length(), 1);

        int totalIssues = 0;

        // 1. 叙述者下结论
        int conclusionCount = 0;
        for (String word : AntiSlopConfig.NARRATOR_CONCLUSION_WORDS) {
            if (whitelist.contains(word)) continue;
            int cnt = countOccurrences(content, word);
            if (cnt > 0) {
                conclusionCount += cnt;
                r.details.add(String.format("叙述者下结论 \"%s\" ×%d", word, cnt));
            }
        }
        totalIssues += conclusionCount;

        // 2. 元叙事侵入
        int metaCount = 0;
        for (String word : AntiSlopConfig.META_NARRATIVE_WORDS) {
            if (whitelist.contains(word)) continue;
            int cnt = countOccurrences(content, word);
            if (cnt > 0) {
                metaCount += cnt;
                r.details.add(String.format("元叙事侵入 \"%s\" ×%d", word, cnt));
            }
        }
        totalIssues += metaCount * 2; // 元叙事权重加倍

        // 3. 上帝感全知评论
        int omniscientCount = 0;
        for (Pattern p : AntiSlopConfig.OMNISCIENT_PATTERNS) {
            Matcher m = p.matcher(content);
            while (m.find()) {
                String snippet = extractSnippet(content, m.start(), 60);
                r.details.add("上帝感评论: " + truncate(snippet, 50));
                omniscientCount++;
            }
        }
        totalIssues += omniscientCount * 2;

        // 定档
        double density = totalIssues * perKFactor;
        if (density > 5) {
            r.severity = Severity.FAIL;
        } else if (density > 2) {
            r.severity = Severity.WARN;
        } else {
            r.severity = Severity.PASS;
        }

        r.score = Math.min(100, density * 15);
        r.summary = String.format("解释腔词 %.1f次/千字（结论%d + 元叙事%d + 上帝感%d）",
                density, conclusionCount, metaCount, omniscientCount);

        return r;
    }

    // ========================================================================
    // 综合评分与定档
    // ========================================================================

    /**
     * 计算综合AI评分 0-100。
     * 各Gate加权汇总：A=30%, B=15%, C=15%, D=10%, E=15%, F=10%, G=15%
     */
    private static double computeOverallScore(DiagnosisResult result) {
        if (result.gateResults.isEmpty()) return 0;

        // 各Gate权重
        double[] weights = {0.30, 0.15, 0.15, 0.10, 0.15, 0.10, 0.15};
        double weightedSum = 0;
        double totalWeight = 0;

        for (int i = 0; i < Math.min(result.gateResults.size(), weights.length); i++) {
            weightedSum += result.gateResults.get(i).score * weights[i];
            totalWeight += weights[i];
        }

        return totalWeight > 0
                ? Math.min(100, Math.round(weightedSum / totalWeight * 10) / 10.0)
                : 0;
    }

    /**
     * 综合判定：任一重度→FAIL, ≥3项中度→WARN, 否则→PASS。
     */
    private static Severity determineOverallSeverity(DiagnosisResult result) {
        int failCount = 0;
        int warnCount = 0;

        for (GateResult gr : result.gateResults) {
            if (gr.severity == Severity.FAIL) failCount++;
            else if (gr.severity == Severity.WARN) warnCount++;
        }

        if (failCount > 0) {
            return Severity.FAIL;
        }
        if (warnCount >= 3) {
            return Severity.WARN;
        }
        return Severity.PASS;
    }

    /**
     * 提取6项客观指标。
     */
    private static void extractMetrics(String content, DiagnosisResult result) {
        double perKFactor = (double) AntiSlopConfig.PER_K_CHARS / Math.max(content.length(), 1);

        // 指标1: 一级禁用词密度
        int l1Count = 0;
        for (String word : AntiSlopConfig.ALL_LEVEL1_BANNED) {
            l1Count += countOccurrences(content, word);
        }
        result.metricLevel1Density = Math.round(l1Count * perKFactor * 10) / 10.0;

        // 指标2: 最毒句式命中次数
        int fatalHits = 0;
        for (Pattern p : AntiSlopConfig.FATAL_SENTENCE_PATTERNS) {
            Matcher m = p.matcher(content);
            while (m.find()) fatalHits++;
        }
        result.metricFatalSentenceHits = fatalHits;

        // 指标3: 心理外化词密度
        int psychCount = 0;
        for (String word : AntiSlopConfig.EMOTION_STATEMENT_WORDS) {
            psychCount += countOccurrences(content, word);
        }
        for (String word : AntiSlopConfig.ABSTRACT_PSYCH_WORDS) {
            psychCount += countOccurrences(content, word);
        }
        result.metricPsychDensity = Math.round(psychCount * perKFactor * 10) / 10.0;

        // 指标4: 段落句数变异系数
        String[] paragraphs = content.split("\\n\\s*\\n|\\n(?=\\S{10,})");
        List<Integer> counts = new ArrayList<>();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            int c = 0;
            for (String s : trimmed.split("[。！？]")) {
                if (s.trim().length() > 1) c++;
            }
            if (c > 0) counts.add(c);
        }
        if (counts.size() >= 3) {
            double mean = counts.stream().mapToInt(Integer::intValue).average().orElse(0);
            double variance = counts.stream().mapToDouble(c -> Math.pow(c - mean, 2)).average().orElse(0);
            double stdDev = Math.sqrt(variance);
            result.metricParagraphCV = mean > 0
                    ? Math.round(stdDev / mean * 1000) / 1000.0 : 0;
        }

        // 指标5: 对话标签占比
        String[] lines = content.split("\\n");
        int dialogLines = 0;
        int taggedLines = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.contains("说") || t.contains("道") || t.contains("问") || t.contains("答")
                    || t.contains("\"") || t.contains("「")) {
                dialogLines++;
                Matcher tm = AntiSlopConfig.DIALOG_TAG_PATTERN.matcher(t);
                if (tm.find()) taggedLines++;
            }
        }
        result.metricDialogTagRatio = dialogLines > 0
                ? Math.round((double) taggedLines / dialogLines * 100) / 100.0 : 0;

        // 指标6: 解释腔词密度
        int expCount = 0;
        for (String word : AntiSlopConfig.NARRATOR_CONCLUSION_WORDS) {
            expCount += countOccurrences(content, word);
        }
        for (String word : AntiSlopConfig.META_NARRATIVE_WORDS) {
            expCount += countOccurrences(content, word);
        }
        result.metricExplanationDensity = Math.round(expCount * perKFactor * 10) / 10.0;
    }

    // ========================================================================
    // 白名单机制
    // ========================================================================

    /**
     * 从 .deslop-whitelist 文件加载白名单。
     * 文件格式：每行一个词，支持 # 注释。
     *
     * @param whitelistPath 白名单文件路径
     * @return 白名单词集合
     */
    public static Set<String> loadWhitelist(Path whitelistPath) {
        if (whitelistPath == null || !Files.exists(whitelistPath)) {
            return Collections.emptySet();
        }
        try {
            List<String> lines = Files.readAllLines(whitelistPath);
            Set<String> whitelist = new HashSet<>();
            for (String line : lines) {
                String trimmed = line.trim();
                // 跳过空行和注释
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                whitelist.add(trimmed);
            }
            log.info("加载白名单: {} 项 from {}", whitelist.size(), whitelistPath);
            return Collections.unmodifiableSet(whitelist);
        } catch (IOException e) {
            log.warn("加载白名单失败: {} — {}", whitelistPath, e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 构建有效白名单 = 默认白名单 + 用户白名单。
     */
    private static Set<String> buildEffectiveWhitelist(Set<String> userWhitelist) {
        if (userWhitelist == null || userWhitelist.isEmpty()) {
            return AntiSlopConfig.DEFAULT_WHITELIST;
        }
        Set<String> merged = new HashSet<>(AntiSlopConfig.DEFAULT_WHITELIST);
        merged.addAll(userWhitelist);
        return Collections.unmodifiableSet(merged);
    }

    // ========================================================================
    // 3-Pass 修复Prompt生成
    // ========================================================================

    /**
     * 生成3-Pass修复Prompt。
     * <p>
     * Pass 1: 去泛化 — 删抽象总结/假深度/意义膨胀<br>
     * Pass 2: 去书面化 — 替换书面连词/专业术语，对话口语化<br>
     * Pass 3: 回自然感 — 补充感官细节/角色差异/节奏变化
     * </p>
     *
     * @param result 诊断结果
     * @param content 原始正文
     * @return 完整的3-Pass修复Prompt
     */
    public static String generateFixPrompt(DiagnosisResult result, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 去AI味修复任务（3-Pass）\n\n");
        sb.append(String.format("当前AI评分: %.0f/100 | 严重度: %s\n\n",
                result.aiScore, result.overallSeverity.label));

        // 汇总各Gate问题
        List<GateResult> problemGates = result.gateResults.stream()
                .filter(g -> g.severity != Severity.PASS)
                .collect(Collectors.toList());

        if (problemGates.isEmpty()) {
            sb.append("未检测到明显AI痕迹，无需修复。\n");
            return sb.toString();
        }

        sb.append("### 检测到的问题Gate\n\n");
        for (GateResult g : problemGates) {
            sb.append(String.format("- **%s** [%s] (评分%.0f): %s\n",
                    g.gateName, g.severity.label, g.score, g.summary));
        }

        // ===== Pass 1: 去泛化 =====
        sb.append("\n---\n");
        sb.append("### Pass 1: 去泛化\n\n");
        sb.append("删除所有抽象总结、假深度、意义膨胀的表达。\n\n");

        boolean hasPsych = problemGates.stream().anyMatch(g -> g.gateName.contains("Gate C"));
        boolean hasExplanation = problemGates.stream().anyMatch(g -> g.gateName.contains("Gate G"));
        boolean hasEnding = problemGates.stream().anyMatch(g -> g.gateName.contains("Gate F"));

        if (hasPsych) {
            sb.append("- 将\"他觉得/感到/认为\"等心理陈述改为具体动作或感官反应\n");
            sb.append("- 删除\"心中/内心/心底\"等抽象心理词，用身体反应替代\n");
        }
        if (hasExplanation) {
            sb.append("- 删除叙述者下结论的句子（\"显然/当然/毕竟\"）\n");
            sb.append("- 删除元叙事侵入（\"本章/伏笔/读者\"等词不应出现在正文中）\n");
            sb.append("- 删除上帝感全知评论，让人物行为自己说话\n");
        }
        if (hasEnding) {
            sb.append("- 删除章末总结体和哲理收尾，用戛然而止的动作或留白替代\n");
            sb.append("- 删除\"他不知道的是...\"等伏笔式预告\n");
        }
        sb.append("- 删除\"不是A而是B\"句式，用直述句替代\n");

        // ===== Pass 2: 去书面化 =====
        sb.append("\n### Pass 2: 去书面化\n\n");
        sb.append("替换书面语表达，让文字更接近口语自然节奏。\n\n");
        sb.append("- 替换书面连词（\"因此/于是/然而/此外\" → 省略或用动作过渡）\n");
        sb.append("- 对话中去掉\"xxx缓缓说道/xxx轻声问\"等模板标签，用动作+纯对话\n");
        sb.append("- 替换\"仿佛/犹如/宛若\"等文艺腔比喻，用白描动作\n");
        sb.append("- 打破列表式结构和排比句式\n");

        // ===== Pass 3: 回自然感 =====
        sb.append("\n### Pass 3: 回自然感\n\n");
        sb.append("补充细节让文字恢复人手写作的自然质感。\n\n");

        boolean hasRhythm = problemGates.stream().anyMatch(g -> g.gateName.contains("Gate D"));
        boolean hasDialog = problemGates.stream().anyMatch(g -> g.gateName.contains("Gate E"));

        if (hasRhythm) {
            sb.append("- 段落长度参差不齐（1句→6句不定），避免均匀段落\n");
        }
        if (hasDialog) {
            sb.append("- 每个角色的对话要有辨识度（口头禅、句式、敬语习惯各不同）\n");
            sb.append("- 减少\"xxx说/道\"标签，用动作描写替代对话归属\n");
        }
        sb.append("- 每500字至少1个非视觉感官细节（气味、触感、温度、声音）\n");
        sb.append("- 添加具体生活化细节（茶凉了、鞋底沾泥、铜镜蒙尘）\n");
        sb.append("- 人物需要有微动作（抿嘴、攥拳、眼皮跳、喉结滚动）\n\n");

        // 附上原始正文
        sb.append("### 待修复正文\n\n");
        sb.append(content).append("\n\n");
        sb.append("请按照Pass 1→Pass 2→Pass 3的顺序逐轮修复，直接输出最终修复后的完整正文。");

        return sb.toString();
    }

    // ========================================================================
    // 输出辅助方法
    // ========================================================================

    /**
     * 生成注入 Writer/Guardian/Polisher prompt 的上下文块。
     * 保持向后兼容 — 旧版调用者仍可使用此方法。
     */
    public static String toContextBlock(DiagnosisResult result) {
        if (result.findings.isEmpty()) {
            return "Anti-AI 预检：7-Gate未检测到明显 AI 痕迹。\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Anti-AI 预检（7-Gate规则引擎，零LLM成本）\n\n");
        sb.append(String.format("AI评分: %.0f/100 | 严重度: %s | 发现 %d 项\n\n",
                result.aiScore, result.overallSeverity.label, result.totalFindings));

        // 各Gate摘要
        for (GateResult g : result.gateResults) {
            String icon = g.severity == Severity.PASS ? "[PASS]"
                    : g.severity == Severity.WARN ? "[WARN]" : "[FAIL]";
            sb.append(String.format("%s %s: %s\n", icon, g.gateName, g.summary));
            for (String d : g.details) {
                sb.append("    - ").append(d).append("\n");
            }
        }

        sb.append("\n### 6项客观指标\n");
        sb.append(String.format("- 一级禁用词密度: %.1f/千字\n", result.metricLevel1Density));
        sb.append(String.format("- 最毒句式命中: %d处\n", result.metricFatalSentenceHits));
        sb.append(String.format("- 心理外化词密度: %.1f/千字\n", result.metricPsychDensity));
        sb.append(String.format("- 段落CV值: %.3f\n", result.metricParagraphCV));
        sb.append(String.format("- 对话标签占比: %.0f%%\n", result.metricDialogTagRatio * 100));
        sb.append(String.format("- 解释腔密度: %.1f/千字\n", result.metricExplanationDensity));

        sb.append("\n### 修复建议\n");
        for (Finding f : result.findings) {
            sb.append(String.format("- [%s|%s] %s\n", f.gate, f.category, f.description));
            if (f.suggestion != null && !f.suggestion.isEmpty()) {
                sb.append("  → ").append(f.suggestion).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 向后兼容 — 旧版 analyze() 返回的 Report 接口。
     * @deprecated 请使用 {@link #analyze(String)} 返回 DiagnosisResult
     */
    @Deprecated
    public static Report legacyAnalyze(String content) {
        DiagnosisResult dr = analyze(content);
        Report report = new Report();
        report.totalFindings = dr.totalFindings;
        report.aiScore = dr.aiScore;
        report.passed = dr.passed;

        for (Finding f : dr.findings) {
            Report.Finding lf = new Report.Finding();
            lf.category = f.category;
            lf.severity = f.severity;
            lf.description = f.description;
            lf.suggestion = f.suggestion;
            lf.confidence = f.confidence;
            report.findings.add(lf);
        }
        return report;
    }

    // ========================================================================
    // 向后兼容的内部类 — 旧版 Report/Finding
    // ========================================================================

    /**
     * @deprecated 请使用 {@link DiagnosisResult}
     */
    @Deprecated
    public static class Report {
        public int totalFindings;
        public double aiScore;
        public List<Report.Finding> findings = new ArrayList<>();
        public boolean passed;

        /**
         * @deprecated 请使用 {@link Finding}
         */
        @Deprecated
        public static class Finding {
            public String category;
            public String severity;
            public String description;
            public String suggestion;
            public double confidence;
        }
    }

    /**
     * 向后兼容 — 旧版 toContextBlock(Report)。
     * @deprecated 请使用 {@link #toContextBlock(DiagnosisResult)}
     */
    @Deprecated
    public static String toContextBlock(Report report) {
        if (report.findings.isEmpty()) {
            return "Anti-AI 预检：未检测到明显 AI 痕迹。\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Anti-AI 预检（规则引擎，零成本）\n\n");
        sb.append(String.format("AI评分: %.0f/100 | 发现 %d 项\n\n", report.aiScore, report.totalFindings));
        for (Report.Finding f : report.findings) {
            sb.append(String.format("- [%s] %s: %s\n", f.severity.toUpperCase(), f.category, f.description));
            if (f.suggestion != null && !f.suggestion.isEmpty()) {
                sb.append("  → ").append(f.suggestion).append("\n");
            }
        }
        return sb.toString();
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private static List<Finding> extractFindings(GateResult gate, String gateLabel) {
        List<Finding> list = new ArrayList<>();
        if (gate.details == null) return list;

        for (String detail : gate.details) {
            Finding f = new Finding();
            f.gate = gateLabel;
            f.category = gate.gateName;
            f.severity = gate.severity == Severity.FAIL ? "severe"
                    : gate.severity == Severity.WARN ? "warning" : "info";
            f.description = detail;
            f.suggestion = generateSuggestion(gate.gateName, detail);
            f.confidence = gate.severity == Severity.FAIL ? 0.85
                    : gate.severity == Severity.WARN ? 0.55 : 0.30;
            list.add(f);
        }
        return list;
    }

    private static String generateSuggestion(String gateName, String detail) {
        if (gateName.contains("禁用词检测")) {
            return "用具体动作、感官描写替代禁用词，让描写具象化";
        }
        if (gateName.contains("句式套路")) {
            return "变换句式结构，用白描直述替代套路化表达";
        }
        if (gateName.contains("心理外化")) {
            return "将内心活动通过表情、动作、对话外化，而非直接陈述";
        }
        if (gateName.contains("节奏")) {
            return "打破均匀段落，用长短句交替创造自然节奏";
        }
        if (gateName.contains("对话")) {
            return "减少对话标签，用动作描写和语气词赋予角色辨识度";
        }
        if (gateName.contains("结尾")) {
            return "用戛然而止的动作或留白替代总结/升华/预告";
        }
        if (gateName.contains("解释腔")) {
            return "删除叙述者评论，让情节和动作自己说话";
        }
        return "请根据具体情况修改";
    }

    private static String extractSnippet(String content, int pos, int window) {
        int start = Math.max(0, pos - window / 2);
        int end = Math.min(content.length(), pos + window);
        return content.substring(start, end).replace("\n", " ");
    }

    private static boolean hasPhysicalEvidence(String text) {
        return EVIDENCE_KEYWORDS.stream().anyMatch(text::contains);
    }

    static int countOccurrences(String text, String word) {
        if (word.isEmpty()) return 0;
        Pattern pattern = Pattern.compile("(?U)\\b" + Pattern.quote(word) + "\\b");
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}
