package com.yunmo.agent.hook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钩子系统核心 — 负责钩子选择、Prompt 生成、跨章悬念弧管理
 *
 * 选择逻辑：
 * - 根据章节位置（开篇/中段/高潮/结局）自动选择合适强度
 * - 中间章节→好奇(1)/关切(2)
 * - 高潮章节→迫切(3)/生存(4)
 * - 结局前→生存(4)/终极(5)
 * - 章首引子和章尾钩子各随机选取（避免连续3章使用同一式）
 */
@Component
public class HookSystem {

    private static final Logger log = LoggerFactory.getLogger(HookSystem.class);
    private static final String REDIS_PREFIX = "yunmo:hook:";
    private static final Duration REDIS_TTL = Duration.ofDays(30);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;

    // 每部小说的钩子使用历史: novelId → [最近使用的钩子类型列表，按章节顺序]
    private final Map<String, List<HookType>> hookHistory = new ConcurrentHashMap<>();

    // 跨章悬念弧状态: novelId → ArcState
    private final Map<String, ArcState> arcStates = new ConcurrentHashMap<>();

    public HookSystem(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ================================================================
    // 主入口：为章节选择合适的钩子组合
    // ================================================================

    /**
     * 为指定章节选择章首引子 + 章尾钩子
     *
     * @param chapterOutline  章纲文本（用于判断内容倾向）
     * @param novelGenre      小说类型（如 xianxia、xuanhuan）
     * @param chapterNumber   当前章节号
     * @param totalChapters   全书总章数
     * @param novelId         小说ID（用于跨章弧追踪）
     * @return 钩子选择结果
     */
    public HookSelection selectChapterHooks(
            String chapterOutline,
            String novelGenre,
            int chapterNumber,
            int totalChapters,
            String novelId
    ) {
        // 0. 从 Redis 恢复状态（如果内存中没有）
        loadHookHistory(novelId);

        // 1. 计算悬念强度
        int intensity = calculateIntensity(chapterNumber, totalChapters, chapterOutline);

        // 2. 计算章节阶段
        ChapterPhase phase = calculatePhase(chapterNumber, totalChapters);

        // 3. 获取该小说的钩子使用历史
        List<HookType> usedHooks = hookHistory.getOrDefault(novelId, new ArrayList<>());

        // 4. 选择章首引子（避免连续3章重复）
        HookType opening = selectHook(HookType.openingHooks(), usedHooks, phase);

        // 5. 选择章尾钩子（避免连续3章重复，且不与章首选同一式）
        HookType closing = selectHook(HookType.closingHooks(), usedHooks, phase);
        // 确保章首和章尾不是同一类型（虽然分类不同，但做安全检查）
        int attempts = 0;
        while (closing == HookType.closingHooks().get(opening.formulaNumber() - 1) && attempts < 5) {
            closing = selectHook(HookType.closingHooks(), usedHooks, phase);
            attempts++;
        }

        // 6. 获取跨章悬念弧上下文
        String arcContext = getArcContext(novelId, chapterNumber, totalChapters);

        // 7. 构建定制化 Prompt
        String openingPrompt = buildCustomOpeningPrompt(opening, chapterOutline, intensity, phase);
        String closingPrompt = buildCustomClosingPrompt(closing, chapterOutline, intensity, phase);

        // 8. 记录钩子使用
        recordHookUsage(novelId, opening);
        recordHookUsage(novelId, closing);
        saveHookHistory(novelId);

        // 9. 前3章钩子（供选择逻辑参考）
        List<HookType> previousHooks = getRecentHooks(novelId, 3);

        log.info("[HookSystem] 章节{}钩子选择: 章首={}, 章尾={}, 强度={}, 阶段={}",
                chapterNumber, opening.chineseName(), closing.chineseName(), intensity, phase);

        return HookSelection.full(opening, openingPrompt, closing, closingPrompt,
                intensity, arcContext, previousHooks);
    }

    // ================================================================
    // Prompt 生成
    // ================================================================

    /**
     * 生成章首引子的定制化 Prompt
     */
    private String buildCustomOpeningPrompt(HookType hook, String outline,
                                             int intensity, ChapterPhase phase) {
        StringBuilder sb = new StringBuilder();
        sb.append(hook.promptTemplate());
        sb.append("\n\n【章首引子要求】\n");
        sb.append("- 长度：50-150字，单独成段，用「---」与正文分隔\n");
        sb.append("- 技法：").append(hook.chineseName()).append("（").append(hook.techniqueDescription()).append("）\n");
        sb.append("- 悬念强度目标：").append(intensityToLabel(intensity)).append("\n");
        sb.append("- 与章纲的关联：").append(buildOutlineConnection(hook, outline)).append("\n");
        return sb.toString();
    }

    /**
     * 生成章尾钩子的定制化 Prompt
     */
    private String buildCustomClosingPrompt(HookType hook, String outline,
                                             int intensity, ChapterPhase phase) {
        StringBuilder sb = new StringBuilder();
        sb.append(hook.promptTemplate());
        sb.append("\n\n【章尾钩子要求】\n");
        sb.append("- 长度：控制在1-3段内，戛然而止\n");
        sb.append("- 技法：").append(hook.chineseName()).append("（").append(hook.techniqueDescription()).append("）\n");
        sb.append("- 悬念强度目标：").append(intensityToLabel(intensity)).append("\n");
        sb.append("- 与章纲的关联：").append(buildOutlineConnection(hook, outline)).append("\n");
        sb.append("- 重要：钩子必须自然融入叙事，不要生硬地制造悬念\n");
        return sb.toString();
    }

    /**
     * 构建章首引子的写作指令 Prompt（注入到 Writer System Prompt 中）
     */
    public String buildOpeningHookPrompt(HookSelection selection) {
        if (selection == null || selection.openingHook() == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n## 章首引子要求\n");
        sb.append("本章正文开始前，必须先写一段50-150字的章首引子，用「---」与正文主体分隔。\n");
        sb.append("引子技法：").append(selection.openingHook().chineseName()).append("\n");
        sb.append("引子要求：").append(selection.openingPrompt()).append("\n");
        sb.append("悬念强度：").append(intensityToLabel(selection.suspenseIntensity())).append("\n");
        return sb.toString();
    }

    /**
     * 构建章尾钩子的写作指令 Prompt（注入到 Writer System Prompt 中）
     */
    public String buildClosingHookPrompt(HookSelection selection) {
        if (selection == null || selection.closingHook() == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n## 章尾钩子要求（铁律）\n");
        sb.append("每章结尾必须留下悬念钩子，不可平铺直叙地结束。\n");
        sb.append("钩子技法：").append(selection.closingHook().chineseName()).append("\n");
        sb.append("钩子要求：").append(selection.closingPrompt()).append("\n");
        sb.append("悬念强度：").append(intensityToLabel(selection.suspenseIntensity())).append("\n");
        if (selection.arcContext() != null && !selection.arcContext().isEmpty()) {
            sb.append("\n跨章悬念弧：\n").append(selection.arcContext()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建跨章悬念弧 Prompt
     */
    public String buildSuspensionArcPrompt(String novelId, int chapterNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 跨章悬念弧管理\n");
        sb.append("你需要在写作中同时运行3条悬念弧：\n");
        sb.append("1. **短弧**（2-3章周期）：即时满足的小悬念，本章或下章解答。\n");
        sb.append("   - 例如：某个神秘人的身份、某个小道具的用途、某个角色的小秘密。\n");
        sb.append("2. **中弧**（5-8章周期）：渐进揭露的较大悬念，在卷末或高朝处解答。\n");
        sb.append("   - 例如：幕后黑手的步步逼近、主角身世的层层揭开、功法/体系的逐步完善。\n");
        sb.append("3. **长弧**（贯穿全书）：全书核心悬念，每章埋一点线索但不揭示全貌。\n");
        sb.append("   - 例如：世界的终极真相、主角命运的终极目的、反派的大计划。\n");

        ArcState arc = arcStates.get(novelId);
        if (arc != null) {
            sb.append("\n当前悬念弧状态：\n");
            sb.append(arc.toPromptContext());
        }

        return sb.toString();
    }

    // ================================================================
    // 内部选择逻辑
    // ================================================================

    /**
     * 随机选择钩子，避免连续3章使用同一式
     */
    private HookType selectHook(List<HookType> candidates, List<HookType> usedHooks,
                                 ChapterPhase phase) {
        // 取最近3个使用的钩子（仅限同一分类）
        List<HookType> recentRelevant = new ArrayList<>();
        for (int i = usedHooks.size() - 1; i >= 0 && recentRelevant.size() < 3; i--) {
            HookType h = usedHooks.get(i);
            if (candidates.contains(h)) {
                recentRelevant.add(h);
            }
        }

        // 过滤掉最近3章已使用的钩子
        Set<HookType> recentSet = new HashSet<>(recentRelevant);
        List<HookType> available = new ArrayList<>(candidates);
        // 如果还有足够的未使用钩子，则过滤
        if (available.size() > recentSet.size()) {
            available.removeAll(recentSet);
        }

        // 随机选择一个
        return available.get(random.nextInt(available.size()));
    }

    /**
     * 根据章节位置计算悬念强度
     */
    private int calculateIntensity(int chapterNumber, int totalChapters, String outline) {
        ChapterPhase phase = calculatePhase(chapterNumber, totalChapters);

        // 根据大纲内容检测是否有战斗/冲突关键词，提升强度
        boolean hasConflict = outline != null && containsConflictKeywords(outline);

        return switch (phase) {
            case OPENING -> hasConflict ? 3 : 2;      // 开篇：好奇→关切，有冲突则迫切
            case DEVELOPMENT -> hasConflict ? 2 : 1;  // 中段：好奇为主，保持读者兴趣
            case RISING -> hasConflict ? 3 : 2;        // 上升期：关切→迫切
            case CLIMAX -> hasConflict ? 5 : 4;        // 高潮：生存→终极
            case FALLING -> 3;                          // 回落：迫切（为下一波高潮做铺垫）
            case FINALE -> 4;                           // 结局前：生存
        };
    }

    /**
     * 章节阶段判定
     */
    private ChapterPhase calculatePhase(int chapterNumber, int totalChapters) {
        if (totalChapters <= 0) totalChapters = 100; // 默认设一个大数

        double ratio = (double) chapterNumber / totalChapters;

        if (chapterNumber <= 3) return ChapterPhase.OPENING;           // 前3章：开篇
        if (ratio < 0.25) return ChapterPhase.DEVELOPMENT;             // 前25%：发展中段
        if (ratio < 0.5) return ChapterPhase.DEVELOPMENT;              // 25%-50%：发展中段
        if (ratio < 0.7) return ChapterPhase.RISING;                   // 50%-70%：上升期
        if (ratio < 0.85) return ChapterPhase.CLIMAX;                  // 70%-85%：高潮
        if (ratio < 0.95) return ChapterPhase.FALLING;                 // 85%-95%：回落
        return ChapterPhase.FINALE;                                     // 最后5%：结局
    }

    /**
     * 检测大纲中是否包含冲突性关键词
     */
    private boolean containsConflictKeywords(String outline) {
        if (outline == null || outline.isEmpty()) return false;
        String lower = outline.toLowerCase();
        return lower.contains("战斗") || lower.contains("决斗") || lower.contains("刺杀")
                || lower.contains("围攻") || lower.contains("死") || lower.contains("杀")
                || lower.contains("危机") || lower.contains("背叛") || lower.contains("真相")
                || lower.contains("陷阱") || lower.contains("偷袭") || lower.contains("决战")
                || lower.contains("惨") || lower.contains("血") || lower.contains("逃");
    }

    /**
     * 根据钩子类型和章纲构建关联说明
     */
    private String buildOutlineConnection(HookType hook, String outline) {
        if (outline == null || outline.isEmpty()) {
            return "请根据本章内容自行关联";
        }

        // 从章纲中提取关键元素作为钩子的锚点
        String brief = outline.length() > 200 ? outline.substring(0, 200) + "…" : outline;
        return "以下章纲内容可作为钩子的锚点：" + brief;
    }

    // ================================================================
    // 跨章悬念弧管理
    // ================================================================

    /**
     * 获取当前小说的悬念弧上下文
     */
    private String getArcContext(String novelId, int chapterNumber, int totalChapters) {
        ArcState arc = arcStates.computeIfAbsent(novelId, k -> new ArcState());
        arc.updateChapter(chapterNumber);

        StringBuilder sb = new StringBuilder();
        sb.append("当前章节编号: ").append(chapterNumber).append("\n");

        // 短弧状态
        if (arc.shortArcChapter > 0) {
            sb.append("- 短弧（2-3章）: 始于第").append(arc.shortArcChapter).append("章");
            int progress = chapterNumber - arc.shortArcChapter;
            if (progress >= 2) sb.append("（已到解答窗口，可回收或推进此短弧）");
            else sb.append("（推进中，第").append(progress + 1).append("章）");
            sb.append("\n");
        }

        // 中弧状态
        if (arc.midArcChapter > 0) {
            sb.append("- 中弧（5-8章）: 始于第").append(arc.midArcChapter).append("章");
            int progress = chapterNumber - arc.midArcChapter;
            if (progress >= 5) sb.append("（已到解答窗口，考虑部分揭示）");
            else sb.append("（推进中，第").append(progress + 1).append("章）");
            sb.append("\n");
        }

        // 长弧
        if (arc.longArcChapter > 0) {
            sb.append("- 长弧（全书）: 始于第").append(arc.longArcChapter).append("章");
            sb.append("（缓慢推进，每章埋少量线索）\n");
        }

        return sb.toString();
    }

    /**
     * 更新悬念弧进度（在章节生成后调用）
     */
    public void advanceArcs(String novelId, int chapterNumber,
                             boolean shortArcResolved, boolean midArcAdvanced) {
        ArcState arc = arcStates.computeIfAbsent(novelId, k -> new ArcState());

        // 如果短弧已解答，开启新的短弧
        if (shortArcResolved) {
            arc.shortArcChapter = chapterNumber + 1;
            log.info("[HookSystem] 短弧解答，新短弧起点: chapter={}", chapterNumber + 1);
        }

        // 如果中弧有进展，记录
        if (midArcAdvanced) {
            log.info("[HookSystem] 中弧推进: chapter={}", chapterNumber);
        }

        // 每10章左右检查是否需要开启新中弧
        if (chapterNumber - arc.midArcChapter >= 8) {
            arc.midArcChapter = chapterNumber + 1;
            log.info("[HookSystem] 新中弧开启: chapter={}", chapterNumber + 1);
        }

        // 第1章或每30章开启新长弧支线
        if (arc.longArcChapter == 0) {
            arc.longArcChapter = 1;
        }
    }

    // ================================================================
    // 历史管理
    // ================================================================

    private void recordHookUsage(String novelId, HookType hook) {
        hookHistory.compute(novelId, (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(hook);
            // 只保留最近30条记录，避免内存膨胀
            if (v.size() > 30) {
                return new ArrayList<>(v.subList(v.size() - 30, v.size()));
            }
            return v;
        });
    }

    /** 从 Redis 加载钩子历史（如果内存中没有） */
    private void loadHookHistory(String novelId) {
        if (hookHistory.containsKey(novelId)) return;
        try {
            String json = redis.opsForValue().get(REDIS_PREFIX + novelId);
            if (json != null && !json.isEmpty()) {
                List<String> names = objectMapper.readValue(json, new TypeReference<>() {});
                List<HookType> hooks = new ArrayList<>();
                for (String name : names) {
                    try { hooks.add(HookType.valueOf(name)); } catch (IllegalArgumentException ignored) {}
                }
                hookHistory.put(novelId, hooks);
                log.debug("[HookSystem] 从 Redis 恢复钩子历史: novel={}, count={}", novelId, hooks.size());
            }
        } catch (Exception e) {
            log.debug("[HookSystem] 加载钩子历史失败: {}", e.getMessage());
        }
    }

    /** 保存钩子历史到 Redis */
    private void saveHookHistory(String novelId) {
        try {
            List<HookType> hooks = hookHistory.getOrDefault(novelId, List.of());
            List<String> names = hooks.stream().map(Enum::name).toList();
            String json = objectMapper.writeValueAsString(names);
            redis.opsForValue().set(REDIS_PREFIX + novelId, json, REDIS_TTL);
        } catch (Exception e) {
            log.debug("[HookSystem] 保存钩子历史失败: {}", e.getMessage());
        }
    }

    private List<HookType> getRecentHooks(String novelId, int count) {
        List<HookType> history = hookHistory.getOrDefault(novelId, List.of());
        int from = Math.max(0, history.size() - count);
        return history.subList(from, history.size());
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private String intensityToLabel(int intensity) {
        return switch (intensity) {
            case 1 -> "好奇（读者想知道答案）";
            case 2 -> "关切（读者担心角色）";
            case 3 -> "迫切（读者急于知道后续）";
            case 4 -> "生存（角色生死攸关）";
            case 5 -> "终极（世界命运/终极真相）";
            default -> "未知强度";
        };
    }

    // ================================================================
    // 章节阶段枚举
    // ================================================================

    public enum ChapterPhase {
        OPENING("开篇"),
        DEVELOPMENT("发展中段"),
        RISING("上升期"),
        CLIMAX("高潮"),
        FALLING("回落"),
        FINALE("结局");

        private final String chineseName;

        ChapterPhase(String chineseName) {
            this.chineseName = chineseName;
        }

        public String chineseName() { return chineseName; }
    }

    // ================================================================
    // 跨章悬念弧状态（内部类）
    // ================================================================

    private static class ArcState {
        int shortArcChapter = 0;   // 当前短弧起点章节
        int midArcChapter = 0;     // 当前中弧起点章节
        int longArcChapter = 0;    // 长弧起点章节（通常是1）
        int currentChapter = 0;

        void updateChapter(int chapter) {
            this.currentChapter = chapter;
            // 自动初始化弧起点
            if (shortArcChapter == 0) shortArcChapter = Math.max(1, chapter);
            if (midArcChapter == 0) midArcChapter = Math.max(1, chapter);
            if (longArcChapter == 0) longArcChapter = 1;
        }

        String toPromptContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("- 短弧进度: 第").append(shortArcChapter).append("章 → 第").append(currentChapter).append("章");
            if (currentChapter - shortArcChapter >= 2) sb.append(" (可解答)");
            sb.append("\n");
            sb.append("- 中弧进度: 第").append(midArcChapter).append("章 → 第").append(currentChapter).append("章");
            if (currentChapter - midArcChapter >= 5) sb.append(" (可部分揭示)");
            sb.append("\n");
            sb.append("- 长弧进度: 第").append(longArcChapter).append("章 → 第").append(currentChapter).append("章");
            sb.append(" (每章埋线索)\n");
            return sb.toString();
        }
    }
}
