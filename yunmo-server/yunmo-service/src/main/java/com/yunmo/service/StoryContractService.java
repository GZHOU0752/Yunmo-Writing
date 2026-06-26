package com.yunmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.common.enums.ContractType;
import com.yunmo.domain.entity.StoryContract;
import com.yunmo.domain.repository.StoryContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yunmo.service.outline.OutlineNodeService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 故事合同服务 — 三层合同架构的生成与查询
 *
 * 合同层级：
 *   MASTER（全书合同）→ VOLUME（卷级合同）→ CHAPTER（章级合同）
 *
 * 参考 webnovel-writer 的 Story System 与题材路由机制
 */
@Service
public class StoryContractService {

    private static final Logger log = LoggerFactory.getLogger(StoryContractService.class);

    private final StoryContractRepository contractRepo;
    private final GenrePackService genrePackService;
    private final OutlineNodeService outlineNodeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StoryContractService(StoryContractRepository contractRepo,
                                GenrePackService genrePackService,
                                OutlineNodeService outlineNodeService) {
        this.contractRepo = contractRepo;
        this.genrePackService = genrePackService;
        this.outlineNodeService = outlineNodeService;
    }

    /**
     * 生成全书合同（MASTER）
     * 从体裁规则包中提取核心承诺、禁用模式、追读法则，
     * 组合为全书写作的最高约束
     *
     * @param novelId 小说ID
     * @param genreId 体裁ID（如 xianxia, wuxia, dushi）
     * @param tropes  附加的叙事范式标签（可选）
     * @return 生成的 MASTER 合同
     */
    @Transactional
    public StoryContract createMasterContract(String novelId, String genreId, String[] tropes) {
        log.info("[合同] 生成全书合同: novel={}, genre={}, tropes={}",
                novelId, genreId, tropes != null ? Arrays.toString(tropes) : "[]");

        // 废除旧版 MASTER 合同
        contractRepo.supersedeAllActive(novelId, ContractType.MASTER);

        StoryContract contract = new StoryContract();
        contract.setNovelId(novelId);
        contract.setContractType(ContractType.MASTER);

        // ── 从体裁规则包构建约束 ──
        Map<String, Object> genrePack = genrePackService.getPack(genreId);
        List<String> corePromises = genrePackService.getCorePromises(genreId);
        List<Map<String, String>> forbiddenPatterns = genrePackService.getForbiddenPatterns(genreId);
        List<String> readerPullRules = genrePackService.getReaderPullRules(genreId);

        // 核心约束：调性 + 体裁规则 + 禁忌
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("genre_id", genreId);
        constraints.put("genre_name", genrePack.getOrDefault("name", genreId));
        constraints.put("core_promises", corePromises);
        constraints.put("tropes", tropes != null ? Arrays.asList(tropes) : List.of());
        constraints.put("reader_pull_rules", readerPullRules);
        contract.setConstraintsJson(toJson(constraints));

        // 反模式：从体裁规则中提取禁止模式
        List<Map<String, Object>> antiPatterns = forbiddenPatterns.stream()
                .map(fp -> {
                    Map<String, Object> ap = new LinkedHashMap<>();
                    ap.put("pattern", fp.get("pattern"));
                    ap.put("reason", fp.get("reason"));
                    ap.put("severity", "HIGH");
                    return ap;
                })
                .collect(Collectors.toList());
        contract.setAntiPatternsJson(toJson(antiPatterns));

        // 动态上下文：体裁章节节奏 + 连续性检查
        Map<String, Object> dynamicContext = new LinkedHashMap<>();
        dynamicContext.put("chapter_rhythm", genrePackService.getChapterRhythm(genreId));
        dynamicContext.put("continuity_checks", genrePackService.getContinuityChecks(genreId));
        dynamicContext.put("character_archetypes", genrePackService.getCharacterArchetypes(genreId));
        contract.setDynamicContextJson(toJson(dynamicContext));

        // 裁决推理
        contract.setReasoningText(buildMasterReasoning(genreId, corePromises, forbiddenPatterns, tropes));

        // MASTER 合同无结构化节点和禁区（由下层合同细化）
        contract.setMustCoverNodesJson(toJson(List.of()));
        contract.setForbiddenZonesJson(toJson(buildMasterForbiddenZones(forbiddenPatterns)));

        contract.setContractVersion(1);
        contract.setStatus(StoryContract.ContractStatus.ACTIVE);

        StoryContract saved = contractRepo.save(contract);
        log.info("[合同] 全书合同已生成: id={}, genre={}, promises={}, antiPatterns={}",
                saved.getId(), genreId, corePromises.size(), antiPatterns.size());
        return saved;
    }

