package com.yunmo.agent.hook;

import java.util.List;

/**
 * 钩子类型枚举 — 参考 chinese-novelist 钩子系统设计
 *
 * 分三个层次：
 * 1. 章首引子（7式）— 50-150字正文前钩子，吸引读者翻页
 * 2. 章尾钩子（13式）— 结尾悬念，迫使读者追更
 * 3. 跨章悬念弧 — 短弧(2-3章) / 中弧(5-8章) / 长弧(全书)
 */
public enum HookType {

    // ================================================================
    // 章首引子 7 式
    // ================================================================

    /** 悬念对话引子 — 用一句惊人的对话开场，读者必须知道上下文 */
    HOOK_OPEN_DIALOG("悬念对话引子", Category.OPENING, 1,
            "用一句惊人的对话开场，让读者立刻想知道'发生了什么'。",
            "在正文开始前，写一段50-150字的悬念对话引子：用一句令人震惊的对话开场。"
                    + "这句话暗示了某种冲突或秘密，但不揭示全貌。"
                    + "对话可以来自本章任意角色，但必须与本章核心事件紧密相关。"),

    /** 闪前碎片 — 从本章高潮截取一个碎片提前展示 */
    HOOK_OPEN_FLASH_FORWARD("闪前碎片", Category.OPENING, 2,
            "从本章高潮/转折点截取一个碎片提前展示，制造'他是怎么走到这一步的'的好奇心。",
            "在正文开始前，写一段50-150字的闪前碎片：截取本章高潮时刻的一幕场景，以碎片化的方式呈现。"
                    + "展示一个紧张、惊人或不寻常的画面，但不能揭示来龙去脉。"
                    + "读者必须读完本章才能理解这个碎片的含义。"),

    /** 倒计时引子 — 用时间压力制造紧迫感 */
    HOOK_OPEN_COUNTDOWN("倒计时引子", Category.OPENING, 3,
            "用明确的时间限制制造紧迫感，'还有X小时/分钟'。",
            "在正文开始前，写一段50-150字的倒计时引子：设定一个明确的时间限制。"
                    + "例如'离最后的期限还有三炷香的时间'或'太阳落山之前，必须找到解药'。"
                    + "用具体的时间单位（时辰、刻、呼吸之间）制造紧迫感，让读者从第一行就感受到压力。"),

    /** 神秘独白 — 角色内心独白揭示隐藏信息 */
    HOOK_OPEN_MONOLOGUE("神秘独白", Category.OPENING, 4,
            "角色内心独白揭示隐藏信息，暗示某种不为人知的真相。",
            "在正文开始前，写一段50-150字的神秘独白：一个角色（可以是主角或重要配角）的内心独白。"
                    + "独白暗示了某个不为人知的秘密、计划或恐惧。语气克制、含蓄，留有余地。"
                    + "读者能感受到角色内心的波澜，但不清楚具体发生了什么。"),

    /** 反差场景 — 两个强烈对比的画面并列 */
    HOOK_OPEN_CONTRAST("反差场景", Category.OPENING, 5,
            "两个强烈对比的画面并列，制造认知冲突。",
            "在正文开始前，写一段50-150字的反差引子：并列两个形成强烈对比的画面或场景。"
                    + "例如：前一秒还欢声笑语的庭院，后一秒血流成河。或：表面平静的湖面，与水下暗涌的漩涡。"
                    + "两个画面的反差越大，悬念越强。不需要解释原因，只呈现对比本身。"),

    /** 未完成动作引子 — 中断的动作制造'接下来会怎样' */
    HOOK_OPEN_CLIFFHANGER("未完成动作引子", Category.OPENING, 6,
            "在一个关键动作即将完成的瞬间切断，读者必须读下去。",
            "在正文开始前，写一段50-150字的未完成动作引子：描绘一个正在进行的关键动作——"
                    + "比如拔剑、推门、落笔、转身——但在动作完成前的瞬间切断。"
                    + "只描写动作的姿态和氛围，不揭示结果。读者在正文中才能找到动作的结局。"),

