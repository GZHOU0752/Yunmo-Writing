package com.yunmo.agent.audit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 37维度审计枚举 — 云墨v2审计体系，参考inkos的DIMENSION_LABELS设计
 *
 * <h3>分层模型</h3>
 * <ul>
 *   <li><b>L1 硬阻塞（Crashed Guard）</b> — FAIL → 拒绝入库，结构性缺陷不可放行</li>
 *   <li><b>L2 质量警告（Quality Warn）</b> — FAIL → 降级为WARN，fail-open机制（参考ProseForge LEVEL2_CANNOT_FAIL）</li>
 *   <li><b>L3 合规（Compliance）</b> — FAIL → BLOCK，合规自检不通过，触发compliance_selfcheck阻断</li>
 * </ul>
 *
 * <h3>同人/番外维度（28-37）</h3>
 * 仅在 submission 模式下默认启用，draft/standard 模式下按需开启。
 *
 * @author 云墨团队
 * @since 2.0
 */
public enum AuditDimension {

    // ==================== L1 硬阻塞 (Crashed Guard) — 6维 ====================

    OOC_CHECK(1, "OOC检查", "OOC Check", AuditTier.L1_CRASHED_GUARD, 1.2,
            "角色行为由'过往经历+当前利益+性格底色'三脚驱动，言行必须内洽。检查角色是否存在与设定矛盾的行为、情感反应或决策逻辑。"),
    TIMELINE_CHECK(2, "时间线检查", "Timeline Check", AuditTier.L1_CRASHED_GUARD, 1.0,
            "时间锚点一致性校验：事件发生时间与已建立的时间线是否一致；时间推移是否合理，是否存在跳跃、倒退或不当压缩。"),
    SETTING_CONFLICT(3, "设定冲突", "Setting Conflict", AuditTier.L1_CRASHED_GUARD, 1.0,
            "世界观内部一致性：新出现的设定是否与已建立的设定矛盾。力量体系、地理、社会结构、种族特性等是否自洽。"),
    POWER_SCALE_COLLAPSE(4, "战力崩坏", "Power Scale Collapse", AuditTier.L1_CRASHED_GUARD, 0.9,
            "力量体系内洽性：角色战力是否前后一致，升级逻辑是否合理，是否存在战力膨胀或莫名削弱。力量层级关系是否清晰稳定。"),
    INFO_OVERSTEP(9, "信息越界", "Info Overstep", AuditTier.L1_CRASHED_GUARD, 1.1,
            "角色只能基于自身已知信息行动和思考。检查角色是否使用了超出其信息掌握范围的认知和判断——包括对话内容、战术决策、情感反应等。"),
    INTEREST_CHAIN_BREAK(11, "利益链断裂", "Interest Chain Break", AuditTier.L1_CRASHED_GUARD, 1.0,
            "角色行为驱动力逻辑链完整性：每个角色的关键行为必须有清晰的利益/动机支撑，检查驱动力链是否出现逻辑断裂或动机真空。"),

    // ==================== L2 质量警告 (Quality Warn) — 19维 ====================

