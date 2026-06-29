package com.yunmo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.domain.dto.ChapterCommit;
import com.yunmo.domain.entity.Character;
import com.yunmo.domain.entity.CharacterState;
import com.yunmo.domain.entity.EmotionalDebt;
import com.yunmo.domain.entity.ForeshadowTracking;
import com.yunmo.domain.entity.WorldRule;
import com.yunmo.domain.repository.CharacterRepository;
import com.yunmo.domain.repository.CharacterStateRepository;
import com.yunmo.domain.repository.EmotionalDebtRepository;
import com.yunmo.domain.repository.ForeshadowTrackingRepository;
import com.yunmo.domain.repository.WorldRuleRepository;
import com.yunmo.llm.provider.ChatModelFactory;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 章节提交服务 — 闭环回写机制核心
 *
 * 每章生成完成后执行回写链：
 *   章节内容保存 → 事实提取 → 状态变更计算 → 多路投影更新
 *
 * 参考 webnovel-writer 的 ChapterCommit 机制设计
 */
@Service
public class ChapterCommitService {

    private static final Logger log = LoggerFactory.getLogger(ChapterCommitService.class);

    private final CharacterStateRepository characterStateRepo;
    private final ForeshadowTrackingRepository foreshadowTrackingRepo;
    private final WorldRuleRepository worldRuleRepo;
    private final EmotionalDebtRepository emotionalDebtRepo;
    private final CharacterRepository characterRepo;
    private final ChatModelFactory modelFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChapterCommitService(CharacterStateRepository characterStateRepo,
                                 ForeshadowTrackingRepository foreshadowTrackingRepo,
                                 WorldRuleRepository worldRuleRepo,
                                 EmotionalDebtRepository emotionalDebtRepo,
                                 CharacterRepository characterRepo,
                                 ChatModelFactory modelFactory) {
        this.characterStateRepo = characterStateRepo;
        this.foreshadowTrackingRepo = foreshadowTrackingRepo;
        this.worldRuleRepo = worldRuleRepo;
        this.emotionalDebtRepo = emotionalDebtRepo;
        this.characterRepo = characterRepo;
        this.modelFactory = modelFactory;
    }

    /**
     * 构建章节提交数据包
     * 流程：提取事实 → 计算状态变更 → 检测伏笔 → 更新世界规则 → 更新情感债
     *
     * @param novelId        小说ID
     * @param chapterNumber  章节号
     * @param content        章节正文
     * @param metadata       生成元数据
     * @return ChapterCommit 提交数据包
     */
    public ChapterCommit buildCommit(String novelId, int chapterNumber,
                                      String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            log.warn("[闭环回写] 章节内容为空，跳过事实提取: chapter={}", chapterNumber);
            return new ChapterCommit(novelId, chapterNumber, content, metadata,
                List.of(), List.of(), List.of(), List.of(), "", 0, 0, 0, 0);
        }

        // 1. 获取角色列表
        List<Character> characters = characterRepo.findByNovelIdAndIsDeadFalse(novelId);

        // 2. 调LLM提取事实
        Map<String, Object> facts = extractFacts(content, characters);

        // 3. 计算角色状态变更
        List<CharacterState> characterStates = computeCharacterStateChanges(
            facts, characters, novelId, chapterNumber);

        // 4. 检测伏笔变化（新增/回收）
        List<ForeshadowTracking> foreshadowChanges = detectForeshadowChanges(
            facts, novelId, chapterNumber);

        // 5. 检测世界规则变更
        List<WorldRule> worldRuleChanges = detectWorldRuleChanges(
            facts, novelId, chapterNumber);

        // 6. 检测情感债变更
        List<EmotionalDebt> emotionalDebtChanges = detectEmotionalDebtChanges(
            facts, novelId, chapterNumber);

        // 7. 提取章节摘要
        String chapterSummary = extractChapterSummary(facts);

