package com.yunmo.common.enums;

/**
 * 33 维度审计枚举 — 参考 InkOS 设计 + 云墨自有10维扩展
 * 分为4组：情节(8维)、角色(7维)、文笔(12维)、合规(6维)
 */
public enum AuditDimension {

    // ========== 情节组 (Plot) — 8 维 ==========
    CAUSAL_CHAIN("因果链自洽", AuditCategory.PLOT, 1.0,
            "事件之间的因果逻辑是否成立，前因后果是否自洽"),
    TIMELINE_CONSISTENCY("时间线一致", AuditCategory.PLOT, 0.8,
            "时间推进是否合理，是否存在时间矛盾"),
    FORESHADOW_DENSITY("伏笔密度", AuditCategory.PLOT, 0.9,
            "伏笔的埋设密度是否适中，不过密不稀疏"),
    CONFLICT_LAYER("冲突层级", AuditCategory.PLOT, 0.9,
            "冲突的层次是否丰富（内在/人际/环境），是否有渐进升级"),
    MAIN_PLOT_FOCUS("主线聚焦度", AuditCategory.PLOT, 1.0,
            "主线情节是否明确推进，是否有无关支线干扰"),
    SUBPLOT_PROGRESS("支线推进", AuditCategory.PLOT, 0.7,
            "支线情节是否有推进或回收，是否停滞"),
    SUSPENSE_QUALITY("悬念设置质量", AuditCategory.PLOT, 0.8,
            "章节末尾悬念是否有效，是否引发读者继续阅读的欲望"),
    CHAPTER_CLOSURE("章节收束质量", AuditCategory.PLOT, 0.7,
            "章节是否有完整的起承转合，结尾是否有阶段性收束"),

    // ========== 角色组 (Character) — 7 维 ==========
    COGNITIVE_MODEL_CONSISTENCY("6层认知模型一致性", AuditCategory.CHARACTER, 1.2,
            "角色言行是否符合其世界观/自我认同/价值观/能力/技能/环境六层模型"),
    CHARACTER_ARC("角色弧线推进", AuditCategory.CHARACTER, 1.0,
            "角色是否有成长或变化，弧线是否在推进"),
    CHARACTER_GROWTH("人物成长追踪", AuditCategory.CHARACTER, 0.8,
            "角色的能力/认知/关系是否有实质性变化"),
    DIALOGUE_PERSONALITY("对白个性化", AuditCategory.CHARACTER, 0.9,
            "不同角色的对话是否有个性差异，是否与角色设定一致"),
    ACTION_QUALITY("动作描写质量", AuditCategory.CHARACTER, 0.7,
            "动作场景的描写是否清晰、有画面感"),
    PSYCHOLOGICAL_DEPTH("心理描写深度", AuditCategory.CHARACTER, 0.8,
            "角色的内心活动是否丰富、有层次"),
    COOLDOWN_COMPLIANCE("出场冷却合规", AuditCategory.CHARACTER, 0.6,
            "角色出场频率是否符合冷却设定，是否有角色疲劳"),

    // ========== 文笔组 (Prose) — 12 维 ==========
    AI_DETECTION("AI味检测", AuditCategory.PROSE, 1.2,
            "文本是否读起来像AI生成，模板化程度如何"),
    TEMPLATE_PATTERN("模板化句式", AuditCategory.PROSE, 1.0,
            "是否存在高频套话（如'不仅...而且...'、'在...的过程中'等）"),
    MODERN_CONTAMINATION("现代语汇污染", AuditCategory.PROSE, 0.8,
            "是否存在不符合世界观设定的现代用语"),
    INDENTATION("段落缩进规范", AuditCategory.PROSE, 0.5,
            "首行缩进、段落间距是否统一规范"),
    PUNCTUATION("标点规范", AuditCategory.PROSE, 0.5,
            "标点符号使用是否符合中文规范"),
    PARAGRAPH_VARIATION("段落长度变异度", AuditCategory.PROSE, 0.6,
            "段落长度是否有变化，避免单调"),
    IDIOM_USAGE("成语使用规范", AuditCategory.PROSE, 0.5,
            "成语使用是否准确、恰当"),
    LONG_SENTENCE_DENSITY("长句密度", AuditCategory.PROSE, 0.6,
            "长句比例是否适中，是否存在阅读困难的长句"),
    POV_STABILITY("叙述视角稳定", AuditCategory.PROSE, 0.9,
            "人称和视角是否一致，有无视角跳跃"),
    DIALOGUE_DESCRIPTION_RATIO("描写-对话比例", AuditCategory.PROSE, 0.6,
            "描写与对话的比例是否合理"),
    RHETORIC_RICHNESS("修辞手法丰富度", AuditCategory.PROSE, 0.6,
            "比喻、拟人等修辞手法的使用是否丰富恰当"),
    SENSORY_COVERAGE("感官描写覆盖", AuditCategory.PROSE, 0.6,
            "视觉/听觉/嗅觉/触觉/味觉五感的描写是否全面"),

    // ========== 合规组 (Compliance) — 6 维 ==========
    GENRE_COMPLIANCE("类型合规性", AuditCategory.COMPLIANCE, 1.2,
            "是否违反小说类型的铁律和基本规则"),
    FORBIDDEN_TERMS("禁止术语扫描", AuditCategory.COMPLIANCE, 1.0,
            "是否出现禁止使用的敏感词汇"),
    WORLD_BUILDING_CONSISTENCY("世界观一致性", AuditCategory.COMPLIANCE, 1.0,
            "是否违反已建立的世界观设定"),
    PERSON_CONSISTENCY("人称一致性", AuditCategory.COMPLIANCE, 0.7,
            "叙述人称是否前后统一"),
    WORD_COUNT_TARGET("字数达标", AuditCategory.COMPLIANCE, 0.6,
            "章节字数是否在目标范围（±20%）内"),
    EMOTIONAL_TENSION("情感张力曲线", AuditCategory.COMPLIANCE, 0.8,
            "章节的情绪起伏是否合理，高潮和低谷的分布是否恰当");

    private final String displayName;
    private final AuditCategory category;
    private final double weight;
    private final String description;

    AuditDimension(String displayName, AuditCategory category, double weight, String description) {
        this.displayName = displayName;
        this.category = category;
        this.weight = weight;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public AuditCategory getCategory() { return category; }
    public double getWeight() { return weight; }
    public String getDescription() { return description; }

    public enum AuditCategory {
        PLOT("情节"),
        CHARACTER("角色"),
        PROSE("文笔"),
        COMPLIANCE("合规");

        private final String displayName;
        AuditCategory(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
}
