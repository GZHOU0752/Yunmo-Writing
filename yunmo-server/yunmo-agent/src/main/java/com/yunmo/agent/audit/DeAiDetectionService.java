package com.yunmo.agent.audit;

import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.common.dto.LLMResponse;
import com.yunmo.llm.provider.LLMProvider;
import com.yunmo.llm.provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 去 AI 味双层检测服务
 * 第一层：正则硬编码高频 AI 味模式匹配（30+ 模式，权重 0.3）
 * 第二层：LLM 轻量模型判断文本"像人写 vs AI写"（权重 0.7）
 */
@Component
public class DeAiDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DeAiDetectionService.class);
    private final ProviderRegistry providerRegistry;

    public DeAiDetectionService(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    // 30+ 高频 AI 味正则模式（预编译 Pattern 避免重复编译开销）
    private static final List<AiPattern> AI_PATTERNS = List.of(
        new AiPattern("不仅.{1,10}而且", "「不仅...而且...」套话", 3),
        new AiPattern("在.{1,15}的过程中", "「在...的过程中」句式", 3),
        new AiPattern("呈现(出|了)", "「呈现(出)」模板词", 2),
        new AiPattern("值得注意的是", "「值得注意的是」开头", 2),
        new AiPattern("总(的|而|之)来说", "「总(的/而/之)来说」总结词", 2),
        new AiPattern("无(论|论是).{1,10}(还|都)", "「无论...还/都...」让步句式", 2),
        new AiPattern("这(不仅|不单单|不只)", "「这(不仅/不单单)」递进句式", 2),
        new AiPattern("从某种(程度|意义)上", "「从某种(程度/意义)上」模糊表达", 2),
        new AiPattern("不可否认", "「不可否认」开头", 1),
        new AiPattern("毫无(疑问|疑义)", "「毫无(疑问/疑义)」强调词", 2),
        new AiPattern("深深地", "「深深地」副词堆叠", 1),
        new AiPattern("不禁.{1,5}(想|问|感叹)", "「不禁...」内心描写模板", 2),
        new AiPattern("内心(深处|深藏)", "「内心(深处/深藏)」套话", 1),
        new AiPattern("仿佛.{1,10}(一般|一样|似的)", "「仿佛...一般/一样」比喻模板", 2),
        new AiPattern("此(刻|时)(，|,)", "「此(刻/时)，」AI 高频衔接词", 2),
        new AiPattern("然而.{1,5}(，|,)", "「然而...，」AI 高频转折", 2),
        new AiPattern("与此同时", "「与此同时」AI 高频并列词", 2),
        new AiPattern("纵观.{1,10}(，|,)", "「纵观...，」总览句式", 1),
        new AiPattern("在这(个|一|片)", "「在这(个/一/片)」场景开头模板", 1),
        new AiPattern("可以(说|看到|看出)", "「可以(说/看到/看出)」解释性表达", 2),
        new AiPattern("极大(地|的)", "「极大(地/的)」夸张修饰", 1),
        new AiPattern("前所未有", "「前所未有」套话修饰", 2),
        new AiPattern("令(人|他)", "「令(人/他)」使役句", 1),
        new AiPattern("注(视|目)着", "「注(视/目)着」AI高频动作词", 1),
        new AiPattern("心中(涌|升|泛)起", "「心中(涌/升/泛)起」情绪描写模板", 2),
        new AiPattern("眼中闪(过|烁)", "「眼中闪(过/烁)」表情模板", 2),
        new AiPattern("嘴角.{1,5}(笑|勾|上扬)", "「嘴角...笑/勾」微表情模板", 2),
        new AiPattern("深吸(一口|了|了)口气", "「深吸(一口/了)口气」动作模板", 2),
        new AiPattern("握(紧|了握)拳", "「握(紧/了握)拳」动作模板", 1),
        new AiPattern("目光.{1,10}(深邃|坚定|温柔|冷)", "「目光...(深邃/坚定/温柔/冷)」描写模板", 2)
    );

    private record AiPattern(String regex, String description, int severity, Pattern compiled) {
        AiPattern(String regex, String description, int severity) {
            this(regex, description, severity, Pattern.compile(regex));
        }
    }

    public RegexResult detectByRegex(String text) {
        List<Map<String, Object>> matches = new ArrayList<>();
        int totalSeverity = 0;

        for (AiPattern pattern : AI_PATTERNS) {
            var matcher = pattern.compiled.matcher(text);
            int count = 0;
            while (matcher.find()) count++;
            if (count > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("pattern", pattern.regex);
                m.put("description", pattern.description);
                m.put("count", count);
                m.put("severity", pattern.severity);
                matches.add(m);
                totalSeverity += pattern.severity * count;
            }
        }

        double rawScore = Math.max(0, 10 - totalSeverity * 0.5);
        double score = Math.round(rawScore * 10.0) / 10.0;

        return new RegexResult(score, matches, totalSeverity);
    }

    public record RegexResult(double score, List<Map<String, Object>> matches, int totalSeverity) {}

    public double detectByLLM(String text) {
        try {
            LLMProvider provider = providerRegistry.get("qwen");
            String sample = text.length() > 2000 ? text.substring(0, 2000) : text;

            LLMResponse response = provider.generate(List.of(
                LLMMessage.system("""
                    你是文本自然度检测专家。阅读以下小说片段，判断它读起来更像"人写的"还是"AI 生成的"。
                    评分标准（0-10分）：
                    - 0-2分：明显的 AI 生成痕迹（大量模板句式、缺乏个性、情感空洞）
                    - 3-4分：有明显 AI 味但有部分人类写作特征
                    - 5-6分：混合特征，难以判断
                    - 7-8分：基本像人写的，偶有模板化表达
                    - 9-10分：完全像人写的，有鲜明个性和自然韵律

                    只输出一个数字（0-10），不要输出其他任何文字。"""),
                LLMMessage.user("请对以下小说片段进行自然度评分：\n\n" + sample)
            ), LLMConfig.precise("qwen-plus"));

            String content = response.content().trim();
            double score = 5.0;
            try {
                score = Double.parseDouble(content.replaceAll("[^0-9.]", ""));
                score = Math.max(0, Math.min(10, score));
            } catch (NumberFormatException ignored) {}
            return score;
        } catch (Exception e) {
            log.warn("LLM 去 AI 味检测失败，使用默认分: {}", e.getMessage());
            return 5.0;
        }
    }

    public Map<String, Object> detect(String text) {
        RegexResult regexResult = detectByRegex(text);
        double llmScore = detectByLLM(text);
        double finalScore = Math.round((regexResult.score * 0.3 + llmScore * 0.7) * 10.0) / 10.0;

        String analysis;
        if (finalScore >= 8) analysis = "文本自然流畅，无明显AI痕迹";
        else if (finalScore >= 6) analysis = "整体自然，偶有模板化表达，可轻微调整";
        else if (finalScore >= 4) analysis = "有明显的AI写作特征，建议进行风格化润色";
        else analysis = "AI痕迹较重，需要大幅改写以增加人味";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", finalScore);
        result.put("regexScore", regexResult.score);
        result.put("llmScore", llmScore);
        result.put("regexMatches", regexResult.matches);
        result.put("analysis", analysis);
        return result;
    }
}
