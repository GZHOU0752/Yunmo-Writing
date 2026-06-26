package com.yunmo.common.slop;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 去AI味量化体系 — 全局配置中心。
 * <p>
 * 定义了所有禁用的AI句式、禁用词分级、章末污染句式、AI写作模式识别规则，
 * 以及白名单配置机制。供 {@link com.yunmo.common.util.AntiAIPatterns} 7-Gate引擎引用。
 * </p>
 *
 * <h3>配置层级</h3>
 * <ul>
 *   <li>最毒禁用句式 — 出现即判重度AI味，零容忍</li>
 *   <li>一级禁用词 — 出现即需替换，按情态/动作/表情/心理/判断/形容分类</li>
 *   <li>二级禁用词 — 高频时才处理</li>
 *   <li>章末污染句式 — 章末特有的AI套路</li>
 *   <li>AI写作模式 — 8种可检测的AI写作行为模式</li>
 * </ul>
 *
 * @author yunmo
 * @since 1.0
 */
public final class AntiSlopConfig {

    private AntiSlopConfig() {
        // 工具类，禁止实例化
    }

    // ========================================================================
    // 一、最毒禁用句式 — 出现即判重度AI味（Gate A 严重度加权）
    // ========================================================================

    /**
     * 最毒禁用句式正则列表。
     * 命中任意一条即触发重度警告，confidence ≥ 0.90。
     */
    public static final List<Pattern> FATAL_SENTENCE_PATTERNS = List.of(
            // "不是A,(而)是B" / "不是A，不是B，(而)是C"
            Pattern.compile("不是.{1,30}[，,]?(而)?是"),
            Pattern.compile("并非.{1,30}[，,]?(而)?是"),
            // "，带着……" 万能状语
            Pattern.compile("[，,][^。]{0,15}带着.{1,20}(?:的|地)"),
            // "声音不大，却带着……"
            Pattern.compile("声音不大[，,](?:却|但).{1,20}带着"),
            // "眼中闪过一丝……" / "嘴角勾起一抹……" / "心中涌起一股……"
            Pattern.compile("眼中闪过.{1,20}(?:丝|抹|股|阵)"),
            Pattern.compile("嘴角勾.{1,20}(?:丝|抹|股)"),
            Pattern.compile("心中涌起.{1,20}(?:丝|抹|股|阵)"),
            // "仿佛/犹如/宛若……一般"
            Pattern.compile("(?:仿佛|犹如|宛若|如同).{1,30}(?:一般|似的|一样)")

    );

    // ========================================================================
    // 二、一级禁用词 — 出现即需替换
    // ========================================================================

    /** 情态类 — 模糊化副词，消解具体感受 */
    public static final Set<String> BANNED_MODAL = Set.of(
            "仿佛", "犹如", "宛若", "如同",
            "一丝", "一抹", "些许", "几分", "隐约"
    );

    /** 动作类 — AI高频填充动作 */
    public static final Set<String> BANNED_ACTION = Set.of(
            "深吸一口气", "缓缓", "不禁", "微微", "轻轻", "淡淡"
    );

    /** 表情类 — 模板化表情描写 */
    public static final Set<String> BANNED_EXPRESSION = Set.of(
            "眼中闪过", "嘴角勾起", "眉头微皱", "眉眼低垂", "瞳孔微缩"
    );

    /** 心理类 — 直接陈述内心活动（应改为外化描写） */
    public static final Set<String> BANNED_PSYCH = Set.of(
            "心中一动", "心头一震", "心下了然", "心中暗道",
            "心底泛起", "不由得"
    );

    /** 判断类 — 叙述者越位下结论 */
    public static final Set<String> BANNED_JUDGMENT = Set.of(
            "不容置疑", "不易察觉", "显而易见", "毫无疑问"
    );

    /** 形容类 — 廉价形容词，缺乏具象支撑 */
    public static final Set<String> BANNED_ADJECTIVE = Set.of(
            "坚定", "闪烁着光芒", "狡黠", "深邃", "凛冽", "冰冷"
    );

    /**
     * 一级禁用词全集 — 合并所有分类，供单次遍历使用。
     */
    public static final Set<String> ALL_LEVEL1_BANNED = buildLevel1All();

    /**
     * 一级禁用词分类映射 — 词 → 分类名，供诊断报告溯源。
     */
    public static final Map<String, String> LEVEL1_CATEGORY_MAP = buildLevel1CategoryMap();

    // ========================================================================
    // 三、二级禁用词 — 高频时才触发警告
    // ========================================================================

    /**
     * 二级禁用词。
     * 单独出现不扣分，但每千字出现 ≥5 次时升级为中度警告。
     */
    public static final Set<String> LEVEL2_BANNED = Set.of(
            "突然", "好像", "瞬间"
    );

    // ========================================================================
    // 四、章末污染句式
    // ========================================================================

