package com.yunmo.service.style;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 风格路由服务 — 根据体裁、章纲、用户焦点自动选择风格模块
 *
 * 选择逻辑：
 *   1. 先判断章节主要靠什么推进（冲突/关系/悬疑/推理/氛围/规则/主题）
 *   2. 再判断读者此章最该感到什么（笑/紧张/好奇/心动/不安/新奇/共情）
 *   3. 最后判断辅助风格（补充或反衬）
 *
 * 返回 1-2 主风格 + 0-1 辅助风格
 */
@Service
public class StyleRouter {

    private static final Logger log = LoggerFactory.getLogger(StyleRouter.class);

    /**
     * 体裁 → 默认主风格的粗粒度映射
     * 体裁规则包已经提供了硬约束，这里只是初始锚定
     */
    private static final Map<String, StyleModule> GENRE_DEFAULT_STYLE = Map.of(
            "xianxia", StyleModule.FANTASY,
            "xuanhuan", StyleModule.FANTASY,
            "qihuan", StyleModule.FANTASY,
            "wuxia", StyleModule.LITERARY,
            "xuanyi", StyleModule.SUSPENSE,
            "dushi", StyleModule.LITERARY,
            "qingxiaoshuo", StyleModule.ROMANCE,
            "tongren", StyleModule.FANTASY,
            "duanpian", StyleModule.LITERARY
    );

    /**
     * 体裁 → 可能的辅助风格候选（按优先级排序）
     */
    private static final Map<String, List<StyleModule>> GENRE_AUX_CANDIDATES = Map.of(
            "xianxia", List.of(StyleModule.LITERARY, StyleModule.HUMOR),
            "xuanhuan", List.of(StyleModule.HUMOR, StyleModule.LITERARY),
            "qihuan", List.of(StyleModule.MYSTERY, StyleModule.LITERARY),
            "wuxia", List.of(StyleModule.ROMANCE, StyleModule.SUSPENSE),
            "xuanyi", List.of(StyleModule.HORROR, StyleModule.MYSTERY),
            "dushi", List.of(StyleModule.ROMANCE, StyleModule.HUMOR),
            "qingxiaoshuo", List.of(StyleModule.LITERARY, StyleModule.HUMOR),
            "tongren", List.of(StyleModule.HUMOR, StyleModule.ROMANCE),
            "duanpian", List.of(StyleModule.HORROR, StyleModule.SUSPENSE)
    );

    // ─── 关键词 → 风格映射（基于章节推进方式和读者感受判断） ───

    /** 章节推进方式关键词 → 风格 */
    private static final Map<String, StyleModule> DRIVER_KEYWORD_STYLE = Map.ofEntries(
            Map.entry("战斗", StyleModule.FANTASY),
            Map.entry("对决", StyleModule.FANTASY),
            Map.entry("修炼", StyleModule.FANTASY),
            Map.entry("突破", StyleModule.FANTASY),
            Map.entry("能力", StyleModule.FANTASY),
            Map.entry("规则", StyleModule.FANTASY),
            Map.entry("冒险", StyleModule.FANTASY),
            Map.entry("探索", StyleModule.FANTASY),
            Map.entry("追杀", StyleModule.SUSPENSE),
            Map.entry("逃亡", StyleModule.SUSPENSE),
            Map.entry("隐藏", StyleModule.SUSPENSE),
            Map.entry("秘密", StyleModule.SUSPENSE),
            Map.entry("真相", StyleModule.MYSTERY),
            Map.entry("调查", StyleModule.MYSTERY),
            Map.entry("线索", StyleModule.MYSTERY),
            Map.entry("推理", StyleModule.MYSTERY),
            Map.entry("证据", StyleModule.MYSTERY),
            Map.entry("表白", StyleModule.ROMANCE),
            Map.entry("约会", StyleModule.ROMANCE),
            Map.entry("误会", StyleModule.ROMANCE),
            Map.entry("牺牲", StyleModule.ROMANCE),
            Map.entry("守护", StyleModule.ROMANCE),
            Map.entry("背叛", StyleModule.ROMANCE),
            Map.entry("玩笑", StyleModule.HUMOR),
            Map.entry("吐槽", StyleModule.HUMOR),
            Map.entry("出糗", StyleModule.HUMOR),
            Map.entry("尴尬", StyleModule.HUMOR),
            Map.entry("恐怖", StyleModule.HORROR),
            Map.entry("噩梦", StyleModule.HORROR),
            Map.entry("诡异", StyleModule.HORROR),
            Map.entry("污染", StyleModule.HORROR),
            Map.entry("离别", StyleModule.LITERARY),
            Map.entry("成长", StyleModule.LITERARY),
            Map.entry("选择", StyleModule.LITERARY),
            Map.entry("代价", StyleModule.LITERARY),
            Map.entry("回忆", StyleModule.LITERARY)
    );