    /**
     * 生成卷级合同（VOLUME）
     * 继承 MASTER 合同约束，附加卷级剧情弧和节奏要求
     *
     * @param novelId      小说ID
     * @param volumeNumber 卷号
     * @return 生成的 VOLUME 合同
     */
    @Transactional
    public StoryContract createVolumeContract(String novelId, int volumeNumber) {
        log.info("[合同] 生成卷级合同: novel={}, volume={}", novelId, volumeNumber);

        // 加载 MASTER 合同作为父约束
        StoryContract masterContract = contractRepo.findActiveMasterContract(novelId).orElse(null);

        // 废除同卷号的旧版 VOLUME 合同（仅限同卷号，不影响其他卷）
        List<StoryContract> volumeContracts = contractRepo
                .findByNovelIdAndContractTypeOrderByContractVersionDesc(novelId, ContractType.VOLUME);
        for (StoryContract vc : volumeContracts) {
            if (vc.getVolumeNumber() != null && vc.getVolumeNumber() == volumeNumber
                    && vc.getStatus() == StoryContract.ContractStatus.ACTIVE) {
                vc.setStatus(StoryContract.ContractStatus.SUPERSEDED);
                contractRepo.save(vc);
            }
        }

        StoryContract contract = new StoryContract();
        contract.setNovelId(novelId);
        contract.setContractType(ContractType.VOLUME);
        contract.setVolumeNumber(volumeNumber);

        // 继承 MASTER 约束并附加卷级要求
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("parent_contract_id", masterContract != null ? masterContract.getId() : null);
        constraints.put("volume_number", volumeNumber);
        constraints.put("volume_requirements", List.of(
                "本卷剧情必须有明确的起承转合",
                "卷末需设置悬念钩子，引导读者进入下一卷",
                "重要角色在本卷中必须有可感知的成长或变化"
        ));
        contract.setConstraintsJson(toJson(constraints));

        // 继承 MASTER 反模式
        if (masterContract != null && masterContract.getAntiPatternsJson() != null) {
            contract.setAntiPatternsJson(masterContract.getAntiPatternsJson());
        } else {
            contract.setAntiPatternsJson(toJson(List.of()));
        }

        // 动态上下文
        Map<String, Object> dynamicContext = new LinkedHashMap<>();
        dynamicContext.put("volume_number", volumeNumber);
        dynamicContext.put("notes", "卷级合同 — 约束卷内各章的整体走向，不替代单章合同");
        contract.setDynamicContextJson(toJson(dynamicContext));

        // 裁决推理
        contract.setReasoningText(String.format(
                "卷级合同 V%d — 继承全书合同的体裁约束，附加卷级剧情弧要求。卷内各章需在卷级框架内展开。",
                volumeNumber));

        // 卷级无结构化节点
        contract.setMustCoverNodesJson(toJson(List.of()));
        contract.setForbiddenZonesJson(
                masterContract != null ? masterContract.getForbiddenZonesJson() : toJson(List.of()));

        contract.setContractVersion(1);
        contract.setStatus(StoryContract.ContractStatus.ACTIVE);

        StoryContract saved = contractRepo.save(contract);
        log.info("[合同] 卷级合同已生成: id={}, volume={}", saved.getId(), volumeNumber);
        return saved;
    }

