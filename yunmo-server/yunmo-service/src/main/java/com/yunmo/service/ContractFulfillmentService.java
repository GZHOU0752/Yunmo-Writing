package com.yunmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.domain.dto.FulfillmentResult;
import com.yunmo.domain.entity.StoryContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 合同履约检查服务 — 验证章节是否完成了合同要求
 *
 * 检查流程:
 *   1. 节点覆盖对比 — planned vs covered vs missed vs extra
 *   2. 禁区扫描 — 使用关键词/正则匹配检查 forbiddenZones
 *   3. 履约分数计算 — 0-100 分，低于60为不通过
 *
 * 参考 webnovel-writer 的 Story System 履约检查机制
 */
@Service
public class ContractFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(ContractFulfillmentService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 履约通过的分数阈值 */
    private static final double PASS_THRESHOLD = 60.0;

    /**
     * 检查章节是否完成了合同要求
     *
     * @param contract       故事合同
     * @param chapterContent 章节正文
     * @return 履约结果
     */
    public FulfillmentResult checkFulfillment(StoryContract contract, String chapterContent) {
        if (contract == null) {
            log.debug("[履约检查] 合同为空，跳过检查");
            return FulfillmentResult.pass(null);
        }

        if (chapterContent == null || chapterContent.isBlank()) {
            log.warn("[履约检查] 章节内容为空: contract={}", contract.getId());
            return new FulfillmentResult(contract.getId(), 0, 0, 0, 0,
                    List.of("章节内容为空"), List.of(), 0.0, false);
        }

        log.info("[履约检查] 开始检查: contract={}, type={}, chapter={}, contentLength={}",
                contract.getId(), contract.getContractType(),
                contract.getChapterNumber(), chapterContent.length());

        // ── 1. 节点覆盖对比 ──
        NodeCoverageResult nodeResult = checkNodeCoverage(contract, chapterContent);

        // ── 2. 禁区扫描 ──
        List<String> forbiddenViolations = scanForbiddenZones(contract, chapterContent);

        // ── 3. 计算履约分数 ──
        double score = calculateScore(nodeResult, forbiddenViolations);
        boolean passed = score >= PASS_THRESHOLD && forbiddenViolations.isEmpty();

        FulfillmentResult result = new FulfillmentResult(
                contract.getId(),
                nodeResult.planned,
                nodeResult.covered,
                nodeResult.missed,
                nodeResult.extra,
                nodeResult.missedDescriptions,
                forbiddenViolations,
                score,
                passed
        );

        log.info("[履约检查] 完成: contract={}, planned={}, covered={}, missed={}, violations={}, score={}, passed={}",
                contract.getId(), nodeResult.planned, nodeResult.covered,
                nodeResult.missed, forbiddenViolations.size(), String.format("%.1f", score), passed);

        return result;
    }

    /**
     * 节点覆盖检查
     * 解析合同中的 mustCoverNodesJson，在正文中逐个检索匹配
     */
    private NodeCoverageResult checkNodeCoverage(StoryContract contract, String content) {
        List<Map<String, Object>> mustCoverNodes = parseNodeList(contract.getMustCoverNodesJson());
        if (mustCoverNodes.isEmpty()) {
            return new NodeCoverageResult(0, 0, 0, 0, List.of());
        }

        String cleanContent = normalizeText(content);
        List<String> coveredDescriptions = new ArrayList<>();
        List<String> missedDescriptions = new ArrayList<>();

        for (Map<String, Object> node : mustCoverNodes) {
            String type = String.valueOf(node.getOrDefault("type", ""));
            String name = String.valueOf(node.getOrDefault("name", ""));
            String description = String.valueOf(node.getOrDefault("description", ""));
            String causalSentence = String.valueOf(node.getOrDefault("causal_sentence", ""));

            boolean isCovered = checkNodeInContent(cleanContent, name, description, causalSentence);

            String nodeLabel = "[" + type + "] " + name;
            if (isCovered) {
                coveredDescriptions.add(nodeLabel);
            } else {
                missedDescriptions.add(nodeLabel);
                log.debug("[履约检查] 遗漏节点: {}", nodeLabel);
            }
        }

        int planned = mustCoverNodes.size();
        int covered = coveredDescriptions.size();
        int missed = missedDescriptions.size();

        return new NodeCoverageResult(planned, covered, missed, 0, missedDescriptions);
    }

    /**
     * 检查单个节点是否在正文中被覆盖
     * 使用关键词匹配策略（不做LLM调用以保持性能）
     */
    private boolean checkNodeInContent(String content, String name, String description, String causalSentence) {
        // 策略：节点名称中的关键词在正文中出现即视为覆盖
        // 对中文文本使用字符级匹配
        List<String> keywords = extractKeywords(name, description, causalSentence);

        if (keywords.isEmpty()) {
            // 无关键词时，检查节点名称是否以某种形式出现在正文中
            return content.contains(name) || (name.length() >= 2 && content.contains(name.substring(0, 2)));
        }

        // 至少需要匹配关键词总数的50%才算覆盖
        int matchThreshold = Math.max(1, keywords.size() / 2);
        int matchedCount = 0;

        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                matchedCount++;
                if (matchedCount >= matchThreshold) {
                    return true;
                }
            }
        }

        return matchedCount >= matchThreshold;
    }

    /**
     * 从节点信息中提取关键词
     * CEN 节点放宽匹配条件（章末状态通常是隐含的）
     */
    private List<String> extractKeywords(String name, String description, String causalSentence) {
        Set<String> keywords = new LinkedHashSet<>();

        // 从名称中提取（去掉标点和常见停用词）
        if (name != null && !name.isBlank() && !"null".equals(name)) {
            String cleaned = name.replaceAll("[【】\\[\\]（）()，。！？、\\s]", "");
            if (cleaned.length() >= 2) {
                keywords.add(cleaned);
                // 添加2-gram片段提高匹配率
                if (cleaned.length() >= 4) {
                    keywords.add(cleaned.substring(0, 2));
                    keywords.add(cleaned.substring(cleaned.length() - 2));
                }
            }
        }

        // 从因果句中提取名词性关键词
        if (causalSentence != null && !causalSentence.isBlank() && !"null".equals(causalSentence)) {
            String cleaned = causalSentence.replaceAll("[【】\\[\\]（）()，。！？、\\s]", "");
            if (cleaned.length() >= 2) {
                keywords.add(cleaned);
            }
        }

        return new ArrayList<>(keywords);
    }

    /**
     * 禁区扫描 — 检查正文是否触碰了合同中的禁区
     * 使用字符串包含匹配（对中文禁区模式）
     */
    private List<String> scanForbiddenZones(StoryContract contract, String content) {
        List<String> forbiddenZones = parseStringList(contract.getForbiddenZonesJson());
        if (forbiddenZones.isEmpty()) {
            return List.of();
        }

        String cleanContent = normalizeText(content);
        List<String> violations = new ArrayList<>();

        for (String zone : forbiddenZones) {
            if (isZoneViolated(cleanContent, zone)) {
                violations.add(zone);
                log.warn("[履约检查] 禁区违规: contract={}, zone={}",
                        contract.getId(), truncate(zone, 60));
            }
        }

        return violations;
    }

    /**
     * 检查单个禁区是否被违反
     * 使用关键词 + 模式匹配策略
     */
    private boolean isZoneViolated(String content, String zone) {
        if (zone == null || zone.isBlank()) return false;

        // 策略1：直接子串匹配（适用于明确的禁止措辞）
        // 对于较长禁区描述，提取其中的关键短语
        if (zone.length() <= 20 && content.contains(zone)) {
            return true;
        }

        // 策略2：提取禁区中的关键实词作为触发词
        List<String> triggerWords = extractTriggerWords(zone);
        if (triggerWords.isEmpty()) return false;

        // 如果所有触发词都在正文中出现，则判定为违规
        int matchCount = 0;
        for (String word : triggerWords) {
            if (content.contains(word)) {
                matchCount++;
            }
        }

        // 至少70%的触发词匹配才判定违规（减少误报）
        double matchRate = (double) matchCount / triggerWords.size();
        return matchRate >= 0.7;
    }

    /**
     * 从禁区文本中提取触发词
     */
    private List<String> extractTriggerWords(String zone) {
        // 去掉"不能""严禁""禁止"等指示词，保留核心内容词
        String cleaned = zone
                .replaceAll("不能|严禁|禁止|不许|不允许|不得|不应|不可", "")
                .replaceAll("[，。！？、；：\"\"''【】（）()\\[\\]]", " ")
                .trim();

        if (cleaned.isEmpty()) return List.of();

        return Arrays.stream(cleaned.split("\\s+"))
                .filter(w -> w.length() >= 2)
                .collect(Collectors.toList());
    }

    /**
     * 计算履约分数 0-100
     *
     * 计分规则:
     *   - 节点覆盖率占 60%（全部覆盖得60分）
     *   - 禁区合规占 40%（无违规得40分，每条违规扣15分）
     *   - 最低0分
     */
    private double calculateScore(NodeCoverageResult nodeResult, List<String> violations) {
        double score = 0.0;

        // 节点覆盖率得分（60%权重）
        if (nodeResult.planned > 0) {
            double coverageRate = (double) nodeResult.covered / nodeResult.planned;
            score += coverageRate * 60.0;
        } else {
            score += 60.0; // 无节点要求时满分
        }

        // 禁区合规得分（40%权重）
        // 按最多5条禁区计算，每条违规扣8分
        int maxZones = 5;
        double zoneScore = 40.0 - (violations.size() * (40.0 / maxZones));
        score += Math.max(0, zoneScore);

        return Math.max(0, Math.min(100, score));
    }

    /**
     * 文本归一化 — 移除HTML标签、实体编码、多余空白，用于匹配
     */
    private String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "")
                .replaceAll("&#\\d+;", "")
                .replaceAll("&[a-z]+;", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    // ========== JSON 解析工具 ==========

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseNodeList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[履约检查] 节点JSON解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[履约检查] 字符串JSON解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ========== 内部类：节点覆盖结果 ==========

    /**
     * 节点覆盖对比结果
     */
    private record NodeCoverageResult(
            int planned,
            int covered,
            int missed,
            int extra,
            List<String> missedDescriptions
    ) {}
}