    NUMERIC_CHECK(5, "数值检查", "Numeric Check", AuditTier.L2_QUALITY_WARN, 0.7,
            "资源、数量、数值一致性：货币金额、物品数量、时间数值、距离等是否与前后文一致，不存在计算错误或随意变更。"),
    FORESHADOW_CHECK(6, "伏笔检查", "Foreshadow Check", AuditTier.L2_QUALITY_WARN, 0.9,
            "伏笔状态追踪：已埋伏笔是否超期未回收，伏笔埋设和回收是否自洽。超期标记的伏笔需要给出检视建议或回收方案。"),
    PACING_CHECK(7, "节奏检查", "Pacing Check", AuditTier.L2_QUALITY_WARN, 0.8,
            "3-5章小目标周期检查：连续5章无爆发点（冲突升级/情节转折/情感高潮/信息揭露）→ 标记为节奏停滞，需要加速或插入爆发事件。"),
    STYLE_CHECK(8, "文风检查", "Style Check", AuditTier.L2_QUALITY_WARN, 0.7,
            "文本风格与style_guide的一致性：用语习惯、修辞倾向、叙事口吻、句式偏好是否符合设定的风格指南。检测风格漂移。"),
    WORD_FATIGUE(10, "词汇疲劳", "Word Fatigue", AuditTier.L2_QUALITY_WARN, 0.6,
            "高疲劳词密度检测：同一词汇/短语在短距离内重复出现次数。AI标记词（如'仿佛''深深地''内心''不禁'等）每3000字出现≤1次为合理。"),
    ERA_VERIFICATION(12, "年代考据", "Era Verification", AuditTier.L2_QUALITY_WARN, 0.6,
            "真实年代/人物/事件一致性：涉及真实历史年代时，器物、制度、语言习惯、社会风貌等是否符合时代背景。纯虚构世界观此项自动跳过。"),
    SUPPORTING_DUMBING(13, "配角降智", "Supporting Dumbing Down", AuditTier.L2_QUALITY_WARN, 0.8,
            "配角有独立动机和反击能力：配角不应为衬托主角而被降智。检查配角行为是否有合理的自身利益逻辑和正常智力水平的决策。"),
    SUPPORTING_TOOLIFICATION(14, "配角工具人化", "Supporting Toolification", AuditTier.L2_QUALITY_WARN, 0.7,
            "配角有自己的利益算盘：每个配角应有独立于主角的目的和动机，不能仅作为推动主角情节的叙事工具。检查配角是否存在独立行动线。"),
    COOL_POINT_BLUR(15, "爽点虚化", "Cool Point Blur", AuditTier.L2_QUALITY_WARN, 0.9,
            "欲望驱动模型：制造情绪缺口→释放超预期满足。70%满足=失败。检查爽点的设置是否到位、释放是否充分、读者情绪回馈是否达成。"),
    DIALOGUE_DISTORTION(16, "台词失真", "Dialogue Distortion", AuditTier.L2_QUALITY_WARN, 0.8,
            "对话符合说话人身份/情绪/信息掌握：每个角色的台词应反映其社会地位、教育水平、当前情绪状态和所知信息范围，不能千人一面。"),
    CHRONICLE_STYLE(17, "流水账", "Chronicle Style", AuditTier.L2_QUALITY_WARN, 0.7,
            "每段必须带来新信息/态度变化/利益变化：检查段落是否仅做机械的时间推移叙述而无实质内容推进，即'然后...然后...然后...'式流水账。"),
    VIEWPOINT_CONSISTENCY(19, "视角一致性", "Viewpoint Consistency", AuditTier.L2_QUALITY_WARN, 0.8,
            "视角切换过渡是否平滑，是否与设定视角（第一人称/第三人称限知/全知）一致。检查是否出现不当的视角跳跃或头跳（head-hopping）。"),
    PARAGRAPH_EQUAL_LENGTH(20, "段落等长", "Paragraph Equal Length", AuditTier.L2_QUALITY_WARN, 0.5,
            "段落长度变异系数<0.15 → AI生成特征。检查段落长度分布是否过于均匀、缺乏自然节奏变化——人类写作天然具有段落长度波动。"),
    CLICHE_DENSITY(21, "套话密度", "Cliche Density", AuditTier.L2_QUALITY_WARN, 0.6,
            "套话词密度>3次/千字 → 标记。检测高频套话词（如'总的来说''值得注意的是''在...的过程中''不仅...而且...'等）的出现频率。"),
    FORMULAIC_TURN(22, "公式化转折", "Formulaic Turn", AuditTier.L2_QUALITY_WARN, 0.5,
            "转折词重复≥3次 → 标记。检查'然而''但是''却''不过''可是'等转折词在短距离内是否过度重复使用，形成公式化的转折节奏。"),
    LIST_FORMAT(23, "列表式结构", "List Format", AuditTier.L2_QUALITY_WARN, 0.4,
            "连续≥3句相同开头 → 标记。检测段落中是否存在连续多句使用相同句式开头的情况（如连续'他...他...他...'开头），呈现AI列表生成特征。"),
    SUBPLOT_STAGNATION(24, "支线停滞", "Subplot Stagnation", AuditTier.L2_QUALITY_WARN, 0.7,
            "对照subplot_board，标记沉寂支线：哪些支线在近期章节中没有任何进展或提及，需要安排回收/推进/收束动作。"),
    ARC_FLAT(25, "弧线平坦", "Arc Flat", AuditTier.L2_QUALITY_WARN, 0.8,
            "情感弧线停滞检测：情绪压力形态不变化，人物情感曲线过于平坦、缺乏起伏变化。检查角色的情感状态是否有进展或转折。"),
    RHYTHM_MONOTONE(26, "节奏单调", "Rhythm Monotone", AuditTier.L2_QUALITY_WARN, 0.7,
            "近期章节类型分布检查：回收/释放/高潮事件缺席过长 → 标记节奏单调。检查章节功能类型（铺垫/推进/爆发/收束）是否过于单一。"),

    // ==================== L3 合规 (Compliance) — 2维 ====================