    /** 读者感受关键词 → 风格 */
    private static final Map<String, StyleModule> FEELING_KEYWORD_STYLE = Map.ofEntries(
            Map.entry("笑", StyleModule.HUMOR),
            Map.entry("轻松", StyleModule.HUMOR),
            Map.entry("紧张", StyleModule.SUSPENSE),
            Map.entry("不安", StyleModule.HORROR),
            Map.entry("毛骨悚然", StyleModule.HORROR),
            Map.entry("好奇", StyleModule.MYSTERY),
            Map.entry("烧脑", StyleModule.MYSTERY),
            Map.entry("心动", StyleModule.ROMANCE),
            Map.entry("甜蜜", StyleModule.ROMANCE),
            Map.entry("感动", StyleModule.LITERARY),
            Map.entry("震撼", StyleModule.LITERARY),
            Map.entry("新奇", StyleModule.FANTASY),
            Map.entry("宏大", StyleModule.FANTASY),
            Map.entry("窒息", StyleModule.HORROR)
    );

    /**
     * 核心方法：根据体裁、章纲、用户焦点选择风格模块
     *
     * @param genreId        体裁ID（如 xianxia、xuanyi）
     * @param chapterOutline 章节大纲文本
     * @param userFocus      用户焦点/作者指示
     * @return 风格选择结果
     */
    public StyleSelection selectStyles(String genreId, String chapterOutline, String userFocus) {
        // 1. 合并所有分析文本
        String combined = normalizeText(
                (chapterOutline != null ? chapterOutline : "") + " " +
                (userFocus != null ? userFocus : "")
        );

        // 2. 从体裁获取默认主风格
        StyleModule genreDefault = GENRE_DEFAULT_STYLE.getOrDefault(genreId, StyleModule.LITERARY);

        // 3. 从文本中识别章节推进方式 → 确定主风格
        StyleModule primary = detectPrimaryStyle(combined, genreDefault);

        // 4. 从文本中识别读者感受 → 确定次风格（与主风格不同）
        StyleModule secondary = detectSecondaryStyle(combined, primary, genreDefault);

        // 5. 从体裁辅助候选 + 文本补充 → 确定辅助风格
        StyleModule auxiliary = detectAuxiliaryStyle(combined, primary, secondary, genreId);

        // 6. 收集优先兑现项和伪风格警示
        List<String> priorities = collectPriorities(primary, secondary, auxiliary);
        List<String> warnings = collectWarnings(primary, secondary, auxiliary);

        // 7. 构建风格上下文文本
        String styleContext = buildStyleContext(primary, secondary, auxiliary);

        StyleSelection selection = new StyleSelection(primary, secondary, auxiliary,
                styleContext, priorities, warnings);

        log.info("风格路由结果: genre={}, primary={}, secondary={}, auxiliary={}",
                genreId,
                primary != null ? primary.getChineseName() : "null",
                secondary != null ? secondary.getChineseName() : "null",
                auxiliary != null ? auxiliary.getChineseName() : "null");

        return selection;
    }

