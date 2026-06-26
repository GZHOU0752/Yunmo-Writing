package com.yunmo.service.style;

import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.domain.entity.Novel;
import com.yunmo.domain.repository.NovelRepository;
import com.yunmo.llm.provider.LLMProvider;
import com.yunmo.llm.provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 文风分析服务
 * 使用 LLM 深度分析参考文本的写作风格
 */
@Service
public class StyleAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StyleAnalysisService.class);

    private final ProviderRegistry providerRegistry;
    private final NovelRepository novelRepo;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public StyleAnalysisService(ProviderRegistry providerRegistry,
                                 NovelRepository novelRepo) {
        this.providerRegistry = providerRegistry;
        this.novelRepo = novelRepo;
    }

    /**
     * AI 文风分析
     * 分析参考文本的文风特征，输出结构化分析结果
     */
    public Mono<Map<String, Object>> analyzeStyle(String novelId, String referenceText) {
        return Mono.fromCallable(() -> {
            if (referenceText == null || referenceText.isBlank()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("status", "error");
                error.put("message", "参考文本不能为空");
                return error;
            }

            LLMProvider provider = providerRegistry.get("deepseek");
            if (provider == null) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("status", "error");
                error.put("message", "LLM 服务不可用");
                return error;
            }

            // 截取文本前 3000 字用于分析
            String text = referenceText.length() > 3000
                    ? referenceText.substring(0, 3000) : referenceText;

            String prompt = String.format("""
                你是一位资深的文学评论家和文风分析师。请深度分析以下文本的写作风格。

                ## 参考文本
                ```
                %s
                ```

                请从以下维度进行分析，并严格按照 JSON 格式输出（不要输出任何其他内容）：

                ```json
                {
                  "style_summary": "整体文风描述（50-80字，概括核心风格特征）",
                  "sentence_pattern": "长句/短句/中句/长短交错",
                  "avg_sentence_length_estimate": 25,
                  "dialogue_ratio_estimate": 30,
                  "tone": "偏古典/偏现代/古今融合/朴实直白/华丽铺陈",
                  "vocabulary_features": ["词汇特点总结1", "词汇特点总结2", "词汇特点总结3"],
                  "rhythm": "节奏描述（如：舒缓细腻/紧凑激烈/张弛有度）",
                  "suitable_genres": ["适合的类型1", "适合的类型2"],
                  "key_techniques": ["突出的写作技法1", "突出的写作技法2"],
                  "sample_sentence": "最能代表本文风的一句话（从文本中选取或仿写）",
                  "tags": ["标签1", "标签2", "标签3", "标签4"]
                }
                ```

                注意：
                - avg_sentence_length_estimate 和 dialogue_ratio_estimate 为数字
                - tags 用中文，如"古风"、"白描"、"意识流"、"爽文"、"偏文艺"等
                - 分析要诚实、精准，不要过度夸赞
                """, text);

            try {
                var response = provider.generate(
                    List.of(LLMMessage.user(prompt)),
                    LLMConfig.precise("deepseek-v4-pro")
                );

                String json = com.yunmo.common.util.JsonExtractor.extractJson(response.content());
                if (json == null || json.isBlank()) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("status", "error");
                    error.put("message", "AI 分析返回格式异常，请重试");
                    return error;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> analysis = objectMapper.readValue(json, Map.class);

                // 安全保存文风到 novel.writingStyle（显式告知调用方这是写操作）
                Object styleObj = analysis.get("style_summary");
                String styleSummary = styleObj instanceof String s ? s : String.valueOf(styleObj);
                if (!styleSummary.isBlank()) {
                    try {
                        Novel novel = novelRepo.findById(novelId).orElse(null);
                        if (novel != null) {
                            novel.setWritingStyle(styleSummary);
                            novelRepo.save(novel);
                            log.info("文风已保存: novel={}, style={}", novelId, styleSummary);
                        }
                    } catch (Exception e) {
                        log.warn("保存文风到 novel 失败：{}", e.getMessage());
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "ok");
                result.put("analysis", analysis);
                log.info("文风分析完成：novel={}, style={}", novelId, styleSummary);
                return result;

            } catch (Exception e) {
                log.error("文风分析失败：{}", e.getMessage());
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("status", "error");
                error.put("message", "分析失败：" + e.getMessage());
                return error;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
