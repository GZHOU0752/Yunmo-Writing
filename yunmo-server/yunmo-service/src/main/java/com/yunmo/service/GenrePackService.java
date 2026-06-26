package com.yunmo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yunmo.service.style.StyleSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 体裁规则包服务 — 加载 genre_packs/*.yaml，按 genre_id 匹配
 * 提供核心承诺、禁用模式、章节节奏、连续性检查、追读法则、角色原型
 */
@Service
public class GenrePackService {

    private static final Logger log = LoggerFactory.getLogger(GenrePackService.class);
    private static final String PACKS_DIR = "genre_packs";

    private final Map<String, Map<String, Object>> packs = new LinkedHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /** genre → pack 文件名映射 */
    private static final Map<String, String> GENRE_FILE_MAP = Map.of(
        "xianxia", "xianxia.yaml",
        "xuanhuan", "xianxia.yaml",
        "wuxia", "wuxia.yaml",
        "qihuan", "fantasy.yaml",
        "xuanyi", "mystery.yaml",
        "dushi", "generic.yaml",
        "qingxiaoshuo", "generic.yaml",
        "tongren", "generic.yaml",
        "duanpian", "generic.yaml"
    );

    public GenrePackService() {
        loadAllPacks();
    }

    private void loadAllPacks() {
        Set<String> files = new HashSet<>(GENRE_FILE_MAP.values());
        for (String fileName : files) {
            try {
                String content = loadFromClasspath(fileName);
                if (content != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pack = yamlMapper.readValue(content, Map.class);
                    String genreId = (String) pack.get("genre_id");
                    if (genreId != null) {
                        packs.put(genreId, pack);
                        log.info("体裁规则包已加载: {} ({})", fileName, pack.getOrDefault("name", genreId));
                    }
                }
            } catch (Exception e) {
                log.warn("体裁规则包加载失败: {} — {}", fileName, e.getMessage());
            }
        }
    }

    private String loadFromClasspath(String fileName) {
        try {
            var is = getClass().getClassLoader()
                .getResourceAsStream(PACKS_DIR + "/" + fileName);
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) { /* fallback */ }
        try {
            Path path = Path.of("yunmo-server", "yunmo-server-app", "src", "main", "resources",
                PACKS_DIR, fileName);
            if (Files.exists(path)) return Files.readString(path, StandardCharsets.UTF_8);
            path = Path.of("src", "main", "resources", PACKS_DIR, fileName);
            if (Files.exists(path)) return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) { /* ignore */ }
        return null;
    }

    /** 获取体裁规则包，不存在则返回 generic */
    public Map<String, Object> getPack(String genreId) {
        Map<String, Object> pack = packs.get(genreId);
        if (pack != null) return pack;
        return packs.getOrDefault("generic", Collections.emptyMap());
    }

    /** 获取核心承诺列表 */
    @SuppressWarnings("unchecked")
    public List<String> getCorePromises(String genreId) {
        Map<String, Object> pack = getPack(genreId);
        Object obj = pack.get("core_promises");
        return obj instanceof List<?> l ? (List<String>) l : List.of();
    }