    /**
     * 简洁版风格选择 — 仅凭体裁，不做文本分析
     */
    public StyleSelection selectByGenre(String genreId) {
        StyleModule primary = GENRE_DEFAULT_STYLE.getOrDefault(genreId, StyleModule.LITERARY);
        List<StyleModule> auxCandidates = GENRE_AUX_CANDIDATES.getOrDefault(genreId, List.of());
        StyleModule auxiliary = auxCandidates.isEmpty() ? null : auxCandidates.get(0);
        // 辅助不能与主重复
        if (auxiliary == primary && auxCandidates.size() > 1) {
            auxiliary = auxCandidates.get(1);
        } else if (auxiliary == primary) {
            auxiliary = null;
        }

        List<String> priorities = collectPriorities(primary, null, auxiliary);
        List<String> warnings = collectWarnings(primary, null, auxiliary);
        String styleContext = buildStyleContext(primary, null, auxiliary);

        StyleSelection selection = new StyleSelection(primary, null, auxiliary,
                styleContext, priorities, warnings);

        log.info("风格路由(体裁默认): genre={}, primary={}, auxiliary={}",
                genreId, primary.getChineseName(),
                auxiliary != null ? auxiliary.getChineseName() : "null");

        return selection;
    }

    /**
     * 将选中的风格模块转化为可注入 Writer prompt 的风格上下文文本
     */
    public String buildStyleContext(StyleSelection selection) {
        return buildStyleContext(selection.primary(), selection.secondary(), selection.auxiliary());
    }

    /**
     * 构建风格上下文文本 — 包括核心原则、子型、优先兑现、伪风格警示
     */
    private String buildStyleContext(StyleModule primary, StyleModule secondary, StyleModule auxiliary) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 风格调度\n\n");

        // 主风格
        sb.append("### 主风格：").append(primary.getChineseName()).append("\n");
        sb.append("**核心原则**：").append(primary.getCorePrinciple()).append("\n");
        sb.append("**子型方向**：").append(primary.getSubTypes()).append("\n");
        sb.append("**本章优先兑现**：\n");
        for (String p : primary.getChapterPriorities()) {
            sb.append("- ").append(p).append("\n");
        }
        sb.append("**伪风格警示**：").append(primary.getPseudoStyleWarning()).append("\n\n");

        // 次风格（如果有）
        if (secondary != null) {
            sb.append("### 次风格：").append(secondary.getChineseName()).append("\n");
            sb.append("**核心原则**：").append(secondary.getCorePrinciple()).append("\n");
            sb.append("**子型方向**：").append(secondary.getSubTypes()).append("\n");
            sb.append("**本章优先兑现**：\n");
            for (String p : secondary.getChapterPriorities()) {
                sb.append("- ").append(p).append("\n");
            }
            sb.append("**伪风格警示**：").append(secondary.getPseudoStyleWarning()).append("\n\n");
        }

        // 辅助风格（如果有）
        if (auxiliary != null) {
            sb.append("### 辅助风格：").append(auxiliary.getChineseName()).append("\n");
            sb.append("**核心原则**：").append(auxiliary.getCorePrinciple()).append("\n");
            sb.append("**融合要求**：辅助风格不独立成段，应自然融入主/次风格的间隙中，作为氛围底色或节奏调节。\n");
            sb.append("**本章优先兑现**：\n");
            for (String p : auxiliary.getChapterPriorities()) {
                sb.append("- ").append(p).append("\n");
            }
            sb.append("**伪风格警示**：").append(auxiliary.getPseudoStyleWarning()).append("\n\n");
        }

        // 组合注意事项
        if (secondary != null || auxiliary != null) {
            sb.append("### 风格组合注意事项\n");
            if (secondary != null) {
                sb.append("- 主风格（").append(primary.getChineseName())
                        .append("）引领本章基调，次风格（").append(secondary.getChineseName())
                        .append("）在关键转折/对话/描写处浮现\n");
            }
            if (auxiliary != null) {
                sb.append("- 辅助风格（").append(auxiliary.getChineseName())
                        .append("）作为底色融入，不要喧宾夺主\n");
            }
            int total = 1 + (secondary != null ? 1 : 0) + (auxiliary != null ? 1 : 0);
            sb.append("- 本章共激活 ").append(total)
                    .append(" 个风格模块，优先保证主风格的优先兑现项\n");
        }