    /** 章末总结体 — 最后200字检测 */
    public static final List<Pattern> ENDING_SUMMARY_PATTERNS = List.of(
            // "这一X告诉/证明/说明/意味着..."
            Pattern.compile("这(?:一|个|场|次|段|番).{0,20}(?:告诉|证明|说明|意味着|表明)"),
            // "从此..." / "自此..."
            Pattern.compile("[从自]此[，,].{5,50}[。]")
    );

    /** 升华式感叹 — 章末强行拔高 */
    public static final List<Pattern> ENDING_ELEVATION_PATTERNS = List.of(
            Pattern.compile("(?:也许|或许|大概|说不定)[，,]?(?:这|那)就是.{1,30}[。]"),
            Pattern.compile("(?:人生|命运|世间|天下|众生).{0,20}(?:也许|或许|大概|终究|不过)"),
            Pattern.compile("(?:终究|到头来|说到底|归根结底)[，,]?.{1,40}[。]")
    );

    /** 伏笔式预告 — AI最爱的"他/她不知道的是..." */
    public static final List<Pattern> ENDING_FORESHADOW_PATTERNS = List.of(
            Pattern.compile("(?:他|她|它).{0,10}(?:不知道|没想到|没料到|不曾想)的是"),
            Pattern.compile("(?:然而|可是|但)[，,]?(?:他|她).{0,15}(?:还不知道|还没意识到)")
    );

    // ========================================================================
    // 五、8种AI写作模式
    // ========================================================================

    /**
     * AI写作模式枚举 — 每种模式配检测规则和修复方向。
     */
    public enum AIPattern {
        /** 高频词重复 — 同一修饰词短时间内反复出现 */
        HIGH_FREQ_WORDS("高频词重复", "同一修饰词在500字内出现≥3次，暴露AI词汇贫乏"),
        /** 弱化副词 — 用副词替代具体描写 */
        WEAK_ADVERB("弱化副词", "用\"缓缓/轻轻/淡淡/微微\"替代具体动作细节"),
        /** 意义膨胀 — 把小动作强行赋予宏大意义 */
        MEANING_INFLATION("意义膨胀", "把日常动作赋予过度的象征意义或哲理内涵"),
        /** 万能结论 — 叙述者跳出来做总结 */
        UNIVERSAL_CONCLUSION("万能结论", "用\"这/这一切\"开头替读者下结论,剥夺读者思考权"),
        /** 论文体 — 书面语过度侵入叙事 */
        ACADEMIC_STYLE("论文体", "使用\"首先/其次/此外/综上所述\"等论文连接词"),
        /** 书面连词 — 口语场景用书面连词 */
        FORMAL_CONJUNCTION("书面连词", "对话中使用\"因此/于是/然而/此外\"等书面连词"),
        /** 三连排比 — AI最爱的排比句式 */
        TRIPLE_PARALLEL("三连排比", "连续三句以上相同结构的排比句式,显得刻意"),
        /** 解释腔 — 叙述者替读者解释人物动机 */
        EXPLANATION_TONE("解释腔", "叙述者直接解释人物行为原因,破坏沉浸感")
        ;

        public final String displayName;
        public final String description;

