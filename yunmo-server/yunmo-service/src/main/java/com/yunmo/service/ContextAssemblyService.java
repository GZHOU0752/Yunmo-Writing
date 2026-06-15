package com.yunmo.service;

import com.yunmo.common.config.AppProperties;
import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.Character;
import com.yunmo.domain.entity.Novel;
import com.yunmo.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 上下文组装服务 — 替代 Python context_assembly.py ContextAssembler
 *
 * 4 层上下文预算 ~46K tokens:
 *   Layer 1: 静态圣经 (~8K) — 类型规则 + 角色总览 + 类型铁律
 *   Layer 2: 活跃上下文 (~20K) — 上一章全文/摘要 + 登场角色状态
 *   Layer 3: 压缩历史 (~15K) — 前10章摘要
 *   Layer 4: 章节计划 (~3K) — 大纲 + 因果句
 */
@Service
public class ContextAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(ContextAssemblyService.class);
    private static final int TOKEN_BUDGET = 46000;

    private final NovelRepository novelRepo;
    private final ChapterRepository chapterRepo;
    private final CharacterRepository characterRepo;
    private final OutlineNodeRepository outlineRepo;
    private final AppProperties appProperties;

    public ContextAssemblyService(NovelRepository novelRepo, ChapterRepository chapterRepo,
                                   CharacterRepository characterRepo, OutlineNodeRepository outlineRepo,
                                   AppProperties appProperties) {
        this.novelRepo = novelRepo;
        this.chapterRepo = chapterRepo;
        this.characterRepo = characterRepo;
        this.outlineRepo = outlineRepo;
        this.appProperties = appProperties;
    }

    /**
     * 组装 4 层上下文
     */
    public String assemble(String novelId, int chapterNumber,
                           Map<String, Object> genreConfig,
                           List<Character> characters
    ) {
        Novel novel = novelRepo.findById(novelId).orElseThrow();

        StringBuilder context = new StringBuilder();

        // Layer 1: 静态圣经 (~8K)
        context.append(buildBible(genreConfig, characters));

        // Layer 2: 活跃上下文 (~20K) — 仅加载上一章正文
        Chapter prevChapter = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber - 1)
                .orElse(null);
        context.append(buildActiveContext(prevChapter, characters, chapterNumber));

        // Layer 3: 压缩历史 (~15K) — 加载前 10 章（不含正文的摘要元数据）
        List<Chapter> recentChapters = chapterRepo
                .findPreviousChapters(novelId, chapterNumber)
                .stream()
                .limit(10)
                .toList();
        context.append(buildHistory(recentChapters));

        // Layer 4: 章节计划 (~3K)
        context.append(buildPlan(novelId, chapterNumber));

        return trimToBudget(context.toString());
    }

    private String buildBible(Map<String, Object> genreConfig, List<Character> characters) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 类型铁律 ===\n");

        if (genreConfig != null) {
            sb.append("文风蓝图: ").append(genreConfig.getOrDefault("writing_blueprint", "无")).append("\n");
            @SuppressWarnings("unchecked")
            var terms = (List<String>) genreConfig.get("forbidden_terms");
            if (terms != null) {
                sb.append("禁止术语: ").append(String.join("、", terms)).append("\n");
            }
        }

        sb.append("\n=== 角色总览 ===\n");
        for (var ch : characters) {
            sb.append(formatCharacterBrief(ch));
        }

        return sb.toString();
    }

    private String buildActiveContext(Chapter prevChapter,
                                       List<Character> characters,
                                       int currentChapterNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 活跃上下文 ===\n");

        // 上一章正文（前 3000 字）
        if (prevChapter != null && prevChapter.getContent() != null) {
            String content = prevChapter.getContent();
            sb.append("上一章摘要: ")
                    .append(content.length() > 3000
                            ? content.substring(0, 3000) + "..."
                            : content)
                    .append("\n");
        }

        // 活跃角色当前状态
        var activeChars = characters.stream()
                .filter(c -> c.getLastAppearanceChapter() != null
                        && c.getLastAppearanceChapter() >= currentChapterNumber - 3)
                .toList();
        if (!activeChars.isEmpty()) {
            sb.append("\n登场角色状态:\n");
            for (var ch : activeChars) {
                sb.append(formatCharacterBrief(ch));
            }
        }

        return sb.toString();
    }

    private String buildHistory(List<Chapter> recentChapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 历史摘要 ===\n");

        for (var c : recentChapters) {
            sb.append("第").append(c.getChapterNumber()).append("章: ");
            sb.append(c.getSummary() != null ? c.getSummary() : "(无摘要)");
            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildPlan(String novelId, int chapterNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 本章大纲 ===\n");

        var nodes = outlineRepo.findByNovelIdAndChapterNumber(novelId, chapterNumber);
        if (!nodes.isEmpty()) {
            for (var node : nodes) {
                sb.append("- ").append(node.getTitle()).append("\n");
                if (node.getCausalSentence() != null) {
                    sb.append("  因果: ").append(node.getCausalSentence()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private String formatCharacterBrief(Character c) {
        return String.format("- %s [%s] 重要度:%d 状态:%s\n",
                c.getName(), c.getRole(), c.getImportance(),
                c.getCurrentState() != null ? c.getCurrentState().toString() : "未知");
    }

    /**
     * Token 预算裁剪 — P2→P1→P0 策略
     */
    private String trimToBudget(String context) {
        int estimatedTokens = context.length() / 4;
        if (estimatedTokens <= appProperties.getContextTokenBudget()) {
            return context;
        }
        log.warn("上下文超出预算 {} tokens，执行裁剪", estimatedTokens);
        int targetChars = appProperties.getContextTokenBudget() * 4;
        return context.substring(0, Math.min(context.length(), targetChars));
    }
}
