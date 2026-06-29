package com.yunmo.api.controller;

import com.yunmo.domain.entity.Character;
import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.CharacterState;
import com.yunmo.domain.entity.EmotionalDebt;
import com.yunmo.domain.repository.CharacterRepository;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.CharacterStateRepository;
import com.yunmo.domain.repository.EmotionalDebtRepository;
import com.yunmo.llm.provider.ChatModelFactory;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/characters")
public class CharacterController {

    private static final Logger log = LoggerFactory.getLogger(CharacterController.class);

    private final CharacterRepository characterRepo;
    private final ChapterRepository chapterRepo;
    private final CharacterStateRepository characterStateRepo;
    private final EmotionalDebtRepository emotionalDebtRepo;
    private final ChatModelFactory modelFactory;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public CharacterController(CharacterRepository characterRepo,
                               ChapterRepository chapterRepo,
                               CharacterStateRepository characterStateRepo,
                               EmotionalDebtRepository emotionalDebtRepo,
                               ChatModelFactory modelFactory) {
        this.characterRepo = characterRepo;
        this.chapterRepo = chapterRepo;
        this.characterStateRepo = characterStateRepo;
        this.emotionalDebtRepo = emotionalDebtRepo;
        this.modelFactory = modelFactory;
    }

    @GetMapping
    public Mono<List<Character>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() ->
                characterRepo.findByNovelIdOrderByImportanceDesc(novelId)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Character>> get(@PathVariable String novelId, @PathVariable String id) {
        return Mono.fromCallable(() ->
                characterRepo.findById(id)
                        .filter(c -> c.getNovelId().equals(novelId))
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<Character> create(@PathVariable String novelId, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            Character c = new Character();
            c.setNovelId(novelId);
            c.setName((String) body.get("name"));
            c.setRole(com.yunmo.common.enums.CharacterRole.valueOf(
                    ((String) body.getOrDefault("role", "SUPPORTING")).toUpperCase()));
            c.setDescription((String) body.getOrDefault("description", ""));
            c.setImportance(body.containsKey("importance")
                    ? ((Number) body.get("importance")).intValue() : 5);
            return characterRepo.save(c);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Character>> update(
            @PathVariable String novelId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() ->
                characterRepo.findById(id)
                        .filter(c -> c.getNovelId().equals(novelId))
                        .map(c -> {
                            if (body.containsKey("name")) c.setName((String) body.get("name"));
                            if (body.containsKey("description")) c.setDescription((String) body.get("description"));
                            if (body.containsKey("importance"))
                                c.setImportance(((Number) body.get("importance")).intValue());
                            if (body.containsKey("layer1_worldview"))
                                c.setLayer1Worldview((String) body.get("layer1_worldview"));
                            if (body.containsKey("layer2_identity"))
                                c.setLayer2Identity((String) body.get("layer2_identity"));
                            if (body.containsKey("layer3_values"))
                                c.setLayer3Values((String) body.get("layer3_values"));
                            if (body.containsKey("layer4_abilities"))
                                c.setLayer4Abilities((String) body.get("layer4_abilities"));
                            if (body.containsKey("layer5_skills"))
                                c.setLayer5Skills((String) body.get("layer5_skills"));
                            if (body.containsKey("layer6_environment"))
                                c.setLayer6Environment((String) body.get("layer6_environment"));
                            if (body.containsKey("current_state"))
                                c.setCurrentState((Map) body.get("current_state"));
                            return ResponseEntity.ok(characterRepo.save(c));
                        })
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String novelId, @PathVariable String id) {
        return Mono.fromCallable(() -> {
            var c = characterRepo.findById(id);
            if (c.isPresent() && c.get().getNovelId().equals(novelId)) {
                characterRepo.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }
            return ResponseEntity.notFound().<Void>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * AI深度角色分析——扫描该角色在所有已写章节中的出场片段，
     * 自动总结性格特征、语言习惯、关系变化、未开发的潜力
     */
    @PostMapping("/{id}/analyze")
    public Mono<Map<String, Object>> analyzeCharacter(@PathVariable String novelId,
                                                       @PathVariable String id) {
        return Mono.fromCallable(() -> {
            Character character = characterRepo.findById(id)
                .filter(c -> c.getNovelId().equals(novelId))
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

            ChatLanguageModel model = modelFactory.getSyncModel("deepseek", "deepseek-v4-pro");

            // 收集该角色在所有章节中的出场上下文
            var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
            StringBuilder samples = new StringBuilder();
            java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile(
                "(?<!\\p{IsHan})" + java.util.regex.Pattern.quote(character.getName()) + "(?!\\p{IsHan})");
            for (Chapter ch : chapters) {
                String content = ch.getContent() != null ? ch.getContent() : "";
                String clean = content.replaceAll("<[^>]+>", "");
                java.util.regex.Matcher nameMatcher = namePattern.matcher(clean);
                if (nameMatcher.find()) {
                    int idx = nameMatcher.start();
                    int start = Math.max(0, idx - 100);
                    int end = Math.min(clean.length(), idx + 200);
                    String snippet = clean.substring(start, end).trim();
                    samples.append(String.format("第%d章：...%s...\n\n",
                        ch.getChapterNumber(), snippet));
                }
            }

            if (samples.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("status", "ok");
                empty.put("message", "该角色尚未在正文中出场");
                return empty;
            }

            String prompt = String.format("""
                你是一个资深网文角色设计师。请基于以下出场片段，对角色「%s」进行深度分析。

                ## 角色基本信息
                姓名：%s
                角色类型：%s
                当前描述：%s

                ## 出场片段（按章节顺序）
                %s

                请分析并输出JSON（不要其他文字）：

                ```json
                {
                  "personality": "性格特征总结（2-3个关键词+40字描述）",
                  "speaking_style": "语言习惯（口头禅、句式特点、敬语方式）",
                  "relationship_changes": "关系变化轨迹（从出场到最新，关键转折点）",
                  "growth_arc": "角色成长弧（从出场到最新，能力/心态变化）",
                  "untapped_potential": "未开发的潜力（剧作角度，这个角色还可以怎么写）",
                  "suggested_description": "建议更新后的角色描述（80-120字，涵盖外貌+性格+身份+核心特质）"
                }
                ```
                """, character.getName(), character.getName(),
                character.getRole().getDescription(),
                character.getDescription() != null ? character.getDescription() : "无",
                samples.toString());

            Response<AiMessage> response = model.generate(UserMessage.from(prompt));

            String json = com.yunmo.common.util.JsonExtractor.extractJson(response.content().text());
            if (json == null || json.isBlank()) json = "{}";

            @SuppressWarnings("unchecked")
            Map<String, Object> analysis;
            try {
                analysis = objectMapper.readValue(json, Map.class);
            } catch (Exception e) {
                log.warn("角色分析 JSON 解析失败: {}", e.getMessage());
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("status", "error");
                error.put("message", "AI 返回格式异常，请重试");
                return error;
            }

            // AI 建议的描述不直接覆盖用户手动编辑的描述，前端可选择应用
            // 将性格特征写入 layer3_values（价值观/性格层）
            String personality = (String) analysis.get("personality");
            if (personality != null && !personality.isBlank()) {
                character.setLayer3Values(personality);
            }
            characterRepo.save(character);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("character_name", character.getName());
            result.put("updated_description", character.getDescription());
            result.put("analysis", analysis);
            log.info("角色分析完成: {} (novel={})", character.getName(), novelId);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 聚合角色关系数据。
     * 从 CharacterState.relationshipChanges 和 EmotionalDebt 两张表聚合，
     * 返回角色间关系图（关系变化 + 情感债）。
     */
    @GetMapping("/relationships")
    public Mono<Map<String, Object>> relationships(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new LinkedHashMap<>();

            // 1. 从 CharacterState 聚合关系变化
            var characters = characterRepo.findByNovelIdOrderByImportanceDesc(novelId);
            List<Map<String, Object>> relationalChanges = new ArrayList<>();
            for (Character c : characters) {
                var states = characterStateRepo.findByNovelIdAndCharacterIdOrderByChapterNumberAsc(novelId, c.getId());
                for (CharacterState state : states) {
                    if (state.getRelationshipChanges() != null && !state.getRelationshipChanges().isBlank()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("characterName", c.getName());
                        entry.put("characterId", c.getId());
                        entry.put("chapterNumber", state.getChapterNumber());
                        try {
                            entry.put("changes", objectMapper.readValue(state.getRelationshipChanges(), Map.class));
                        } catch (Exception e) {
                            entry.put("changes", state.getRelationshipChanges());
                        }
                        relationalChanges.add(entry);
                    }
                }
            }
            result.put("relationshipChanges", relationalChanges);

            // 2. 从 EmotionalDebt 聚合情感债
            var debts = emotionalDebtRepo.findByNovelId(novelId);
            List<Map<String, Object>> debtList = new ArrayList<>();
            for (EmotionalDebt d : debts) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", d.getId());
                entry.put("debtor", d.getDebtor());
                entry.put("creditor", d.getCreditor());
                entry.put("debtType", d.getDebtType().name());
                entry.put("description", d.getDescription());
                entry.put("createdChapter", d.getCreatedChapter());
                entry.put("expectedResolutionChapter", d.getExpectedResolutionChapter());
                entry.put("resolvedChapter", d.getResolvedChapter());
                entry.put("status", d.getStatus().name());
                debtList.add(entry);
            }
            result.put("emotionalDebts", debtList);

            // 3. 构建角色关系图（邻接表）
            Map<String, Set<String>> graph = new LinkedHashMap<>();
            for (EmotionalDebt d : debts) {
                graph.computeIfAbsent(d.getDebtor(), k -> new LinkedHashSet<>()).add(d.getCreditor());
                graph.computeIfAbsent(d.getCreditor(), k -> new LinkedHashSet<>()).add(d.getDebtor());
            }
            List<Map<String, Object>> edges = new ArrayList<>();
            Set<String> seenEdges = new HashSet<>();
            for (var entry : graph.entrySet()) {
                for (String target : entry.getValue()) {
                    String edgeKey = entry.getKey().compareTo(target) < 0
                        ? entry.getKey() + "|||" + target
                        : target + "|||" + entry.getKey();
                    if (seenEdges.add(edgeKey)) {
                        Map<String, Object> edge = new LinkedHashMap<>();
                        edge.put("source", entry.getKey());
                        edge.put("target", target);
                        edges.add(edge);
                    }
                }
            }
            result.put("graph", edges);

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

}
