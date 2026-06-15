package com.yunmo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.common.enums.ForeshadowStatus;
import com.yunmo.domain.entity.Foreshadow;
import com.yunmo.domain.repository.ForeshadowRepository;
import com.yunmo.llm.adapter.MultiProviderChatModel;
import com.yunmo.llm.provider.ProviderRegistry;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 伏笔管理服务
 */
@Service
public class ForeshadowService {

    private static final Logger log = LoggerFactory.getLogger(ForeshadowService.class);
    private final ForeshadowRepository repo;
    private final MultiProviderChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ForeshadowService(ForeshadowRepository repo, ProviderRegistry registry) {
        this.repo = repo;
        this.chatModel = MultiProviderChatModel.create(
                registry.get("deepseek"), "deepseek-v4-pro");
    }

    /** 调用 LLM 检测本章新埋设的伏笔 */
    public List<Foreshadow> detectNew(String novelId, int chapterNumber, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        log.info("伏笔检测: novel={}, chapter={}, contentLength={}", novelId, chapterNumber, content.length());

        // 截取前 3000 字用于检测
        String sample = content.length() > 3000 ? content.substring(0, 3000) : content;

        try {
            var response = chatModel.generate(
                    SystemMessage.from("""
                        你是伏笔分析专家。分析小说章节，检测作者埋设的伏笔。
                        伏笔包括但不限于：
                        - 未解释的神秘事件或现象
                        - 角色的秘密或隐藏动机
                        - 被提及但未出现的物品/人物
                        - 预言、暗示或铺垫
                        - 刻意回避的话题

                        输出 JSON 数组，每个伏笔包含：
                        - title: 伏笔简述（≤20字）
                        - description: 伏笔详细分析
                        - keywords: 关键词（逗号分隔，用于后续回收检测）
                        - urgency: 紧急度 1-10（越早回收越高）
                        - expected_chapter: 预计多少章后回收（默认后续5章内）

                        若无伏笔则返回空数组 []。只输出 JSON，不要其他文字。
                        """),
                    UserMessage.from(String.format("""
                        ## 章节内容（第%d章）
                        %s
                        """, chapterNumber, sample))
            );

            String json = response.content().text();
            log.info("伏笔检测 LLM 响应: {} 字符", json.length());

            // 提取 JSON 数组
            String cleaned = json.replace("```json", "").replace("```", "").trim();
            if (!cleaned.startsWith("[")) {
                int start = cleaned.indexOf('[');
                int end = cleaned.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    cleaned = cleaned.substring(start, end + 1);
                }
            }

            List<Map<String, Object>> items = objectMapper.readValue(
                    cleaned, new TypeReference<>() {});
            if (items.isEmpty()) {
                log.info("未检测到新伏笔");
                return List.of();
            }

            List<Foreshadow> result = new ArrayList<>();
            for (var item : items) {
                String title = (String) item.get("title");
                if (title == null || title.isBlank()) continue;

                Foreshadow f = new Foreshadow();
                f.setNovelId(novelId);
                f.setTitle(title);
                f.setContent((String) item.getOrDefault("description", ""));
                f.setKeywords((String) item.getOrDefault("keywords", ""));
                f.setPlantedChapter(chapterNumber);
                f.setStatus(ForeshadowStatus.PLANTED);
                f.setStableId(generateStableId(novelId + ":" + chapterNumber + ":" + title));
                int urgency = item.get("urgency") instanceof Number n
                        ? n.intValue() : 5;
                f.setUrgency(Math.min(10, Math.max(1, urgency)));
                int expectedAfter = item.get("expected_chapter") instanceof Number n
                        ? n.intValue() : 5;
                f.setExpectedResolveChapter(chapterNumber + expectedAfter);
                result.add(repo.save(f));
            }
            log.info("检测到 {} 个新伏笔", result.size());
            return result;

        } catch (Exception e) {
            log.error("伏笔检测失败: novel={}, chapter={}", novelId, chapterNumber, e);
            return List.of();
        }
    }

    /** 通过关键词匹配 + LLM 语义判断检查已解决的伏笔 */
    public List<Foreshadow> checkResolutions(String novelId, int chapterNumber, String content) {
        var planted = repo.findByNovelIdAndStatus(novelId, ForeshadowStatus.PLANTED);
        if (planted.isEmpty()) return List.of();

        // Step 1: 先用关键词快速筛选候选人
        List<Foreshadow> candidates = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        for (var f : planted) {
            if (f.getKeywords() == null) continue;
            for (String kw : f.getKeywords().split(",")) {
                if (lowerContent.contains(kw.trim().toLowerCase())) {
                    candidates.add(f);
                    break;
                }
            }
        }

        if (candidates.isEmpty()) return List.of();

        // Step 2: 用 LLM 语义判断是否真正回收（避免假阳性）
        List<Foreshadow> resolved = new ArrayList<>();
        String sample = content.length() > 3000 ? content.substring(0, 3000) : content;
        for (var f : candidates) {
            try {
                var response = chatModel.generate(
                        SystemMessage.from("""
                            判断以下伏笔是否在本章被真正回收（而非仅仅提及相关词汇）。
                            只回答 YES 或 NO。
                            """),
                        UserMessage.from(String.format("""
                            伏笔: %s (%s)
                            章节正文(前3000字): %s
                            """, f.getTitle(), f.getKeywords(), sample))
                );
                if ("YES".equalsIgnoreCase(response.content().text().trim())) {
                    f.setStatus(ForeshadowStatus.RESOLVED);
                    f.setResolvedChapter(chapterNumber);
                    resolved.add(f);
                }
            } catch (Exception e) {
                // LLM 调用失败时，回退到关键词匹配的结果
                log.warn("伏笔语义判断失败，使用关键词结果: {}", f.getTitle());
                f.setStatus(ForeshadowStatus.RESOLVED);
                f.setResolvedChapter(chapterNumber);
                resolved.add(f);
            }
        }

        if (!resolved.isEmpty()) {
            repo.saveAll(resolved);
            log.info("伏笔回收: {} 个 (关键词候选: {} 个)", resolved.size(), candidates.size());
        }
        return resolved;
    }

    /** 获取待回收的伏笔提醒 — 按紧急度三级 */
    public Map<String, List<Foreshadow>> getReminders(String novelId, int currentChapter) {
        var all = repo.findByNovelId(novelId);
        Map<String, List<Foreshadow>> reminders = new LinkedHashMap<>();
        reminders.put("must_resolve", new ArrayList<>());  // 紧急度 >=7
        reminders.put("overdue", new ArrayList<>());        // 已超预期章节
        reminders.put("upcoming", new ArrayList<>());        // 即将到期

        for (var f : all) {
            if (f.getStatus() == ForeshadowStatus.PLANTED
                    || f.getStatus() == ForeshadowStatus.PLANNED) {
                if (f.getUrgency() >= 7) {
                    reminders.get("must_resolve").add(f);
                } else if (f.getExpectedResolveChapter() != null
                        && f.getExpectedResolveChapter() < currentChapter) {
                    reminders.get("overdue").add(f);
                } else if (f.getExpectedResolveChapter() != null
                        && f.getExpectedResolveChapter() <= currentChapter + 3) {
                    reminders.get("upcoming").add(f);
                }
            }
        }
        return reminders;
    }

    /** 生成稳定 ID (MD5 → 32 位 hex，匹配数据库 stable_id 列定义) */
    public String generateStableId(String title) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(title.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 是 JVM 标准算法，不应失败；若失败则回退到 title 的 hashCode
            log.error("MD5 不可用，使用 hashCode 回退", e);
            return Integer.toHexString(title.hashCode());
        }
    }
}