    KNOWLEDGE_POLLUTION(18, "知识库污染", "Knowledge Pollution", AuditTier.L3_COMPLIANCE, 1.0,
            "禁止非常识性的AI幻觉注入：检查是否存在模型编造的非真实知识、伪专业术语、不存在的历史事件或虚构的学术引用。仅允许设定内虚构。"),
    SENSITIVE_WORD_CHECK(27, "敏感词检查", "Sensitive Word Check", AuditTier.L3_COMPLIANCE, 1.2,
            "敏感词/违规内容检测：检查是否存在政治敏感词、色情违规内容、暴力违规内容或其他不合规表述。此维度阻断优先级最高。"),

    // ==================== 同人/番外维度 (Fanfic/Spin-off) — 10维 ====================

    CANON_EVENT_CONFLICT(28, "正传事件冲突", "Canon Event Conflict", AuditTier.L1_CRASHED_GUARD, 0.9,
            "同人情节与正传已发生事件是否冲突：检查同人创作是否与正传已确立的事件时间线和结果矛盾，确保不破坏正传的事件骨架。"),
    FUTURE_INFO_LEAK(29, "未来信息泄露", "Future Info Leak", AuditTier.L2_QUALITY_WARN, 0.8,
            "是否泄露了角色在当前时间点不应知晓的正传未来信息：检查角色言行中是否包含超出当前时间线的认知——这是同人创作最常见的逻辑漏洞。"),
    CROSS_BOOK_RULE_CONSISTENCY(30, "世界规则跨书一致性", "Cross-Book Rule Consistency", AuditTier.L2_QUALITY_WARN, 0.7,
            "跨作品的世界规则是否保持一致：同一世界体系下的多部作品间，基础规则设定（魔法体系/科技水平/种族设定等）是否相互矛盾。"),
    SPINOFF_FORESHADOW_ISOLATION(31, "番外伏笔隔离", "Spin-off Foreshadow Isolation", AuditTier.L2_QUALITY_WARN, 0.6,
            "番外伏笔是否与正传伏笔正确隔离：番外埋设的伏笔不应与正传未回收伏笔产生冲突或混淆，应明确标记伏笔的作用域。"),
    READER_EXPECTATION_MGMT(32, "读者期待管理", "Reader Expectation Management", AuditTier.L2_QUALITY_WARN, 0.6,
            "番外是否恰当管理读者对正传的期待：番外内容不应给正传主线造成误导性的读者期待，应明确番外的'非正典'或'if线'属性。"),
    CHAPTER_MEMO_DEVIATION(33, "章节备忘偏离", "Chapter Memo Deviation", AuditTier.L2_QUALITY_WARN, 0.8,
            "是否偏离了章节备忘中的规划：检查实际撰写内容与章节规划备忘（chapter_memo）之间的偏离程度，严重偏离需说明原因或更新备忘。"),
    CHARACTER_FIDELITY(34, "角色还原度", "Character Fidelity", AuditTier.L1_CRASHED_GUARD, 0.9,
            "同人角色是否符合原作性格设定：角色性格、行为模式、语言习惯是否与原作保持一致性，避免'同名不同人'的OOC问题。"),
    WORLD_RULE_COMPLIANCE(35, "世界规则遵守", "World Rule Compliance", AuditTier.L1_CRASHED_GUARD, 0.8,
            "是否遵守原作的世界规则：同人创作是否在已建立的世界规则框架内展开，不引入与原作世界规则冲突的新设定或破坏性规则。"),
    RELATIONSHIP_DYNAMIC(36, "关系动态", "Relationship Dynamic", AuditTier.L2_QUALITY_WARN, 0.7,
            "角色间关系发展是否符合原作基础：角色间的互动模式、情感关系发展是否与原作设定的关系基础一致，关系变化的速率和方向是否合理。"),
    CANON_EVENT_CONSISTENCY(37, "正典事件一致性", "Canon Event Consistency", AuditTier.L2_QUALITY_WARN, 0.8,
            "正典事件引用是否准确一致：检查对原有正典事件的引用、描述和利用是否准确，无信息畸变或记忆偏差。");

    // ==================== 字段 ====================

    private final int id;
    private final String chineseName;
    private final String englishName;
    private final AuditTier tier;
    private final double defaultWeight;
    private final String description;

    AuditDimension(int id, String chineseName, String englishName, AuditTier tier,
                   double defaultWeight, String description) {
        this.id = id;
        this.chineseName = chineseName;
        this.englishName = englishName;
        this.tier = tier;
        this.defaultWeight = defaultWeight;
        this.description = description;
    }

    // ==================== 访问器 ====================

    public int id() { return id; }
    public String chineseName() { return chineseName; }
    public String englishName() { return englishName; }
    public AuditTier tier() { return tier; }
    public double defaultWeight() { return defaultWeight; }
    public String description() { return description; }

    /**
     * 是否默认启用（v2规则：L1+L2+L3的前27维默认启用，同人/番外维度需显式开启）
     */
    public boolean enabledByDefault() {
        return id <= 27;
    }