    /** 意象伏笔引子 — 反复出现的意象发生变化 */
    HOOK_OPEN_SYMBOL("意象伏笔引子", Category.OPENING, 7,
            "反复出现的某个意象（物件、颜色、自然现象）突然发生变化，暗示剧变将至。",
            "在正文开始前，写一段50-150字的意象伏笔引子：聚焦于一个反复出现的意象——"
                    + "可以是物件（镜子、玉佩、信、剑）、颜色（红色、黑色）、或自然现象（雨、雾、风）。"
                    + "这个意象在之前的章节中已经出现过，但在本章开始前发生了微妙的变化。"
                    + "用细腻的笔触描写变化本身，暗示某种命运的转折。"),

    // ================================================================
    // 章尾钩子 13 式
    // ================================================================

    /** 突然揭示 — 结尾揭示一个改变一切的信息 */
    HOOK_CLOSE_REVEAL("突然揭示", Category.CLOSING, 1,
            "结尾突然揭示一个改变一切的信息，推翻读者的既有认知。",
            "在章节结尾，使用突然揭示的钩子手法：揭示一个之前被隐藏的信息，"
                    + "这个信息改变了角色（和读者）对某个事件或人物的理解。"
                    + "揭示越意外、后果越深远，钩子效果越强。控制在1-3段内完成，戛然而止。"),

    /** 紧急危机 — 角色面临迫在眉睫的危险 */
    HOOK_CLOSE_CRISIS("紧急危机", Category.CLOSING, 2,
            "角色突然面临迫在眉睫的危险，生命或关键目标受到威胁。",
            "在章节结尾，使用紧急危机的钩子手法：让角色突然面临迫在眉睫的危险。"
                    + "危险必须是具体的、直接的——偷袭、陷阱、背叛、灾难、走火入魔等。"
                    + "在危险降临的瞬间结束章节，不给解决方案，让读者处于高度紧张中。"),

    /** 未完成的动作 — 关键动作被打断 */
    HOOK_CLOSE_UNFINISHED("未完成的动作", Category.CLOSING, 3,
            "关键动作被打断，结果的悬念悬而未决。",
            "在章节结尾，使用未完成动作的钩子手法：主角正在完成一个关键动作——"
                    + "比如即将击杀敌人、即将说出真相、即将拿到宝物——"
                    + "但在结果出现的前一刻被打断或中断。章节结束，读者只能等下一章。"),

    /** 身份反转 — 某人被揭示为不是我们以为的那样 */
    HOOK_CLOSE_IDENTITY("身份反转", Category.CLOSING, 4,
            "揭示某人的真实身份与表面身份不同，颠覆前文的认知。",
            "在章节结尾，使用身份反转的钩子手法：揭示某个角色的真实身份与表面身份不同。"
                    + "可以是盟友变敌人、路人变关键人物、逝者未死等。"
                    + "反转需要在前文埋下足够伏笔，结尾时让读者感到'原来如此'的恍然大悟。"),

    /** 两难选择 — 必须在两个都不好的选项中抉择 */
    HOOK_CLOSE_DILEMMA("两难选择", Category.CLOSING, 5,
            "角色被逼到墙角，必须在两个都不好的选项中做出选择。",
            "在章节结尾，使用两难选择的钩子手法：将角色置于必须抉择的境地。"
                    + "两个选项都有巨大的代价——救人还是完成任务？忠诚还是正义？自己的命还是同伴的命？"
                    + "章节在角色面临抉择的瞬间结束，不揭示选择结果。"),

    /** 神秘物品/线索 — 发现意义不明但暗示威胁的东西 */
    HOOK_CLOSE_CLUE("神秘物品/线索", Category.CLOSING, 6,
            "发现意义不明但暗示深层威胁的物品或线索。",
            "在章节结尾，使用神秘物品/线索的钩子手法：让角色发现一件意义不明的物品或线索。"
                    + "这件物品不应在此处出现——来历不明、功能未知、但暗示着某种深层威胁。"
                    + "描写物品的细节（质地、铭文、颜色、温度等），暗示而不明说其重要性。"),