        return new ChapterCommit(
            novelId, chapterNumber, content, metadata,
            characterStates, foreshadowChanges, worldRuleChanges, emotionalDebtChanges,
            chapterSummary,
            characterStates.size(), foreshadowChanges.size(),
            worldRuleChanges.size(), emotionalDebtChanges.size()
        );
    }

    /**
     * 持久化提交数据 — 将所有变更写入数据库
     */
    @Transactional
    public void persistCommit(ChapterCommit commit) {
        if (commit == null) return;

        // 批量保存角色状态快照
        if (!commit.characterStates().isEmpty()) {
            characterStateRepo.saveAll(commit.characterStates());
            log.info("[闭环回写] 角色状态快照已持久化: count={}", commit.characterStateCount());
        }

        // 批量保存伏笔追踪
        if (!commit.foreshadowChanges().isEmpty()) {
            foreshadowTrackingRepo.saveAll(commit.foreshadowChanges());
            log.info("[闭环回写] 伏笔追踪已持久化: count={}", commit.hookCount());
        }

        // 批量保存世界规则
        if (!commit.worldRuleChanges().isEmpty()) {
            worldRuleRepo.saveAll(commit.worldRuleChanges());
            log.info("[闭环回写] 世界规则已持久化: count={}", commit.ruleCount());
        }

        // 批量保存情感债
        if (!commit.emotionalDebtChanges().isEmpty()) {
            emotionalDebtRepo.saveAll(commit.emotionalDebtChanges());
            log.info("[闭环回写] 情感债已持久化: count={}", commit.debtCount());
        }
    }

    /**
     * 多路投影更新 — 将提交数据投影到各子系统
     * 包括：角色状态投影、伏笔投影、世界规则投影、情感债投影、章节摘要投影
     */
    public void applyProjections(ChapterCommit commit) {
        if (commit == null) return;

        // ── 角色状态投影 ──
        applyCharacterStateProjection(commit);

        // ── 伏笔投影 ──
        applyForeshadowProjection(commit);

        // ── 世界规则投影 ──
        applyWorldRuleProjection(commit);

        // ── 情感债投影 ──
        applyEmotionalDebtProjection(commit);

        // ── 章节摘要投影 ──
        applyChapterSummaryProjection(commit);

        log.info("[闭环回写] 多路投影已完成: chapter={}, states={}, hooks={}, rules={}, debts={}",
            commit.chapterNumber(),
            commit.characterStateCount(), commit.hookCount(),
            commit.ruleCount(), commit.debtCount());
    }

    /**
     * 调LLM提取事实 — 从章节正文中提取结构化的关键事件和状态变化
     *
     * @param content    章节正文
     * @param characters 角色列表
     * @return 结构化事实Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractFacts(String content, List<Character> characters) {
        // 截取正文前3000字作为LLM输入（足够提取关键事实）
        String snippet = content.length() > 3000 ? content.substring(0, 3000) : content;

        // 构建角色名列表，帮助LLM识别
        String characterNames = characters.stream()
            .map(c -> c.getName())
            .collect(Collectors.joining("、"));

        String prompt = String.format("""
            你是一位小说编辑，请从以下小说章节中提取关键叙事事实。请仔细分析并输出JSON。

            ## 已知角色
            %s

            ## 章节正文（前3000字）
            %s

            ## 输出要求
            请严格按以下JSON格式输出，不要输出任何其他内容：

            ```json
            {
              "chapter_summary": "本章一句话摘要（50字以内）",
              "character_state_changes": [
                {
                  "characterName": "角色名",
                  "location": "当前所在位置（null表示未变化）",
                  "realm": "境界/等级变化（null表示未变化，如'炼气三层→炼气四层'）",
                  "emotionalState": "情绪状态（null表示无显著情绪，如'愤怒'、'悲伤'、'坚定'）",
                  "physicalState": "身体状况（null表示正常，如'重伤'、'中毒'、'濒死'）",
                  "relationshipChanges": {"角色名": "关系变化描述"},
                  "resources": {"物品名": "获得/失去/消耗"}
                }
              ],
              "foreshadows": [
                {
                  "hookId": "F编号（如F001，如果是新埋设的伏笔）",
                  "content": "伏笔内容",
                  "importance": "CORE/SUB/ORNAMENTAL",
                  "expectedPayoffChapter": "预计回收章节号推测（可为null）",
                  "action": "PLANTED/ACTIVATED/RESOLVED"
                }
              ],
              "world_rules": [
                {
                  "ruleName": "规则名",
                  "category": "POWER_SYSTEM/PHYSICS/SOCIETY/MAGIC/OTHER",
                  "description": "规则内容",
                  "action": "REVEALED/BROKEN/MODIFIED"
                }
              ],
              "emotional_debts": [
                {
                  "debtor": "债务人角色名",
                  "creditor": "债权人角色名",
                  "debtType": "PROMISE/GRUDGE/GRATITUDE/GUILT/LOVE",
                  "description": "债务内容",
                  "action": "CREATED/RESOLVED",
                  "expectedResolutionChapter": "预期解决章节（可为null）"
                }
              ]
            }
            ```

            注意：
            - 只提取本章确实发生的变化，没有变化的部分返回空数组
            - 角色名必须与已知角色列表中的名称精确匹配
            - 伏笔编号F001/F002等根据本章是第几章合理编号
            """, characterNames.isEmpty() ? "暂无已知角色" : characterNames, snippet);

        try {
            ChatLanguageModel model = modelFactory.getSyncModel("deepseek", "deepseek-v4-pro");

            Response<AiMessage> response = model.generate(UserMessage.from(prompt));

            String json = response.content().text();
            // 清理markdown代码块标记
            json = json.replaceAll("```json|```", "").trim();
            if (!json.startsWith("{")) {
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
            }

            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[闭环回写] 事实提取失败(非致命): {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 从LLM提取的事实中计算角色状态变更
     */
    @SuppressWarnings("unchecked")
    public List<CharacterState> computeCharacterStateChanges(Map<String, Object> facts,
                                                              List<Character> characters,
                                                              String novelId, int chapterNumber) {
        List<CharacterState> states = new ArrayList<>();
        List<Map<String, Object>> changes = safeExtractList(facts, "character_state_changes");

        // 构建角色名→角色ID映射
        Map<String, String> nameToId = characters.stream()
            .collect(Collectors.toMap(
                Character::getName,
                Character::getId,
                (a, b) -> a
            ));

        for (Map<String, Object> change : changes) {
            String characterName = (String) change.get("characterName");
            if (characterName == null || characterName.isBlank()) continue;

            String characterId = nameToId.get(characterName);
            if (characterId == null) {
                log.debug("[闭环回写] 角色'{}'不在已知角色列表中，跳过", characterName);
                continue;
            }

            CharacterState state = new CharacterState();
            state.setNovelId(novelId);
            state.setCharacterId(characterId);
            state.setChapterNumber(chapterNumber);
            state.setLocation(nullableString(change.get("location")));
            state.setRealm(nullableString(change.get("realm")));
            state.setEmotionalState(nullableString(change.get("emotionalState")));
            state.setPhysicalState(nullableString(change.get("physicalState")));

            // 序列化关系变化
            Object relChanges = change.get("relationshipChanges");
            if (relChanges instanceof Map) {
                try {
                    state.setRelationshipChanges(objectMapper.writeValueAsString(relChanges));
                } catch (Exception e) {
                    state.setRelationshipChanges("{}");
                }
            } else {
                state.setRelationshipChanges("{}");
            }

            // 序列化资源变化
            Object resChanges = change.get("resources");
            if (resChanges instanceof Map) {
                try {
                    state.setResources(objectMapper.writeValueAsString(resChanges));
                } catch (Exception e) {
                    state.setResources("{}");
                }
            } else {
                state.setResources("{}");
            }

            states.add(state);
        }

        return states;
    }

    /**
     * 从LLM提取的事实中检测伏笔变化（新增/回收）
     */
    @SuppressWarnings("unchecked")
    public List<ForeshadowTracking> detectForeshadowChanges(Map<String, Object> facts,
                                                             String novelId, int chapterNumber) {
        List<ForeshadowTracking> trackings = new ArrayList<>();
        List<Map<String, Object>> hooks = safeExtractList(facts, "foreshadows");

        for (Map<String, Object> hook : hooks) {
            String hookId = (String) hook.get("hookId");
            String hookContent = (String) hook.get("content");
            String importance = (String) hook.get("importance");
            String action = (String) hook.get("action");

            if (action == null) continue;

            ForeshadowTracking tracking;

            // 检查是否已存在该伏笔
            if (hookId != null) {
                var existing = foreshadowTrackingRepo.findByNovelIdAndHookId(novelId, hookId);
                tracking = existing.orElseGet(ForeshadowTracking::new);
            } else {
                tracking = new ForeshadowTracking();
                // 自动生成编号
                hookId = generateHookId(novelId);
            }

            tracking.setNovelId(novelId);
            tracking.setHookId(hookId);

            if (hookContent != null && !hookContent.isBlank()) {
                tracking.setContent(hookContent);
            }

            // 设置重要度
            if (importance != null) {
                try {
                    tracking.setImportance(ForeshadowTracking.HookImportance.valueOf(importance.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    tracking.setImportance(ForeshadowTracking.HookImportance.SUB);
                }
            }

            // 处理动作
            switch (action.toUpperCase()) {
                case "PLANTED" -> {
                    tracking.setPlantedChapter(chapterNumber);
                    tracking.setStatus(ForeshadowTracking.HookStatus.PLANTED);
                }
                case "ACTIVATED" -> {
                    tracking.setPlantedChapter(
                        tracking.getPlantedChapter() != null ? tracking.getPlantedChapter() : chapterNumber);
                    tracking.setStatus(ForeshadowTracking.HookStatus.ACTIVATED);
                }
                case "RESOLVED" -> {
                    tracking.setStatus(ForeshadowTracking.HookStatus.RESOLVED);
                    // 尝试解析预计回收章节
                    Object expected = hook.get("expectedPayoffChapter");
                    if (expected instanceof Number n) {
                        tracking.setExpectedPayoffChapter(n.intValue());
                    }
                }
            }

            trackings.add(tracking);
        }

        return trackings;
    }

    /**
     * 从LLM提取的事实中检测世界规则变更
     */
    @SuppressWarnings("unchecked")
    public List<WorldRule> detectWorldRuleChanges(Map<String, Object> facts,
                                                   String novelId, int chapterNumber) {
        List<WorldRule> rules = new ArrayList<>();
        List<Map<String, Object>> worldRules = safeExtractList(facts, "world_rules");

        for (Map<String, Object> wr : worldRules) {
            String ruleName = (String) wr.get("ruleName");
            String category = (String) wr.get("category");
            String description = (String) wr.get("description");
            String action = (String) wr.get("action");

            if (ruleName == null || ruleName.isBlank()) continue;

            // 检查是否已存在该规则
            WorldRule rule;
            var existing = worldRuleRepo.findByNovelIdAndRuleName(novelId, ruleName);
            rule = existing.orElseGet(WorldRule::new);

            rule.setNovelId(novelId);
            rule.setRuleName(ruleName);

            if (description != null && !description.isBlank()) {
                rule.setDescription(description);
            }

            // 设置分类
            if (category != null) {
                try {
                    rule.setCategory(WorldRule.RuleCategory.valueOf(category.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    rule.setCategory(WorldRule.RuleCategory.OTHER);
                }
            }

            // 处理动作
            if (action != null) {
                switch (action.toUpperCase()) {
                    case "REVEALED" -> {
                        if (rule.getRevealedChapter() == null) {
                            rule.setRevealedChapter(chapterNumber);
                        }
                        rule.setLastModifiedChapter(chapterNumber);
                        rule.setStatus(WorldRule.RuleStatus.ACTIVE);
                    }
                    case "BROKEN" -> {
                        rule.setLastModifiedChapter(chapterNumber);
                        rule.setStatus(WorldRule.RuleStatus.BROKEN);
                    }
                    case "MODIFIED" -> {
                        rule.setLastModifiedChapter(chapterNumber);
                        rule.setStatus(WorldRule.RuleStatus.MODIFIED);
                    }
                }
            }

            rules.add(rule);
        }

        return rules;
    }

    /**
     * 从LLM提取的事实中检测情感债变更
     */
    @SuppressWarnings("unchecked")
    public List<EmotionalDebt> detectEmotionalDebtChanges(Map<String, Object> facts,
                                                           String novelId, int chapterNumber) {
        List<EmotionalDebt> debts = new ArrayList<>();
        List<Map<String, Object>> emotionalDebts = safeExtractList(facts, "emotional_debts");

        for (Map<String, Object> ed : emotionalDebts) {
            String debtor = (String) ed.get("debtor");
            String creditor = (String) ed.get("creditor");
            String debtType = (String) ed.get("debtType");
            String description = (String) ed.get("description");
            String action = (String) ed.get("action");

            if (debtor == null || creditor == null) continue;

            EmotionalDebt debt = new EmotionalDebt();
            debt.setNovelId(novelId);
            debt.setDebtor(debtor);
            debt.setCreditor(creditor);

            if (description != null && !description.isBlank()) {
                debt.setDescription(description);
            }

            // 设置债务类型
            if (debtType != null) {
                try {
                    debt.setDebtType(EmotionalDebt.DebtType.valueOf(debtType.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    debt.setDebtType(EmotionalDebt.DebtType.PROMISE);
                }
            }

            // 处理动作
            if (action != null) {
                switch (action.toUpperCase()) {
                    case "CREATED" -> {
                        debt.setCreatedChapter(chapterNumber);
                        debt.setStatus(EmotionalDebt.DebtStatus.OUTSTANDING);
                        Object expected = ed.get("expectedResolutionChapter");
                        if (expected instanceof Number n) {
                            debt.setExpectedResolutionChapter(n.intValue());
                        }
                    }
                    case "RESOLVED" -> {
                        debt.setResolvedChapter(chapterNumber);
                        debt.setStatus(EmotionalDebt.DebtStatus.RESOLVED);
                    }
                }
            }

            debts.add(debt);
        }

        return debts;
    }

    // ===== 投影方法 =====

    /**
     * 角色状态投影 — 更新角色表的 currentState 字段
     */
    private void applyCharacterStateProjection(ChapterCommit commit) {
        for (CharacterState cs : commit.characterStates()) {
            try {
                characterRepo.findById(cs.getCharacterId()).ifPresent(character -> {
                    Map<String, Object> currentState = character.getCurrentState();
                    if (currentState == null) {
                        currentState = new HashMap<>();
                    }

                    if (cs.getLocation() != null) {
                        currentState.put("location", cs.getLocation());
                    }
                    if (cs.getRealm() != null) {
                        currentState.put("realm", cs.getRealm());
                    }
                    if (cs.getEmotionalState() != null) {
                        currentState.put("emotionalState", cs.getEmotionalState());
                    }
                    if (cs.getPhysicalState() != null) {
                        currentState.put("physicalState", cs.getPhysicalState());
                    }

                    character.setCurrentState(currentState);
                    character.setLastAppearanceChapter(commit.chapterNumber());
                    characterRepo.save(character);

                    log.debug("[投影] 角色状态已更新: name={}, chapter={}",
                        character.getName(), commit.chapterNumber());
                });
            } catch (Exception e) {
                log.warn("[投影] 角色状态更新失败: characterId={}, {}", cs.getCharacterId(), e.getMessage());
            }
        }
    }

    /**
     * 伏笔投影 — 检测即将过期的伏笔并告警
     */
    private void applyForeshadowProjection(ChapterCommit commit) {
        try {
            // 检查是否有超过预期回收章节仍未处理的伏笔
            var expired = foreshadowTrackingRepo.findByNovelIdAndStatusNotOrderByPlantedChapterAsc(
                commit.novelId(), ForeshadowTracking.HookStatus.RESOLVED);

            int currentChapter = commit.chapterNumber();
            for (ForeshadowTracking ft : expired) {
                if (ft.getStatus() != ForeshadowTracking.HookStatus.EXPIRED
                    && ft.getExpectedPayoffChapter() != null
                    && ft.getExpectedPayoffChapter() < currentChapter) {
                    ft.setStatus(ForeshadowTracking.HookStatus.EXPIRED);
                    foreshadowTrackingRepo.save(ft);
                    log.warn("[投影] 伏笔已过期: hookId={}, expectedChapter={}, currentChapter={}",
                        ft.getHookId(), ft.getExpectedPayoffChapter(), currentChapter);
                }
            }
        } catch (Exception e) {
            log.warn("[投影] 伏笔投影失败: {}", e.getMessage());
        }
    }

    /**
     * 世界规则投影 — 记录规则变更日志
     */
    private void applyWorldRuleProjection(ChapterCommit commit) {
        for (WorldRule wr : commit.worldRuleChanges()) {
            if (wr.getStatus() == WorldRule.RuleStatus.BROKEN) {
                log.info("[投影] 世界规则被打破: rule={}, chapter={}",
                    wr.getRuleName(), commit.chapterNumber());
            } else if (wr.getStatus() == WorldRule.RuleStatus.MODIFIED) {
                log.info("[投影] 世界规则被修改: rule={}, chapter={}",
                    wr.getRuleName(), commit.chapterNumber());
            }
        }
    }

    /**
     * 情感债投影 — 检查即将到期未偿还的情感债并告警
     */
    private void applyEmotionalDebtProjection(ChapterCommit commit) {
        try {
            var outstanding = emotionalDebtRepo.findByNovelIdAndStatusNotOrderByCreatedChapterAsc(
                commit.novelId(), EmotionalDebt.DebtStatus.RESOLVED);

            int currentChapter = commit.chapterNumber();
            for (EmotionalDebt debt : outstanding) {
                if (debt.getExpectedResolutionChapter() != null
                    && debt.getExpectedResolutionChapter() < currentChapter
                    && debt.getStatus() != EmotionalDebt.DebtStatus.FORGOTTEN) {
                    log.warn("[投影] 情感债已逾期: debtor={}, creditor={}, type={}, expectedChapter={}, currentChapter={}",
                        debt.getDebtor(), debt.getCreditor(), debt.getDebtType(),
                        debt.getExpectedResolutionChapter(), currentChapter);
                }
            }
        } catch (Exception e) {
            log.warn("[投影] 情感债投影失败: {}", e.getMessage());
        }
    }

    /**
     * 章节摘要投影 — 将摘要回写到 Chapter 表的 summary 字段
     */
    private void applyChapterSummaryProjection(ChapterCommit commit) {
        // 章节摘要的持久化由 ChapterGenerationService.saveChapter 中的
        // autoGenerateChapterPlan 处理（写入 writingPlan）。
        // 此处仅记录日志，避免与现有逻辑冲突。
        if (commit.chapterSummary() != null && !commit.chapterSummary().isBlank()) {
            log.debug("[投影] 章节摘要: chapter={}, summary={}",
                commit.chapterNumber(), commit.chapterSummary());
        }
    }

    // ===== 辅助方法 =====

    /**
     * 从事实Map中提取章节摘要
     */
    private String extractChapterSummary(Map<String, Object> facts) {
        Object summary = facts.get("chapter_summary");
        return summary instanceof String s ? s : "";
    }

    /**
     * 安全转换为String，null返回null
     */
    private String nullableString(Object value) {
        if (value == null || "null".equals(String.valueOf(value))) return null;
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }

    /**
     * 安全从facts Map中提取List，防止ClassCastException
     * LLM返回格式偏差时不会导致整个commit流程崩溃
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeExtractList(Map<String, Object> facts, String key) {
        try {
            Object value = facts.get(key);
            if (value == null) return List.of();
            if (value instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
            log.warn("[闭环回写] facts['{}'] 不是List类型(实际类型: {})，返回空列表", key,
                    value.getClass().getSimpleName());
            return List.of();
        } catch (Exception e) {
            log.warn("[闭环回写] 安全提取facts['{}']失败: {}，返回空列表", key, e.getMessage());
            return List.of();
        }
    }

    /**
     * 自动生成伏笔编号（F001, F002...）
     */
    private String generateHookId(String novelId) {
        var existing = foreshadowTrackingRepo.findByNovelId(novelId);
        int maxNum = existing.stream()
            .map(ForeshadowTracking::getHookId)
            .filter(id -> id != null && id.startsWith("F"))
            .mapToInt(id -> {
                try { return Integer.parseInt(id.substring(1)); }
                catch (NumberFormatException e) { return 0; }
            })
            .max()
            .orElse(0);
        return String.format("F%03d", maxNum + 1);
    }
}
