package com.yunmo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 写作指南加载服务 — 根据章节大纲内容自动匹配并加载对应的写作指南
 */
@Service
public class WritingGuideService {

    private static final Logger log = LoggerFactory.getLogger(WritingGuideService.class);
    private static final String GUIDES_DIR = "writing-guides";

    private final Map<String, String> guideCache = new HashMap<>();

    /** 始终注入的基础指南（不依赖关键词匹配） */
    private static final List<String> DEFAULT_GUIDES = List.of(
        "de-ai-patterns.md",         // 去AI味（句式替换+自检）
        "rhythm-humanize.md",        // 节奏人工化（打碎均匀句长）
        "short-sentence-style.md",   // 短句网感（移动端追读优化）
        "chapter-hooks.md",          // 章末钩子（5种钩子类型）
        "character-psychology.md",   // 六步人物心理分析（OOC/降智/信息越界防护）
        "reader-psychology.md",      // 读者心理学（期待管理/信息落差/情绪节拍/沉没成本）
        "immersion-techniques.md"    // 代入感技法（五感/画面/自然信息交代）
    );

    /** 场景类型 → 指南文件 + 触发关键词（按需加载） */
    private static final Map<String, GuideTrigger> GUIDE_TRIGGERS = Map.ofEntries(
        Map.entry("combat", new GuideTrigger("combat-scenes.md",
            "战斗", "打斗", "对决", "追杀", "厮杀", "围剿", "搏杀", "刺杀", "伏击", "屠", "剿灭", "征讨")),
        Map.entry("emotion", new GuideTrigger("emotion-scenes.md",
            "告白", "离别", "重逢", "背叛", "和解", "求婚", "成亲", "殉情", "牺牲", "诀别")),
        Map.entry("dialogue", new GuideTrigger("dialogue-writing.md",
            "对话", "密谈", "审讯", "谈判", "争论", "辩论", "朝会", "议会", "酒宴")),
        Map.entry("subtext_dialogue", new GuideTrigger("subtext-dialogue.md",
            "潜台词", "试探", "话里有话", "交锋", "话锋", "逼问")),
        Map.entry("scene", new GuideTrigger("scene-description.md",
            "初到", "进入", "抵达", "降临", "穿越", "飞升", "下山", "入城", "新世界", "秘境", "遗迹")),
        Map.entry("sensory", new GuideTrigger("sensory-scene.md",
            "感官", "五感", "气味", "温度", "光线", "声音")),
        Map.entry("suspense", new GuideTrigger("suspense-writing.md",
            "诡异", "恐怖", "诅咒", "厉鬼", "凶杀", "失踪", "谜团", "尸变", "密室", "附身", "阴魂")),
        Map.entry("shuangwen", new GuideTrigger("fast-paced-shuangwen.md",
            "升级", "打脸", "突破", "碾压", "爽点", "爆点", "逆袭")),
        Map.entry("shuanggan", new GuideTrigger("shuanggan-control.md",
            "屈辱", "反击", "夺宝", "认主", "显圣", "打脸", "收获"))
    );

    public WritingGuideService() {
        // 启动时预加载所有指南
        loadAllGuides();
    }

    private void loadAllGuides() {
        // 收集所有需要加载的文件名（默认 + 触发匹配）
        Set<String> allFiles = new LinkedHashSet<>(DEFAULT_GUIDES);
        GUIDE_TRIGGERS.values().stream().map(g -> g.fileName).forEach(allFiles::add);

        for (String fileName : allFiles) {
            try {
                String content = loadFromClasspath(fileName);
                if (content != null) {
                    guideCache.put(fileName, content);
                    log.info("写作指南已加载: {} ({} 字)", fileName, content.length());
                }
            } catch (Exception e) {
                log.warn("写作指南加载失败: {}", fileName, e.getMessage());
            }
        }
    }

    private String loadFromClasspath(String fileName) {
        try {
            var is = getClass().getClassLoader()
                .getResourceAsStream(GUIDES_DIR + "/" + fileName);
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // fallback
        }
        // 尝试从文件系统加载
        try {
            Path path = Path.of("yunmo-server", "yunmo-server-app", "src", "main", "resources",
                GUIDES_DIR, fileName);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            // 再从运行目录试
            path = Path.of("src", "main", "resources", GUIDES_DIR, fileName);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    /**
     * 根据章节大纲内容匹配适用的写作指南
     * @param chapterPlan 章节大纲/写作计划文本
     * @return 匹配的指南文件名列表
     */
    public List<String> matchGuides(String chapterPlan) {
        if (chapterPlan == null || chapterPlan.isBlank()) return List.of();
        List<String> matched = new ArrayList<>();
        String lower = chapterPlan.toLowerCase();
        for (var entry : GUIDE_TRIGGERS.entrySet()) {
            for (String keyword : entry.getValue().keywords) {
                if (lower.contains(keyword)) {
                    matched.add(entry.getValue().fileName);
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * 获取匹配的写作指南内容（默认指南 + 关键词匹配指南，合并为一段文本）
     */
    public String getMatchedGuidesContent(String chapterPlan) {
        // 默认指南始终注入
        List<String> allFiles = new ArrayList<>(DEFAULT_GUIDES);
        // 关键词匹配的按需指南
        List<String> matchedFiles = matchGuides(chapterPlan);
        for (String f : matchedFiles) {
            if (!allFiles.contains(f)) allFiles.add(f);
        }

        if (allFiles.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 写作指南\n\n");
        for (String fileName : allFiles) {
            String content = guideCache.get(fileName);
            if (content != null && !content.isEmpty()) {
                sb.append(content).append("\n\n---\n\n");
            }
        }
        return sb.toString();
    }

    /** 获取所有已加载的指南文件名 */
    public Set<String> loadedGuides() {
        return guideCache.keySet();
    }

    private record GuideTrigger(String fileName, String... keywords) {}
}