    /** 时间限制 — 明确时限+资源不足 */
    HOOK_CLOSE_DEADLINE("时间限制", Category.CLOSING, 7,
            "明确的时间限制+资源不足，制造倒计时焦虑。",
            "在章节结尾，使用时间限制的钩子手法：揭示一个明确的截止时间，"
                    + "同时强调目前掌握的资源（信息、人力、物资）远不足以在时限内完成任务。"
                    + "例如'三天之内要找到解药，但唯一的线索已经断了'。用具体数字增强紧迫感。"),

    /** 承诺/威胁 — 某人做出承诺或威胁，改变预期 */
    HOOK_CLOSE_PROMISE("承诺/威胁", Category.CLOSING, 8,
            "某人做出一个承诺或威胁，改变读者对后续剧情的预期。",
            "在章节结尾，使用承诺/威胁的钩子手法：让某个角色（尤其是反派或关键配角）"
                    + "做出一个有力的承诺或威胁。这个承诺/威胁必须具体、可信、有分量，"
                    + "让读者确信它将在后续章节中兑现。例如'三日之后，我会让你跪下来求我'。"),

    /** 离奇消失 — 某人/物在不可能的条件下消失 */
    HOOK_CLOSE_VANISH("离奇消失", Category.CLOSING, 9,
            "某人或某物在不可能的条件下离奇消失，留下未解之谜。",
            "在章节结尾，使用离奇消失的钩子手法：一个关键人物、重要物品在不可能的条件下消失了。"
                    + "强调消失的不可能性——密室消失、众目睽睽之下、前一秒还在——以增强悬疑感。"
                    + "不揭示消失的原因或去向，留给后续章节展开。"),

    /** 言外之意 — 表面正常但暗示更深层信息的一句话 */
    HOOK_CLOSE_SUBTEXT("言外之意", Category.CLOSING, 10,
            "表面正常的一句话，但暗示了更深层的信息或威胁。",
            "在章节结尾，使用言外之意的钩子手法：写一句表面正常、"
                    + "但细思极恐的话作为章节的最后一句话。这句话的潜台词比字面含义重大得多。"
                    + "例如下属报告'一切都安排好了'，但读者知道那个'安排'可能是陷阱。"),

    /** 意象钩子 — 全章反复铺垫的意象在结尾发生微妙变化 */
    HOOK_CLOSE_IMAGE("意象钩子", Category.CLOSING, 11,
            "全章反复铺垫的某个意象在结尾发生微妙但意味深长的变化。",
            "在章节结尾，使用意象钩子手法：聚焦于本章反复出现的一个意象（物件、天气、颜色、声音等），"
                    + "在结尾处让这个意象发生微妙的变化。变化越小、越安静，反而越有力量。"
                    + "例如一直燃烧的蜡烛突然熄灭了，或一直阴沉的天空裂开了一道光。"),

    /** 回声钩子 — 结尾句呼应或反转开篇第一句话 */
    HOOK_CLOSE_ECHO("回声钩子", Category.CLOSING, 12,
            "结尾句呼应或反转开篇第一句话，形成闭环或颠覆。",
            "在章节结尾，使用回声钩子手法：让本章的最后一句话呼应或反转开篇的第一句话。"
                    + "可以是直接重复但赋予新含义，也可以是反向的对称。"
                    + "这种首尾呼应为章节提供了结构性的满足感，同时暗示了循环或转变。"),

    /** 留白钩子 — 不写悬念本身，只写角色对悬念的反应 */
    HOOK_CLOSE_BLANK("留白钩子", Category.CLOSING, 13,
            "不写悬念本身，只写角色对悬念的反应——让读者自己脑补最可怕的部分。",
            "在章节结尾，使用留白钩子手法：不直接描述发生了什么，"
                    + "只描写角色看到/听到/感受到某件事之后的反应——"
                    + "表情变化、身体颤抖、一句话说不出来、手中的东西掉落。"
                    + "读者通过角色的反应来揣测发生了什么事，留白越充分，读者越焦虑。");