        AIPattern(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    // ========================================================================
    // 六、句式套路检测 — Gate B 扩展
    // ========================================================================

    /** 公式化转折词 — ≥3次触发警告 */
    public static final List<String> TRANSITION_WORDS = List.of(
            "然而", "不过", "与此同时", "另一方面", "尽管如此", "话虽如此"
    );

    /** 列表式结构检测 — 相同开头 */
    public static final int LIST_CONSECUTIVE_THRESHOLD = 3;

    /** 排比句式模式 */
    public static final List<Pattern> PARALLEL_PATTERNS = List.of(
            Pattern.compile("(?:有的|有人|有些).{1,30}[，,;；].{1,30}(?:有的|有人|有些)"),
            Pattern.compile("是.{1,20}[，,;；]是.{1,20}[，,;；]是"),
            Pattern.compile("(?:可以|能够|应该|需要).{1,20}[，,;；](?:可以|能够|应该|需要)")
    );

    // ========================================================================
    // 七、心理外化检测 — Gate C
    // ========================================================================

    /** 情绪陈述词 — 直接说人物"觉得/感到"，而非通过动作外化 */
    public static final Set<String> EMOTION_STATEMENT_WORDS = Set.of(
            "觉得", "感到", "认为", "想", "知道", "明白", "意识到", "察觉"
    );

    /** 抽象心理词 — "心中/内心/心底/心头"等内视角词 */
    public static final Set<String> ABSTRACT_PSYCH_WORDS = Set.of(
            "心中", "内心", "心底", "心头", "心里", "心间", "脑海", "脑中"
    );

    // ========================================================================
    // 八、对话检测 — Gate E
    // ========================================================================

    /** 对话标签前缀 — "xxx说"/"xxx道" 模式 */
    public static final Pattern DIALOG_TAG_PATTERN = Pattern.compile(
            "[，,]?(?:他|她|它|其)(?:.{0,10})?(?:说|道|问|答|喊|叫|吼|叹|笑)"
    );

    /** 禁止的对话前后缀 — 固定模式 */
    public static final List<Pattern> DIALOG_PREFIX_PATTERNS = List.of(
            Pattern.compile("(?:他|她).{0,5}(?:缓缓|轻轻|淡淡|微微).{0,10}(?:说|道)"),
            Pattern.compile("(?:他|她).{0,5}(?:深吸一口气|叹了口气)[，,]?(?:说|道)?"),
            Pattern.compile("(?:他|她).{0,10}(?:声音|语气|语调).{0,10}(?:说|道)?")
    );

    // ========================================================================
    // 九、结尾检测 — Gate F
    // ========================================================================

    /** 章末段落字数阈值（最后N字检测） */
    public static final int ENDING_CHECK_CHARS = 200;

    // ========================================================================
    // 十、解释腔检测 — Gate G
    // ========================================================================

    /** 叙述者下结论标志词 */
    public static final Set<String> NARRATOR_CONCLUSION_WORDS = Set.of(
            "显然", "当然", "毕竟", "毫无疑问", "可想而知", "不言而喻", "众所周知"
    );

    /** 元叙事标志词 — 元信息词进入正文 */
    public static final Set<String> META_NARRATIVE_WORDS = Set.of(
            "本章", "前文", "细纲", "读者", "伏笔", "铺垫",
            "剧情", "情节安排", "设定", "世界观", "人物弧光"
    );

    /** 上帝感模式 — 全知叙述者评论人物动机 */
    public static final List<Pattern> OMNISCIENT_PATTERNS = List.of(
            Pattern.compile("(?:他|她)(?:之所以|之所以会).{1,40}(?:是因为|源于|出于)"),
            Pattern.compile("(?:实际上|事实上|其实)[，,]?(?:他|她).{1,30}(?:真正|真正地)"),
            Pattern.compile("(?:他|她).{1,20}(?:自己也没意识到|自己都未察觉|连自己也.{0,10}(?:知道|明白))")
    );

    // ========================================================================
    // 十一、白名单配置
    // ========================================================================

    /**
     * 默认白名单（可被 .deslop-whitelist 文件覆盖）。
     * 白名单项在检测时跳过，不扣分。
     */
    public static final Set<String> DEFAULT_WHITELIST = new HashSet<>();

    static {
        // 常见角色绰号 — 避免把"坚定"（人名/绰号）误判为禁用词
        DEFAULT_WHITELIST.addAll(Set.of(
                // 可在此处添加项目级默认白名单
        ));
    }

    // ========================================================================
    // 十二、量化阈值配置
    // ========================================================================

    /** 一级禁用词：重度阈值（每千字次数） */
    public static final int LEVEL1_SEVERE_PER_K = 5;

    /** 一级禁用词：中度阈值（每千字次数） */
    public static final int LEVEL1_MODERATE_PER_K = 3;

    /** 心理外化词：重度阈值（每千字次数） */
    public static final int PSYCH_SEVERE_PER_K = 8;

    /** 对话标签占比：AI味阈值（>50%触发） */
    public static final double DIALOG_TAG_AI_RATIO = 0.50;

    /** 段落句数变异系数：AI特征阈值（CV < 0.15） */
    public static final double CV_AI_THRESHOLD = 0.15;

    /** 每千字标准 */
    public static final int PER_K_CHARS = 1000;

    /** AI评分：重度下限 */
    public static final int SCORE_SEVERE_MIN = 70;

    /** AI评分：中度下限 */
    public static final int SCORE_MODERATE_MIN = 40;

    // ========================================================================
    // 初始化辅助方法
    // ========================================================================

    private static Set<String> buildLevel1All() {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(BANNED_MODAL);
        all.addAll(BANNED_ACTION);
        all.addAll(BANNED_EXPRESSION);
        all.addAll(BANNED_PSYCH);
        all.addAll(BANNED_JUDGMENT);
        all.addAll(BANNED_ADJECTIVE);
        return Collections.unmodifiableSet(all);
    }

    private static Map<String, String> buildLevel1CategoryMap() {
        Map<String, String> map = new LinkedHashMap<>();
        BANNED_MODAL.forEach(w -> map.put(w, "情态类"));
        BANNED_ACTION.forEach(w -> map.put(w, "动作类"));
        BANNED_EXPRESSION.forEach(w -> map.put(w, "表情类"));
        BANNED_PSYCH.forEach(w -> map.put(w, "心理类"));
        BANNED_JUDGMENT.forEach(w -> map.put(w, "判断类"));
        BANNED_ADJECTIVE.forEach(w -> map.put(w, "形容类"));
        return Collections.unmodifiableMap(map);
    }

    /**
     * 根据词获取所属禁用类别。
     */
    public static String categoryOf(String word) {
        return LEVEL1_CATEGORY_MAP.getOrDefault(word, "未知");
    }
}