    /**
     * 生成章级合同（CHAPTER）
     * 包含 CBN/CPNs/CEN 结构化节点，是写作时最核心的合同层
     *
     * @param novelId       小说ID
     * @param chapterNumber 章号
     * @return 生成的 CHAPTER 合同
     */
    @Transactional
    public StoryContract createChapterContract(String novelId, int chapterNumber) {
        log.info("[合同] 生成章级合同: novel={}, chapter={}", novelId, chapterNumber);

        // 加载 MASTER 合同作为父约束
        StoryContract masterContract = contractRepo.findActiveMasterContract(novelId).orElse(null);

        // 废除旧版 CHAPTER 合同
        List<StoryContract> oldContracts = contractRepo
                .findByNovelIdAndContractTypeOrderByContractVersionDesc(novelId, ContractType.CHAPTER);
        for (StoryContract old : oldContracts) {
            if (old.getChapterNumber() != null && old.getChapterNumber() == chapterNumber
                    && old.getStatus() == StoryContract.ContractStatus.ACTIVE) {
                old.setStatus(StoryContract.ContractStatus.SUPERSEDED);
                contractRepo.save(old);
            }
        }

        StoryContract contract = new StoryContract();
        contract.setNovelId(novelId);
        contract.setContractType(ContractType.CHAPTER);
        contract.setChapterNumber(chapterNumber);

        // ── 从大纲树提取章纲信息，构建结构化节点 ──
        var outlineNodes = outlineNodeService.getTree(novelId);
        var matchedNodes = outlineNodes.stream()
                .filter(n -> n.getChapterNumber() != null && n.getChapterNumber() == chapterNumber)
                .sorted(Comparator.comparing(
                        com.yunmo.domain.entity.OutlineNode::getLevel)
                        .thenComparing(com.yunmo.domain.entity.OutlineNode::getSequenceOrder))
                .toList();

        // 构建 CBN（章节大节点）、CPNs（章节推进节点）、CEN（章节结束节点）
        List<Map<String, Object>> mustCoverNodes = buildChapterNodes(chapterNumber, matchedNodes);

        // 约束
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("parent_contract_id", masterContract != null ? masterContract.getId() : null);
        constraints.put("chapter_number", chapterNumber);
        constraints.put("node_count", mustCoverNodes.size());
        constraints.put("max_must_cover", 4);
        contract.setConstraintsJson(toJson(constraints));

        // 继承 MASTER 反模式
        if (masterContract != null && masterContract.getAntiPatternsJson() != null) {
            contract.setAntiPatternsJson(masterContract.getAntiPatternsJson());
        } else {
            contract.setAntiPatternsJson(toJson(List.of()));
        }

        // 动态上下文
        Map<String, Object> dynamicContext = new LinkedHashMap<>();
        dynamicContext.put("chapter_number", chapterNumber);
        dynamicContext.put("outline_node_count", matchedNodes.size());
        contract.setDynamicContextJson(toJson(dynamicContext));

        // 裁决推理
        contract.setReasoningText(buildChapterReasoning(chapterNumber, mustCoverNodes));

        // 结构化节点（CBN/CPNs/CEN）
        contract.setMustCoverNodesJson(toJson(mustCoverNodes));

        // 禁区 — 章级补充特定禁区
        List<String> forbiddenZones = buildChapterForbiddenZones(masterContract);
        contract.setForbiddenZonesJson(toJson(forbiddenZones));

        contract.setContractVersion(1);
        contract.setStatus(StoryContract.ContractStatus.ACTIVE);

        StoryContract saved = contractRepo.save(contract);
        log.info("[合同] 章级合同已生成: id={}, chapter={}, nodes={}",
                saved.getId(), chapterNumber, mustCoverNodes.size());
        return saved;
    }