    /** 获取禁用模式列表 */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getForbiddenPatterns(String genreId) {
        Map<String, Object> pack = getPack(genreId);
        Object obj = pack.get("forbidden_patterns");
        if (obj instanceof List<?> l) {
            List<Map<String, String>> result = new ArrayList<>();
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    Object p = m.get("pattern");
                    Object r = m.get("reason");
                    entry.put("pattern", p != null ? p.toString() : "");
                    entry.put("reason", r != null ? r.toString() : "");
                    result.add(entry);
                }
            }
            return result;
        }
        return List.of();
    }

    /** 获取章节节奏建议 */
    public String getChapterRhythm(String genreId) {
        Map<String, Object> pack = getPack(genreId);
        Object obj = pack.get("chapter_rhythm");
        return obj instanceof String s ? s : "";
    }

    /** 获取连续性检查列表 */
    @SuppressWarnings("unchecked")
    public List<String> getContinuityChecks(String genreId) {
        Map<String, Object> pack = getPack(genreId);
        Object obj = pack.get("continuity_checks");
        return obj instanceof List<?> l ? (List<String>) l : List.of();
    }

    /** 获取追读法则 */
    @SuppressWarnings("unchecked")
    public List<String> getReaderPullRules(String genreId) {
        Map<String, Object> pack = getPack(genreId);
        Object obj = pack.get("reader_pull_rules");
        return obj instanceof List<?> l ? (List<String>) l : List.of();
    }

    /** 获取角色原型 */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getCharacterArchetypes(String genreId) {
        Map<String, Object> pack = getPack(genreId);
        Object obj = pack.get("character_archetypes");
        if (obj instanceof List<?> l) {
            return (List<Map<String, String>>) l;
        }
        return List.of();
    }

    /** 获取体裁规则包中所有规则，合并为一段注入 Writer prompt 的文本 */
    public String buildGenreContext(String genreId) {
        Map<String, Object> pack = getPack(genreId);
        if (pack.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 体裁专项规则（").append(pack.getOrDefault("name", genreId)).append("）\n\n");

        List<String> promises = getCorePromises(genreId);
        if (!promises.isEmpty()) {
            sb.append("### 核心承诺\n");
            for (String p : promises) sb.append("- ").append(p).append("\n");
            sb.append("\n");
        }

        List<Map<String, String>> forbidden = getForbiddenPatterns(genreId);
        if (!forbidden.isEmpty()) {
            sb.append("### 禁止模式\n");
            for (Map<String, String> fp : forbidden) {
                sb.append("- **").append(fp.get("pattern")).append("**：").append(fp.get("reason")).append("\n");
            }
            sb.append("\n");
        }

        String rhythm = getChapterRhythm(genreId);
        if (!rhythm.isEmpty()) {
            sb.append("### 章节节奏\n").append(rhythm).append("\n\n");
        }

        List<String> hooks = getReaderPullRules(genreId);
        if (!hooks.isEmpty()) {
            sb.append("### 追读法则\n");
            for (String h : hooks) sb.append("- ").append(h).append("\n");
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 将风格调度与体裁规则融合，生成完整的写作上下文
     *
     * 分层结构：
     *   体裁规则（硬约束）→ 核心承诺/禁用模式/节奏建议
     *   风格模块（软指导）→ 优先兑现/伪风格警示/子型选择
     *   融合区（衔接规则）→ 风格如何服务体裁承诺
     *
     * @param genreId   体裁ID
     * @param selection 风格选择结果（由StyleRouter产出）
     * @return 融合后的写作指导文本，可直接注入 Writer prompt
     */
    public String buildStyleGenreContext(String genreId, StyleSelection selection) {
        if (selection == null) {
            // 无风格调度时，回退到纯体裁规则
            return buildGenreContext(genreId);
        }

        StringBuilder sb = new StringBuilder();

        // ─── 体裁规则（硬约束） ───
        Map<String, Object> pack = getPack(genreId);
        sb.append("## 体裁规则（硬约束）\n");
        sb.append("体裁：").append(pack.getOrDefault("name", genreId)).append("\n\n");

        // 核心承诺
        List<String> promises = getCorePromises(genreId);
        if (!promises.isEmpty()) {
            sb.append("### 核心承诺（必须兑现，否则读者会认为不是这个类型）\n");
            for (String p : promises) {
                sb.append("- ").append(p).append("\n");
            }
            sb.append("\n");
        }

        // 禁用模式
        List<Map<String, String>> forbidden = getForbiddenPatterns(genreId);
        if (!forbidden.isEmpty()) {
            sb.append("### 禁止模式（出现即违规）\n");
            for (Map<String, String> fp : forbidden) {
                sb.append("- **").append(fp.get("pattern")).append("**：").append(fp.get("reason")).append("\n");
            }
            sb.append("\n");
        }

        // 节奏建议
        String rhythm = getChapterRhythm(genreId);
        if (!rhythm.isEmpty()) {
            sb.append("### 章节节奏\n").append(rhythm).append("\n\n");
        }

        // ─── 分隔线 ───
        sb.append("---\n\n");

        // ─── 风格模块（软指导） ───
        sb.append("## 风格指导（软约束）\n\n");
        sb.append(selection.styleContext());

        // ─── 融合区：风格如何服务体裁承诺 ───
        sb.append("---\n\n");
        sb.append("## 融合规则：风格服务体裁\n\n");
        sb.append("核心原则：风格是'怎么写'，体裁是'写什么'。\n");
        sb.append("体裁的硬约束优先于风格的软指导。当两者冲突时，以体裁核心承诺为准。\n\n");

        // 根据主风格给出融合建议
        sb.append("本风格组合对体裁的服务方式：\n");
        sb.append("- 主风格决定这一章的叙事质地和读者的主要情感体验\n");
        if (selection.hasSecondary()) {
            sb.append("- 次风格在主风格撑不起来时可以介入，特别是在对话、转折或描写段落\n");
        }
        if (selection.hasAuxiliary()) {
            sb.append("- 辅助风格作为底色，在不需要刻意推进时提供氛围支撑\n");
        }
        sb.append("- 所有风格技巧必须在体裁核心承诺的框架内运作\n\n");

        // 追读法则
        List<String> hooks = getReaderPullRules(genreId);
        if (!hooks.isEmpty()) {
            sb.append("### 追读法则（结合当前风格）\n");
            for (String h : hooks) {
                sb.append("- ").append(h).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