    /**
     * 是否属于同人/番外维度组（id 28-37）
     */
    public boolean isFanficDimension() {
        return id >= 28;
    }

    /**
     * 是否属于L1硬阻塞层
     */
    public boolean isCrashedGuard() {
        return tier == AuditTier.L1_CRASHED_GUARD;
    }

    /**
     * 是否属于L2质量警告层
     */
    public boolean isQualityWarn() {
        return tier == AuditTier.L2_QUALITY_WARN;
    }

    /**
     * 是否属于L3合规章
     */
    public boolean isCompliance() {
        return tier == AuditTier.L3_COMPLIANCE;
    }

    // ==================== 静态工具方法 ====================

    /**
     * 根据id查找维度
     */
    public static Optional<AuditDimension> byId(int id) {
        return Arrays.stream(values())
                .filter(d -> d.id == id)
                .findFirst();
    }

    /**
     * 获取指定模式(draft/standard/submission)下应启用的维度列表
     *
     * @param mode 审计模式
     * @param includeFanfic 是否强制包含同人/番外维度
     * @return 启用的维度（按id排序）
     */
    public static List<AuditDimension> enabledForMode(AuditMode mode, boolean includeFanfic) {
        return Arrays.stream(values())
                .filter(d -> mode.includes(d, includeFanfic))
                .sorted(Comparator.comparingInt(AuditDimension::id))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定层级的全部维度
     */
    public static List<AuditDimension> byTier(AuditTier tier) {
        return Arrays.stream(values())
                .filter(d -> d.tier == tier)
                .sorted(Comparator.comparingInt(AuditDimension::id))
                .collect(Collectors.toList());
    }

    /**
     * 获取全部L1硬阻塞维度
     */
    public static List<AuditDimension> crashedGuards() {
        return byTier(AuditTier.L1_CRASHED_GUARD);
    }

    /**
     * 获取全部L2质量警告维度
     */
    public static List<AuditDimension> qualityWarns() {
        return byTier(AuditTier.L2_QUALITY_WARN);
    }

    /**
     * 获取全部L3合规维度
     */
    public static List<AuditDimension> compliances() {
        return byTier(AuditTier.L3_COMPLIANCE);
    }

    // ==================== 审计层级枚举 ====================

    /**
     * 审计层级 — 决定FAIL时的处理策略
     */
    public enum AuditTier {
        /** L1 硬阻塞 — FAIL则拒绝入库，结构性缺陷不可放行 */
        L1_CRASHED_GUARD("硬阻塞", "FAIL → 拒绝入库"),
        /** L2 质量警告 — FAIL则降级为WARN，fail-open机制，不阻断 */
        L2_QUALITY_WARN("质量警告", "FAIL → 降级WARN(fail-open)"),
        /** L3 合规 — FAIL则BLOCK，合规自检必须通过 */
        L3_COMPLIANCE("合规", "FAIL → BLOCK(compliance_selfcheck)");

        private final String displayName;
        private final String failPolicy;

        AuditTier(String displayName, String failPolicy) {
            this.displayName = displayName;
            this.failPolicy = failPolicy;
        }

        public String displayName() { return displayName; }
        public String failPolicy() { return failPolicy; }
    }

    // ==================== 审计模式枚举 ====================

    /**
     * 审计模式 — 控制启用哪些维度子集
     */
    public enum AuditMode {
        /** 草稿模式 — 仅L1硬阻塞+L3合规，快速检查 */
        DRAFT("draft", "草稿模式"),
        /** 标准模式 — L1+L2+L3（不含同人/番外维度），完整质量审计 */
        STANDARD("standard", "标准模式"),
        /** 提交模式 — 全部37维度（含同人/番外），最严格审计 */
        SUBMISSION("submission", "提交模式");

        private final String code;
        private final String displayName;

        AuditMode(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String code() { return code; }
        public String displayName() { return displayName; }

        /**
         * 判断某维度在该模式下是否应启用
         */
        public boolean includes(AuditDimension dim, boolean includeFanfic) {
            return switch (this) {
                case DRAFT -> dim.tier == AuditTier.L1_CRASHED_GUARD
                        || dim.tier == AuditTier.L3_COMPLIANCE;
                case STANDARD -> !dim.isFanficDimension() || includeFanfic;
                case SUBMISSION -> true;
            };
        }

        /**
         * 根据code字符串解析模式，默认返回STANDARD
         */
        public static AuditMode fromCode(String code) {
            if (code == null) return STANDARD;
            return switch (code.toLowerCase()) {
                case "draft" -> DRAFT;
                case "submission" -> SUBMISSION;
                default -> STANDARD;
            };
        }
    }
}