    // ================================================================
    // 枚举字段
    // ================================================================

    /** 钩子中文名称 */
    private final String chineseName;
    /** 分类（章首/章尾） */
    private final Category category;
    /** 式序号 */
    private final int formulaNumber;
    /** 技法说明（中文，供开发者参考） */
    private final String techniqueDescription;
    /** 生成 Prompt 模板（注入 Writer LLM） */
    private final String promptTemplate;

    HookType(String chineseName, Category category, int formulaNumber,
             String techniqueDescription, String promptTemplate) {
        this.chineseName = chineseName;
        this.category = category;
        this.formulaNumber = formulaNumber;
        this.techniqueDescription = techniqueDescription;
        this.promptTemplate = promptTemplate;
    }

    // ================================================================
    // 悬念强度
    // ================================================================

    /**
     * 悬念强度 5 级
     * 好奇(1) — 想知道答案
     * 关切(2) — 担心角色
     * 迫切(3) — 急于知道后续
     * 生存(4) — 角色生死攸关
     * 终极(5) — 世界命运/终极真相
     */
    public enum Intensity {
        CURIOSITY(1, "好奇"),
        CONCERN(2, "关切"),
        URGENCY(3, "迫切"),
        SURVIVAL(4, "生存"),
        ULTIMATE(5, "终极");

        private final int level;
        private final String chineseName;

        Intensity(int level, String chineseName) {
            this.level = level;
            this.chineseName = chineseName;
        }

        public int level() { return level; }
        public String chineseName() { return chineseName; }
    }

    /**
     * 钩子分类
     */
    public enum Category {
        OPENING("章首引子"),
        CLOSING("章尾钩子");

        private final String chineseName;

        Category(String chineseName) {
            this.chineseName = chineseName;
        }

        public String chineseName() { return chineseName; }
    }

    // ================================================================
    // 便捷方法
    // ================================================================

    public String chineseName() { return chineseName; }
    public Category category() { return category; }
    public int formulaNumber() { return formulaNumber; }
    public String techniqueDescription() { return techniqueDescription; }
    public String promptTemplate() { return promptTemplate; }

    /** 是否为章首引子 */
    public boolean isOpening() { return category == Category.OPENING; }

    /** 是否为章尾钩子 */
    public boolean isClosing() { return category == Category.CLOSING; }

    // ================================================================
    // 预缓存列表（避免每次 values() 创建新数组）
    // ================================================================

    private static final List<HookType> OPENING_HOOKS = List.of(
            HOOK_OPEN_DIALOG, HOOK_OPEN_FLASH_FORWARD, HOOK_OPEN_COUNTDOWN,
            HOOK_OPEN_MONOLOGUE, HOOK_OPEN_CONTRAST, HOOK_OPEN_CLIFFHANGER,
            HOOK_OPEN_SYMBOL
    );

    private static final List<HookType> CLOSING_HOOKS = List.of(
            HOOK_CLOSE_REVEAL, HOOK_CLOSE_CRISIS, HOOK_CLOSE_UNFINISHED,
            HOOK_CLOSE_IDENTITY, HOOK_CLOSE_DILEMMA, HOOK_CLOSE_CLUE,
            HOOK_CLOSE_DEADLINE, HOOK_CLOSE_PROMISE, HOOK_CLOSE_VANISH,
            HOOK_CLOSE_SUBTEXT, HOOK_CLOSE_IMAGE, HOOK_CLOSE_ECHO,
            HOOK_CLOSE_BLANK
    );

    public static List<HookType> openingHooks() { return OPENING_HOOKS; }
    public static List<HookType> closingHooks() { return CLOSING_HOOKS; }
}
