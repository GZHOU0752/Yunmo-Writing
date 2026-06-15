package com.yunmo.service;

import com.yunmo.agent.pipeline.*;
import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.Character;
import com.yunmo.domain.entity.OutlineNode;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.CharacterRepository;
import com.yunmo.service.outline.OutlineNodeService;
import com.yunmo.service.rag.ContextEnrichmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 章节生成协调服务 — 替代 Python generation.py
 * 组装初始状态 → 启动流水线引擎 → 返回结果
 */
@Service
public class ChapterGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ChapterGenerationService.class);

    private final ChapterPipelineEngine pipelineEngine;
    private final ContextAssemblyService contextAssembly;
    private final ChapterRepository chapterRepo;
    private final CharacterRepository characterRepo;
    private final ForeshadowService foreshadowService;
    private final OutlineNodeService outlineNodeService;
    private final WritingStatsService writingStatsService;
    private final ContextEnrichmentService contextEnrichment;
    private final AssembleContextStage assembleContextStage;
    private final PreFlightStage preflightStage;
    private final WriteChapterStage writeChapterStage;
    private final ReviewChapterStage reviewChapterStage;
    private final DecideVerdictRouter decideVerdictRouter;

    public ChapterGenerationService(ChapterPipelineEngine pipelineEngine,
                                     ContextAssemblyService contextAssembly,
                                     ChapterRepository chapterRepo,
                                     CharacterRepository characterRepo,
                                     ForeshadowService foreshadowService,
                                     OutlineNodeService outlineNodeService,
                                     WritingStatsService writingStatsService,
                                     ContextEnrichmentService contextEnrichment,
                                     AssembleContextStage assembleContextStage,
                                     PreFlightStage preflightStage,
                                     WriteChapterStage writeChapterStage,
                                     ReviewChapterStage reviewChapterStage,
                                     DecideVerdictRouter decideVerdictRouter) {
        this.pipelineEngine = pipelineEngine;
        this.contextAssembly = contextAssembly;
        this.chapterRepo = chapterRepo;
        this.characterRepo = characterRepo;
        this.foreshadowService = foreshadowService;
        this.outlineNodeService = outlineNodeService;
        this.writingStatsService = writingStatsService;
        this.contextEnrichment = contextEnrichment;
        this.assembleContextStage = assembleContextStage;
        this.preflightStage = preflightStage;
        this.writeChapterStage = writeChapterStage;
        this.reviewChapterStage = reviewChapterStage;
        this.decideVerdictRouter = decideVerdictRouter;
    }

    /**
     * 构建流水线定义
     */
    public ChapterPipelineDefinition buildPipeline() {
        return ChapterPipelineDefinition.builder()
                .entryPoint("assemble_context")
                .stage("assemble_context", assembleContextStage)
                .edge("assemble_context", "preflight")
                .stage("preflight", preflightStage)
                .edge("preflight", "write_chapter")
                .stage("write_chapter", writeChapterStage)
                .edge("write_chapter", "review_chapter")
                .stage("review_chapter", reviewChapterStage)
                .conditionalEdge("review_chapter", decideVerdictRouter, Map.of(
                        "pass", ChapterPipelineDefinition.END,
                        "rewrite", "write_chapter",
                        "regenerate", "write_chapter"
                ))
                .build();
    }

    /**
     * 构建初始状态 — 若章节不存在则自动创建
     */
    public Mono<PipelineState> buildInitialState(String novelId, int chapterNumber,
                                                   Map<String, Object> genreConfig,
                                                   String userFocus
    ) {
        return Mono.fromCallable(() -> {
            // 若章节不存在，自动创建
            var chapter = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                    .orElseGet(() -> {
                        Chapter newChapter = new Chapter();
                        newChapter.setNovelId(novelId);
                        newChapter.setChapterNumber(chapterNumber);
                        newChapter.setTitle("第" + chapterNumber + "章");
                        newChapter.setTargetWordCount(2000);
                        newChapter.setStatus(com.yunmo.common.enums.ChapterStatus.OUTLINE);
                        log.info("自动创建章节: novel={}, chapter={}", novelId, chapterNumber);
                        return chapterRepo.save(newChapter);
                    });
            var characters = characterRepo.findByNovelIdAndIsDeadFalse(novelId);

            // 组装上下文
            String contextText = contextAssembly.assemble(novelId, chapterNumber,
                    genreConfig, characters);

            // 构建大纲文本（注入大纲树的章/节信息）
            String chapterPlan = buildChapterPlan(novelId, chapterNumber, chapter);

            // RAG 检索参考素材（异步）
            String ragContext = "";
            try {
                String ragQuery = contextText.length() > 2000
                        ? contextText.substring(0, 2000) : contextText;
                ragContext = contextEnrichment.retrieve(novelId, ragQuery);
            } catch (Exception e) {
                log.debug("RAG 检索跳过（无素材或检索失败）: {}", e.getMessage());
            }
            PipelineState state = new PipelineState();
            state.put("novel_id", novelId);
            state.put("chapter_number", chapterNumber);
            state.put("genre_config", genreConfig);
            state.put("context_text", contextText);
            state.put("chapter_plan", chapterPlan);
            state.put("user_focus", userFocus != null ? userFocus : "");
            state.put("target_word_count", chapter.getTargetWordCount());
            state.put("character_profiles", buildCharacterProfiles(characters));
            state.put("retry_count", 0);
            state.put("rag_context", ragContext);  // RAG 参考素材

            // 缓存 Chapter 对象避免 saveChapter 重复查询
            state.put("_chapter_entity", chapter);

            // 写入虚拟文件系统
            state.putFile("context_text.txt", contextText);
            state.putFile("chapter_plan.txt", chapterPlan);
            if (!ragContext.isEmpty()) state.putFile("rag_reference.txt", ragContext);

            return state;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 非流式生成 — 直接返回最终内容
     */
    public Mono<Map<String, Object>> generate(String novelId, int chapterNumber,
                                                Map<String, Object> genreConfig,
                                                String userFocus
    ) {
        return buildInitialState(novelId, chapterNumber, genreConfig, userFocus)
                .flatMap(initialState -> Mono.fromCallable(() -> {
                    var definition = buildPipeline();
                    var finalState = pipelineEngine.execute(definition, initialState);
                    Map<String, Object> result = new HashMap<>();
                    result.put("content", finalState.get("chapter_content", String.class));
                    result.put("word_count", finalState.get("chapter_word_count", Integer.class));
                    result.put("verdict", finalState.get("verdict", String.class));
                    result.put("inspector_report", finalState.get("inspector_report", String.class));
                    result.put("guardian_report", finalState.get("guardian_report", String.class));
                    return result;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 流式生成 — Flux<StageEvent> 供 Controller 转 SSE
     */
    public Flux<StageEvent> generateStream(String novelId, int chapterNumber,
                                             Map<String, Object> genreConfig,
                                             String userFocus
    ) {
        return buildInitialState(novelId, chapterNumber, genreConfig, userFocus)
                .flatMapMany(initialState -> {
                    var definition = buildPipeline();
                    return pipelineEngine.executeStream(definition, initialState)
                            .concatWith(
                                Mono.<StageEvent>fromRunnable(() -> saveChapter(initialState))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .doOnError(e -> log.error("[GenerateStream] 章节保存失败: novel={}, chapter={}",
                                            novelId, chapterNumber, e))
                                    .onErrorResume(e -> Mono.just(
                                        new StageEvent("save_chapter", "error",
                                            StageOutput.of("error", "章节保存失败: " + e.getMessage()))))
                            );
                });
    }

    /**
     * 保存章节到数据库（使用 buildInitialState 中已加载的 Chapter 对象避免重复查询）
     */
    private void saveChapter(PipelineState state) {
        String novelId = state.get("novel_id", String.class);
        int chapterNumber = state.get("chapter_number", Integer.class);
        String content = state.get("chapter_content", String.class);
        int wordCount = state.getOrDefault("chapter_word_count", Integer.class, 0);

        if (content == null || content.isEmpty()) {
            log.warn("[GenerateStream] 章节内容为空，跳过保存: novel={}, chapter={}", novelId, chapterNumber);
            return;
        }

        // 优先使用 buildInitialState 中缓存的 Chapter，避免重复 DB 查询
        Chapter cachedChapter = state.get("_chapter_entity", Chapter.class);
        if (cachedChapter != null) {
            cachedChapter.setContent(content);
            cachedChapter.setWordCount(wordCount);
            cachedChapter.setStatus(com.yunmo.common.enums.ChapterStatus.GENERATED);
            chapterRepo.save(cachedChapter);
            log.info("章节已保存(缓存): novel={}, chapter={}, words={}", novelId, chapterNumber, wordCount);
            // 伏笔检测
            detectAndResolveForeshadows(novelId, chapterNumber, content);
            // 码字统计
            try { writingStatsService.recordWriting(novelId, wordCount); } catch (Exception ignored) {}
            return;
        }

        chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber)
                .ifPresentOrElse(chapter -> {
                    chapter.setContent(content);
                    chapter.setWordCount(wordCount);
                    chapter.setStatus(com.yunmo.common.enums.ChapterStatus.GENERATED);
                    chapterRepo.save(chapter);
                    log.info("章节已保存: novel={}, chapter={}, words={}", novelId, chapterNumber, wordCount);
                    // 伏笔检测
                    detectAndResolveForeshadows(novelId, chapterNumber, content);
                    // 码字统计
                    try { writingStatsService.recordWriting(novelId, wordCount); } catch (Exception ignored) {}
                }, () -> log.error("章节不存在，无法保存: novel={}, chapter={}", novelId, chapterNumber));
    }

    /** 异步执行伏笔检测和回收 */
    private void detectAndResolveForeshadows(String novelId, int chapterNumber, String content) {
        try {
            foreshadowService.detectNew(novelId, chapterNumber, content);
            foreshadowService.checkResolutions(novelId, chapterNumber, content);
        } catch (Exception e) {
            log.error("伏笔检测异常: novel={}, chapter={}", novelId, chapterNumber, e);
        }
    }

    /** 构建章节的大纲注释，优先从大纲树读取，回退到 writingPlan */
    private String buildChapterPlan(String novelId, int chapterNumber, Chapter chapter) {
        // 有手动写作计划则优先
        if (chapter.getWritingPlan() != null && !chapter.getWritingPlan().isBlank()) {
            return chapter.getWritingPlan();
        }

        // 从大纲树中获取与该章节关联的节点
        var outlineNodes = outlineNodeService.getTree(novelId);
        var matchedNodes = outlineNodes.stream()
                .filter(n -> n.getChapterNumber() != null && n.getChapterNumber() == chapterNumber)
                .sorted(Comparator.comparing(OutlineNode::getLevel).thenComparing(OutlineNode::getSequenceOrder))
                .toList();

        if (matchedNodes.isEmpty()) {
            return String.format("第 %d 章 - %s", chapterNumber,
                    chapter.getTitle() != null ? chapter.getTitle() : "");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("第 %d 章 - %s\n", chapterNumber,
                chapter.getTitle() != null ? chapter.getTitle() : ""));

        // 找到该章节对应的大纲节点（level=2 的章节点）及其下属节节点
        OutlineNode chapterNode = matchedNodes.stream()
                .filter(n -> n.getLevel() == 2).findFirst().orElse(null);
        if (chapterNode != null) {
            sb.append("【章纲】").append(chapterNode.getTitle());
            if (chapterNode.getOutlineContent() != null) {
                sb.append(" — ").append(chapterNode.getOutlineContent());
            }
            if (chapterNode.getCausalSentence() != null) {
                sb.append("\n因果：").append(chapterNode.getCausalSentence());
            }
            sb.append("\n");
        }

        // 列出节节点（level=3）
        var sectionNodes = matchedNodes.stream()
                .filter(n -> n.getLevel() == 3)
                .toList();
        if (!sectionNodes.isEmpty()) {
            sb.append("【节纲】\n");
            for (var sec : sectionNodes) {
                sb.append("  - ").append(sec.getTitle());
                if (sec.getOutlineContent() != null) {
                    sb.append("：").append(sec.getOutlineContent());
                }
                sb.append("\n");
            }
        }

        // 查找上级（卷/总纲）信息
        if (chapterNode != null && chapterNode.getParentId() != null) {
            outlineNodes.stream()
                    .filter(n -> n.getId().equals(chapterNode.getParentId()))
                    .findFirst()
                    .ifPresent(volume -> {
                        sb.insert(0, String.format("【卷】%s\n", volume.getTitle()));
                        if (volume.getParentId() != null) {
                            outlineNodes.stream()
                                    .filter(n -> n.getId().equals(volume.getParentId()))
                                    .findFirst()
                                    .ifPresent(root ->
                                            sb.insert(0, String.format("【总纲】%s\n", root.getTitle())));
                        }
                    });
        }

        return sb.toString().trim();
    }

    private List<Map<String, String>> buildCharacterProfiles(List<Character> characters) {
        return characters.stream()
                .map(c -> {
                    Map<String, String> profile = new HashMap<>();
                    profile.put("name", c.getName());
                    profile.put("role", c.getRole().name());
                    profile.put("description", c.getDescription() != null ? c.getDescription() : "");
                    profile.put("state", c.getCurrentState() != null
                            ? c.getCurrentState().toString() : "");
                    return profile;
                })
                .toList();
    }
}