        return sb.toString();
    }

    /**
     * 检测主风格 — 遍历章节推进关键词，统计各风格命中次数
     */
    private StyleModule detectPrimaryStyle(String text, StyleModule genreDefault) {
        if (text.isBlank()) return genreDefault;

        Map<StyleModule, Integer> scores = new EnumMap<>(StyleModule.class);
        for (var entry : DRIVER_KEYWORD_STYLE.entrySet()) {
            if (text.contains(entry.getKey())) {
                scores.merge(entry.getValue(), 1, Integer::sum);
            }
        }

        if (scores.isEmpty()) return genreDefault;

        // 返回得分最高的风格
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(genreDefault);
    }

    /**
     * 检测次风格 — 从读者感受关键词中提取，必须与主风格不同
     */
    private StyleModule detectSecondaryStyle(String text, StyleModule primary, StyleModule genreDefault) {
        if (text.isBlank()) return null;

        Map<StyleModule, Integer> scores = new EnumMap<>(StyleModule.class);
        for (var entry : FEELING_KEYWORD_STYLE.entrySet()) {
            if (text.contains(entry.getKey())) {
                scores.merge(entry.getValue(), 1, Integer::sum);
            }
        }

        if (scores.isEmpty()) return null;

        // 排除主风格，取得分最高的
        return scores.entrySet().stream()
                .filter(e -> e.getKey() != primary)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 检测辅助风格 — 从体裁候选+文本补充中选择
     */
    private StyleModule detectAuxiliaryStyle(String text, StyleModule primary,
                                              StyleModule secondary, String genreId) {
        List<StyleModule> candidates = GENRE_AUX_CANDIDATES.getOrDefault(genreId, List.of());

        // 排除已选的主/次风格
        Set<StyleModule> selected = new HashSet<>();
        selected.add(primary);
        if (secondary != null) selected.add(secondary);

        // 按优先级选第一个未被占用的候选
        for (StyleModule candidate : candidates) {
            if (!selected.contains(candidate)) {
                return candidate;
            }
        }

        // 如果没有合适的候选，尝试从文本中找第三个风格
        if (!text.isBlank()) {
            Map<StyleModule, Integer> allScores = new EnumMap<>(StyleModule.class);
            for (var entry : DRIVER_KEYWORD_STYLE.entrySet()) {
                if (text.contains(entry.getKey())) {
                    allScores.merge(entry.getValue(), 1, Integer::sum);
                }
            }
            for (var entry : FEELING_KEYWORD_STYLE.entrySet()) {
                if (text.contains(entry.getKey())) {
                    allScores.merge(entry.getValue(), 1, Integer::sum);
                }
            }
            return allScores.entrySet().stream()
                    .filter(e -> !selected.contains(e.getKey()))
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        return null;
    }

    /**
     * 收集所有优先兑现项（去重）
     */
    private List<String> collectPriorities(StyleModule primary, StyleModule secondary, StyleModule auxiliary) {
        List<String> all = new ArrayList<>();
        all.addAll(primary.getChapterPriorities());
        if (secondary != null) all.addAll(secondary.getChapterPriorities());
        if (auxiliary != null) all.addAll(auxiliary.getChapterPriorities());
        return all.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 收集所有伪风格警示（去重）
     */
    private List<String> collectWarnings(StyleModule primary, StyleModule secondary, StyleModule auxiliary) {
        List<String> all = new ArrayList<>();
        all.add(primary.getPseudoStyleWarning());
        if (secondary != null) all.add(secondary.getPseudoStyleWarning());
        if (auxiliary != null) all.add(auxiliary.getPseudoStyleWarning());
        return all.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 文本归一化 — 去除多余空白
     */
    private String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }
}