    /**
     * 获取当前活跃合同
     *
     * @param novelId       小说ID
     * @param contractType  合同类型字符串（MASTER/VOLUME/CHAPTER/REVIEW）
     * @param chapterNumber 章号（CHAPTER 类型时需要）
     * @return 活跃合同，不存在则返回 null
     */
    public StoryContract getActiveContract(String novelId, String contractType, Integer chapterNumber) {
        ContractType type;
        try {
            type = ContractType.valueOf(contractType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[合同] 无效的合同类型: {}", contractType);
            return null;
        }

        return switch (type) {
            case MASTER -> contractRepo.findActiveMasterContract(novelId).orElse(null);
            case CHAPTER -> {
                if (chapterNumber != null) {
                    yield contractRepo.findActiveChapterContract(novelId, chapterNumber).orElse(null);
                }
                yield null;
            }
            case VOLUME, REVIEW -> contractRepo
                    .findFirstByNovelIdAndContractTypeAndStatusOrderByContractVersionDesc(
                            novelId, type, StoryContract.ContractStatus.ACTIVE)
                    .orElse(null);
        };
    }

    /**
     * 将合同转化为可注入 prompt 的上下文文本
     * 在流水线的 assemble_context 阶段追加到 context_text 中
     *
     * @param contract 故事合同
     * @return 格式化的合同上下文文本
     */
    public String buildContractContext(StoryContract contract) {
        if (contract == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 写作合同 — ").append(contract.getContractType().getDescription()).append("\n\n");

        // ── 核心约束 ──
        Map<String, Object> constraintsMap = parseJsonObject(contract.getConstraintsJson());
        if (!constraintsMap.isEmpty()) {
            sb.append("### 核心约束\n");
            for (Map.Entry<String, Object> entry : constraintsMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof List<?> list && !list.isEmpty()) {
                    sb.append("- **").append(key).append("**: ").append(list).append("\n");
                } else if (value instanceof Map<?, ?> map) {
                    sb.append("- **").append(key).append("**: ").append(map).append("\n");
                } else if (value != null) {
                    sb.append("- **").append(key).append("**: ").append(value).append("\n");
                }
            }
            sb.append("\n");
        }

        // ── 反模式（禁止触碰的写作模式） ──
        List<Map<String, Object>> antiPatterns = parseJsonList(contract.getAntiPatternsJson());
        if (!antiPatterns.isEmpty()) {
            sb.append("### 禁止模式（严禁触碰）\n");
            for (Map<String, Object> ap : antiPatterns) {
                String pattern = String.valueOf(ap.getOrDefault("pattern", ""));
                String reason = String.valueOf(ap.getOrDefault("reason", ""));
                sb.append("- **").append(pattern).append("**：").append(reason).append("\n");
            }
            sb.append("\n");
        }

        // ── 必须覆盖的结构化节点 ──
        List<Map<String, Object>> mustCoverNodes = parseJsonList(contract.getMustCoverNodesJson());
        if (!mustCoverNodes.isEmpty()) {
            sb.append("### 本章必须覆盖的结构化节点\n");
            for (Map<String, Object> node : mustCoverNodes) {
                String type = String.valueOf(node.getOrDefault("type", ""));
                String name = String.valueOf(node.getOrDefault("name", ""));
                String description = String.valueOf(node.getOrDefault("description", ""));
                sb.append("- **[").append(type).append("]** ").append(name);
                if (!description.isEmpty() && !"null".equals(description)) {
                    sb.append("：").append(description);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // ── 禁区 ──
        List<String> forbiddenZones = parseJsonStringList(contract.getForbiddenZonesJson());
        if (!forbiddenZones.isEmpty()) {
            sb.append("### 禁区（严禁出现）\n");
            for (String fz : forbiddenZones) {
                sb.append("- ").append(fz).append("\n");
            }
            sb.append("\n");
        }

        // ── 裁决推理 ──
        if (contract.getReasoningText() != null && !contract.getReasoningText().isBlank()) {
            sb.append("### 推理依据\n");
            sb.append(contract.getReasoningText()).append("\n\n");
        }

        return sb.toString();
    }

    // ========== 私有方法：节点构建 ==========

    /**
     * 构建章级结构化节点（CBN/CPNs/CEN）
     * 从大纲树节点中提取，不足时自动生成默认节点
     *
     * @param chapterNumber 章号
     * @param outlineNodes  该章关联的大纲节点
     * @return 结构化节点列表（最多4个）
     */
    private List<Map<String, Object>> buildChapterNodes(int chapterNumber,
                                                         List<com.yunmo.domain.entity.OutlineNode> outlineNodes) {
        List<Map<String, Object>> nodes = new ArrayList<>();

        if (!outlineNodes.isEmpty()) {
            // 从大纲节点中提取结构化信息
            for (int i = 0; i < Math.min(outlineNodes.size(), 4); i++) {
                var node = outlineNodes.get(i);
                Map<String, Object> coverNode = new LinkedHashMap<>();
                // 第一个节点是 CBN（章节大节点），后续是 CPN（章节推进节点）
                String type = (i == 0) ? "CBN" : "CPN";
                coverNode.put("type", type);
                coverNode.put("name", node.getTitle() != null ? node.getTitle() : "节点" + (i + 1));
                coverNode.put("description", node.getOutlineContent() != null
                        ? node.getOutlineContent() : "");
                coverNode.put("causal_sentence", node.getCausalSentence() != null
                        ? node.getCausalSentence() : "");
                nodes.add(coverNode);
            }
        } else {
            // 大纲节点为空时生成默认节点
            Map<String, Object> cbn = new LinkedHashMap<>();
            cbn.put("type", "CBN");
            cbn.put("name", "第" + chapterNumber + "章核心事件");
            cbn.put("description", "本章最重要的叙事事件，需在前500字内铺垫");
            cbn.put("causal_sentence", "");
            nodes.add(cbn);
        }

        // 确保至少1个CPN（若从大纲中只提取了1个CBN）
        if (nodes.size() < 2) {
            Map<String, Object> cpn = new LinkedHashMap<>();
            cpn.put("type", "CPN");
            cpn.put("name", "剧情推进点");
            cpn.put("description", "推进主线或支线的一个关键步骤");
            cpn.put("causal_sentence", "");
            nodes.add(cpn);
        }

        // 添加 CEN（章节结束节点）
        Map<String, Object> cen = new LinkedHashMap<>();
        cen.put("type", "CEN");
        cen.put("name", "章节结束状态");
        cen.put("description", "章末应营造悬念或情绪落点，使读者产生追读欲望");
        cen.put("causal_sentence", "");
        nodes.add(cen);

        // 限制最多4个节点（含CEN）
        if (nodes.size() > 4) {
            nodes = nodes.subList(0, 4);
        }

        return nodes;
    }

    /**
     * 构建章级禁区列表
     */
    private List<String> buildChapterForbiddenZones(StoryContract masterContract) {
        List<String> zones = new ArrayList<>();

        // 继承 MASTER 合同禁区
        if (masterContract != null && masterContract.getForbiddenZonesJson() != null) {
            List<String> masterZones = parseJsonStringList(masterContract.getForbiddenZonesJson());
            zones.addAll(masterZones);
        }

        // 章级补充禁区
        zones.add("章节结尾不能用'未完待续'或类似元叙事标记");
        zones.add("不能在单章内引入超过3个新角色");
        zones.add("不能出现大段说教或世界观解说（>200字）");

        // 限制最多5条
        if (zones.size() > 5) {
            zones = zones.subList(0, 5);
        }

        return zones;
    }

    /**
     * 构建 MASTER 合同的裁决推理文本
     */
    private String buildMasterReasoning(String genreId,
                                         List<String> corePromises,
                                         List<Map<String, String>> forbiddenPatterns,
                                         String[] tropes) {
        StringBuilder sb = new StringBuilder();
        sb.append("全书合同推理：\n");
        sb.append("- 体裁: ").append(genreId).append("\n");
        sb.append("- 核心承诺数: ").append(corePromises.size()).append("\n");
        sb.append("- 禁用模式数: ").append(forbiddenPatterns.size()).append("\n");

        if (tropes != null && tropes.length > 0) {
            sb.append("- 叙事范式: ").append(String.join("、", tropes)).append("\n");
        }

        sb.append("\n本合同定义了全书的写作边界。所有卷级和章级合同必须在此边界内运作。");
        sb.append("违反核心承诺或触碰禁用模式将触发 AdversarialEdit 阶段的整章重写。");

        return sb.toString();
    }

    /**
     * 构建章级合同的裁决推理文本
     */
    private String buildChapterReasoning(int chapterNumber, List<Map<String, Object>> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("第").append(chapterNumber).append("章合同推理：\n");

        long cbnCount = nodes.stream().filter(n -> "CBN".equals(n.get("type"))).count();
        long cpnCount = nodes.stream().filter(n -> "CPN".equals(n.get("type"))).count();
        long cenCount = nodes.stream().filter(n -> "CEN".equals(n.get("type"))).count();

        sb.append("- CBN（章节大节点）: ").append(cbnCount).append("个\n");
        sb.append("- CPN（章节推进节点）: ").append(cpnCount).append("个\n");
        sb.append("- CEN（章节结束节点）: ").append(cenCount).append("个\n");

        sb.append("\n本章必须覆盖上述结构化节点。");
        sb.append("CBN 是本章核心事件，必须在正文中明确呈现。");
        sb.append("CPNs 是推进步骤，顺序可调整但不能遗漏。");
        sb.append("CEN 是章末状态，必须在章节末尾达成。");

        return sb.toString();
    }

    /**
     * 从 MASTER 的反模式中构建禁区列表
     */
    private List<String> buildMasterForbiddenZones(List<Map<String, String>> forbiddenPatterns) {
        List<String> zones = new ArrayList<>();
        for (Map<String, String> fp : forbiddenPatterns) {
            String pattern = fp.get("pattern");
            if (pattern != null && !pattern.isBlank()) {
                zones.add(pattern);
            }
        }
        // 通用禁区
        zones.add("严禁注水：不能用大量无意义对话或环境描写填充字数");
        // 限制最多5条
        if (zones.size() > 5) {
            zones = zones.subList(0, 5);
        }
        return zones;
    }

    // ========== JSON 序列化/反序列化工具 ==========

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.warn("[合同] JSON解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("[合同] JSON对象解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.warn("[合同] JSON解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[合同] JSON序列化失败: {}", e.getMessage());
            return "[]";
        }
    }
}
