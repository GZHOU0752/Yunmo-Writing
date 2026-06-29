package com.yunmo.service;

import com.yunmo.agent.hook.HookSelection;
import com.yunmo.agent.hook.HookSystem;
import com.yunmo.agent.pipeline.*;
import com.yunmo.common.config.PipelineProperties;
import com.yunmo.domain.dto.ChapterCommit;
import com.yunmo.domain.dto.FulfillmentResult;
import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.Character;
import com.yunmo.domain.entity.Novel;
import com.yunmo.domain.entity.OutlineNode;
import com.yunmo.domain.entity.StoryContract;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.CharacterRepository;
import com.yunmo.domain.repository.NovelRepository;
import com.yunmo.service.outline.OutlineNodeService;
import com.yunmo.service.rag.ContextEnrichmentService;
import com.yunmo.service.rag.ReferenceMaterialService;
import com.yunmo.service.style.StyleRouter;
import com.yunmo.service.style.StyleSelection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.domain.entity.ReferenceMaterial;
import com.yunmo.llm.provider.ChatModelFactory;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
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
    private final NovelRepository novelRepo;
    private final ForeshadowService foreshadowService;
    private final OutlineNodeService outlineNodeService;
    private final WritingStatsService writingStatsService;
    private final ContextEnrichmentService contextEnrichment;
    private final AssembleContextStage assembleContextStage;
    private final PreFlightStage preflightStage;
    private final WriteChapterStage writeChapterStage;
    private final DecideVerdictRouter decideVerdictRouter;
    private final PleasureBeatStage pleasureBeatStage;
    private final PolishChapterStage polishChapterStage;
    private final AdversarialEditStage adversarialEditStage;
    private final DebateOutlineStage debateOutlineStage;
    private final IncrementalMemoryService memoryService;
    private final ReferenceMaterialService referenceMaterialService;
    private final WritingGuideService writingGuideService;
    private final WriterPersonaService writerPersonaService;
    private final GenrePackService genrePackService;
    private final StyleRouter styleRouter;
    private final EntityLifecycleService entityLifecycleService;
    private final CheckpointService checkpointService;
    private final ChatModelFactory modelFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // P3-3: 插件注册中心（可选注入，用于配置化管线）
    private final PipelinePluginRegistry pluginRegistry;
    private final PipelineProperties pipelineProperties;
    private final ChapterCommitService chapterCommitService;
    // P1-7: 钩子系统
    private final HookSystem hookSystem;
    /** 故事合同服务 — 三层合同架构（MASTER/VOLUME/CHAPTER） */
    private final StoryContractService storyContractService;
    /** 合同履约检查服务 — 验证章节节点覆盖 + 禁区扫描 */
    private final ContractFulfillmentService contractFulfillmentService;

    public ChapterGenerationService(ChapterPipelineEngine pipelineEngine,
                                     ContextAssemblyService contextAssembly,
                                     ChapterRepository chapterRepo,
                                     CharacterRepository characterRepo,
                                     NovelRepository novelRepo,
                                     ForeshadowService foreshadowService,
                                     OutlineNodeService outlineNodeService,
                                     WritingStatsService writingStatsService,
                                     ContextEnrichmentService contextEnrichment,
                                     AssembleContextStage assembleContextStage,
                                     PreFlightStage preflightStage,
                                     WriteChapterStage writeChapterStage,
                                     DecideVerdictRouter decideVerdictRouter,
                                     PleasureBeatStage pleasureBeatStage,
                                     PolishChapterStage polishChapterStage,
                                     AdversarialEditStage adversarialEditStage,
                                     DebateOutlineStage debateOutlineStage,
                                     IncrementalMemoryService memoryService,
                                     ReferenceMaterialService referenceMaterialService,
                                     WritingGuideService writingGuideService,
                                     WriterPersonaService writerPersonaService,
                                     GenrePackService genrePackService,
                                     StyleRouter styleRouter,
                                     EntityLifecycleService entityLifecycleService,
                                     CheckpointService checkpointService,
                                     ChatModelFactory modelFactory,
                                     PipelinePluginRegistry pluginRegistry,
                                     PipelineProperties pipelineProperties,
                                     ChapterCommitService chapterCommitService,
                                     HookSystem hookSystem,
                                     StoryContractService storyContractService,
                                     ContractFulfillmentService contractFulfillmentService) {
        this.pipelineEngine = pipelineEngine;
        this.contextAssembly = contextAssembly;
        this.chapterRepo = chapterRepo;
        this.characterRepo = characterRepo;
        this.novelRepo = novelRepo;
        this.foreshadowService = foreshadowService;
        this.outlineNodeService = outlineNodeService;
        this.writingStatsService = writingStatsService;
        this.contextEnrichment = contextEnrichment;
        this.assembleContextStage = assembleContextStage;
        this.preflightStage = preflightStage;
        this.writeChapterStage = writeChapterStage;
        this.decideVerdictRouter = decideVerdictRouter;
        this.pleasureBeatStage = pleasureBeatStage;
        this.polishChapterStage = polishChapterStage;
        this.adversarialEditStage = adversarialEditStage;
        this.debateOutlineStage = debateOutlineStage;
        this.memoryService = memoryService;
        this.referenceMaterialService = referenceMaterialService;
        this.writingGuideService = writingGuideService;
        this.writerPersonaService = writerPersonaService;
        this.genrePackService = genrePackService;
        this.styleRouter = styleRouter;
        this.entityLifecycleService = entityLifecycleService;
        this.checkpointService = checkpointService;
        this.modelFactory = modelFactory;
        this.pluginRegistry = pluginRegistry;
        this.pipelineProperties = pipelineProperties;
        this.chapterCommitService = chapterCommitService;
        this.hookSystem = hookSystem;
        this.storyContractService = storyContractService;
        this.contractFulfillmentService = contractFulfillmentService;
    }

    /**
     * 构建流水线定义 — 含并行阶段以缩减总延迟。
     *
     * 管线拓扑:
     *   assemble_context → debate_outline
     *     → [preflight ∥ pleasure_beat]           ← 并行组（无数据依赖，独立分析章纲）
     *     → write_chapter → polish_chapter → adversarial_edit
     *     → router: pass→END | rewrite→polish | regenerate→write
     *
     * 若 yml 配置了自定义阶段列表（与默认不同），则回退到纯顺序管线。
     */
    public ChapterPipelineDefinition buildPipeline() {
        // 检查是否有自定义阶段配置（与默认不同）
        List<String> configuredStages = pipelineProperties.getStages();
        Set<String> disabledStages = pipelineProperties.getDisabled();
        boolean hasCustomConfig = (configuredStages != null && !configuredStages.isEmpty()
            && !configuredStages.equals(List.of("assemble_context", "debate_outline", "preflight",
                "pleasure_beat", "write_chapter", "polish_chapter", "adversarial_edit")))
            || (disabledStages != null && !disabledStages.isEmpty());

        if (hasCustomConfig && pluginRegistry != null && !pluginRegistry.all().isEmpty()) {
            // 自定义管线：纯顺序执行（不做并行假设）
            List<PipelinePlugin> plugins = pluginRegistry.ordered(
                configuredStages.isEmpty() ? null : configuredStages,
                disabledStages
            );
            if (!plugins.isEmpty()) {
                var builder = ChapterPipelineDefinition.builder();
                builder.entryPoint(plugins.get(0).name());
                for (int i = 0; i < plugins.size(); i++) {
                    builder.stage(plugins.get(i).name(), plugins.get(i));
                    if (i < plugins.size() - 1) {
                        builder.edge(plugins.get(i).name(), plugins.get(i + 1).name());
                    }
                }
                String lastStage = plugins.get(plugins.size() - 1).name();
                if ("adversarial_edit".equals(lastStage)) {
                    builder.conditionalEdge("adversarial_edit", decideVerdictRouter, Map.of(
                        "pass", ChapterPipelineDefinition.END,
                        "rewrite", "polish_chapter",
                        "regenerate", "write_chapter"
                    ));
                }
                return builder.build();
            }
        }

        // 默认管线：preflight ∥ pleasure_beat 并行，其余顺序
        return ChapterPipelineDefinition.builder()
                .entryPoint("assemble_context")
                .stage("assemble_context", assembleContextStage)
                .edge("assemble_context", "debate_outline")
                .stage("debate_outline", debateOutlineStage)
                // 并行组: preflight ∥ pleasure_beat
                //   两者都只读取 chapter_plan + context_text，无数据依赖
                //   并行执行可节省 Architect + Guardian + PleasureBeat 三者的 LLM 调用延迟
                .stage("preflight", preflightStage)
                .stage("pleasure_beat", pleasureBeatStage)
                .parallelEdge("debate_outline", List.of("preflight", "pleasure_beat"))
                .edge("debate_outline", "write_chapter")
                .stage("write_chapter", writeChapterStage)
                .edge("write_chapter", "polish_chapter")
                .stage("polish_chapter", polishChapterStage)
                .edge("polish_chapter", "adversarial_edit")
                .stage("adversarial_edit", adversarialEditStage)
                .conditionalEdge("adversarial_edit", decideVerdictRouter, Map.of(
                        "pass", ChapterPipelineDefinition.END,
                        "rewrite", "polish_chapter",
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
                        newChapter.setTargetWordCount(2500);
                        newChapter.setStatus(com.yunmo.common.enums.ChapterStatus.OUTLINE);
                        log.info("自动创建章节: novel={}, chapter={}", novelId, chapterNumber);
                        return chapterRepo.save(newChapter);
                    });
            // 防御：genreConfig 为 null 时使用空 Map（Marathon 调用等场景）
            final Map<String, Object> effectiveGenreConfig = genreConfig != null
                    ? genreConfig : Collections.emptyMap();

            var characters = characterRepo.findByNovelIdAndIsDeadFalse(novelId);

            // 加载小说基本信息 — 书名+简介+全书大纲，作为 AI 写作的最低限度上下文
            Novel novel = novelRepo.findById(novelId).orElse(null);
            String novelTitle = novel != null ? novel.getTitle() : "";
            String novelSynopsis = novel != null && novel.getSynopsis() != null ? novel.getSynopsis() : "";
            String novelOutline = novel != null && novel.getOutline() != null ? novel.getOutline() : "";

            // 组装上下文
            String contextText = contextAssembly.assemble(novelId, chapterNumber,
                    effectiveGenreConfig, characters);

            // 构建大纲文本（注入大纲树的章/节信息）
            String chapterPlan = buildChapterPlan(novelId, chapterNumber, chapter);

            // RAG 检索参考素材 — 用章纲+作者指示作为查询，比用前文更精准
            String ragContext = "";
            try {
                String ragQuery = buildRagQuery(chapterPlan, userFocus);
                if (!ragQuery.isEmpty()) {
                    ragContext = contextEnrichment.retrieve(novelId, ragQuery);
                }
            } catch (Exception e) {
                log.warn("RAG 检索跳过（无素材或检索失败）: {}", e.getMessage());
            }

            // 关键词触发素材（智能触发）
            String triggeredContext = "";
            try {
                List<ReferenceMaterial> activeMaterials = referenceMaterialService
                    .getActiveMaterials(novelId, chapterNumber, chapterPlan + " " + (userFocus != null ? userFocus : ""));
                if (!activeMaterials.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("## 已激活的参考素材（触发词匹配）\n\n");
                    for (int i = 0; i < activeMaterials.size(); i++) {
                        ReferenceMaterial m = activeMaterials.get(i);
                        sb.append(String.format("【%s】（触发模式：%s）\n",
                            m.getFileName(), m.getTriggerMode()));
                        // 通过VectorStore检索该素材最相关的片段
                        try {
                            String chunk = contextEnrichment.retrieve(novelId,
                                chapterPlan != null ? chapterPlan : m.getFileName(), 2, 0.5);
                            if (!chunk.isEmpty()) sb.append(chunk).append("\n");
                        } catch (Exception ignored) {}
                    }
                    triggeredContext = sb.toString();
                    log.info("[SmartTrigger] 激活 {} 个素材: chapter={}",
                        activeMaterials.size(), chapterNumber);
                }
            } catch (Exception e) {
                log.warn("智能触发检索跳过: {}", e.getMessage());
            }

            PipelineState state = new PipelineState();
            state.put("novel_id", novelId);
            state.put("chapter_number", chapterNumber);
            state.put("genre_config", effectiveGenreConfig);
            state.put("genre_id", novel != null ? novel.getGenreId() : "");
            state.put("context_text", contextText);
            state.put("chapter_plan", chapterPlan);
            state.put("user_focus", userFocus != null ? userFocus : "");
            // 小说基本上下文 — 书名+简介+大纲，即使作者不填 userFocus 也能让 AI 知道在写什么
            state.put("novel_title", novelTitle);
            state.put("novel_synopsis", novelSynopsis);
            state.put("novel_outline", novelOutline);
            state.put("target_word_count", chapter.getTargetWordCount());
            state.put("target_word_min", 2300);
            state.put("target_word_max", 2799);
            // 全书目标总字数（大纲规划时为150万字约束）
            state.put("target_total_words", novel != null && novel.getTargetTotalWords() != null
                    ? novel.getTargetTotalWords() : 1_500_000);
            state.put("character_profiles", buildCharacterProfiles(characters));
            state.put("retry_count", 0);
            // 合并 RAG 检索 + 触发素材
            String combinedRag = (ragContext.isEmpty() ? "" : ragContext + "\n")
                + (triggeredContext.isEmpty() ? "" : triggeredContext);
            state.put("rag_context", combinedRag.trim());  // RAG 参考素材 + 触发素材

            // 写作指南（默认4本 + 关键词匹配）
            String writingGuide = writingGuideService.getMatchedGuidesContent(chapterPlan);
            if (!writingGuide.isEmpty()) {
                state.put("writing_guide", writingGuide);
                log.info("写作指南已加载: {}", writingGuideService.matchGuides(chapterPlan));
            }

            // Writer persona — 根据小说类型自动选择写手人设
            String writerPersona = novel != null
                ? writerPersonaService.getPersona(novel.getGenreId()) : "";
            if (!writerPersona.isEmpty()) {
                state.put("writer_persona", writerPersona);
                log.info("Writer persona 已激活: genre={}, length={}", novel.getGenreId(), writerPersona.length());
            }

            // 体裁规则包 — 核心承诺/禁用模式/节奏建议/追读法则
            String genreContext = novel != null
                ? genrePackService.buildGenreContext(novel.getGenreId()) : "";
            if (!genreContext.isEmpty()) {
                state.put("genre_context", genreContext);
                log.info("体裁规则包已注入: genre={}", novel != null ? novel.getGenreId() : "null");
            }

            // P1-6: 风格调度 — 根据体裁+章纲+用户焦点自动选择风格模块
            StyleSelection styleSelection = null;
            try {
                String genreId = novel != null ? novel.getGenreId() : "";
                styleSelection = styleRouter.selectStyles(genreId, chapterPlan, userFocus);
                // 风格上下文文本
                state.put("style_context", styleSelection.styleContext());
                // 风格人设 — 体裁人设的补充
                String stylePersona = writerPersonaService.getStylePersona(styleSelection.primary());
                if (styleSelection.hasSecondary()) {
                    stylePersona += "\n" + writerPersonaService.getStylePersona(styleSelection.secondary());
                }
                state.put("style_persona", stylePersona);
                // 融合的文体规则上下文（体裁硬约束 + 风格软指导）
                String styleGenreContext = genrePackService.buildStyleGenreContext(genreId, styleSelection);
                state.put("style_genre_context", styleGenreContext);
                // 原始选择结果（供管线阶段引用）
                state.put("style_selection", styleSelection);
                log.info("风格调度完成: primary={}, secondary={}, auxiliary={}",
                        styleSelection.primary().getChineseName(),
                        styleSelection.hasSecondary() ? styleSelection.secondary().getChineseName() : "无",
                        styleSelection.hasAuxiliary() ? styleSelection.auxiliary().getChineseName() : "无");
            } catch (Exception e) {
                log.warn("风格调度失败，回退到纯体裁规则: {}", e.getMessage());
                // 失败时回退：仅使用体裁默认风格
                if (novel != null && novel.getGenreId() != null) {
                    try {
                        styleSelection = styleRouter.selectByGenre(novel.getGenreId());
                        state.put("style_context", styleSelection.styleContext());
                        String stylePersona = writerPersonaService.getStylePersona(styleSelection.primary());
                        state.put("style_persona", stylePersona);
                        String styleGenreContext = genrePackService.buildStyleGenreContext(
                                novel.getGenreId(), styleSelection);
                        state.put("style_genre_context", styleGenreContext);
                        state.put("style_selection", styleSelection);
                    } catch (Exception e2) {
                        log.warn("风格回退也失败: {}", e2.getMessage());
                    }
                }
            }

            // 实体生命周期 — 终结实体警告 + 活跃/冷却/冷藏角色分层
            List<String> entityWarnings = entityLifecycleService.getTerminatedEntityWarnings(novelId, chapterNumber);
            String entitySummary = entityLifecycleService.getEntitySummary(novelId, chapterNumber);
            if (!entityWarnings.isEmpty()) {
                state.put("entity_warnings", entityWarnings);
            }
            if (!entitySummary.isEmpty()) {
                state.put("entity_summary", entitySummary);
            }

            // P2: 增量记忆（近期剧情追踪 + 世界状态）
            String incrementalMemory = memoryService.getMemorySummary(novelId, chapterNumber);
            if (!incrementalMemory.isEmpty()) {
                state.put("incremental_memory", incrementalMemory);
            }

            // P0-5: 连续性预检 — 生成前检查世界状态，有警告则注入Writer prompt
            List<String> continuityWarnings = memoryService.getContinuityWarnings(novelId, chapterNumber);
            if (!continuityWarnings.isEmpty()) {
                state.put("continuity_warnings", continuityWarnings);
                log.info("[PreFlight] 连续性预检发现 {} 条警告: chapter={}", continuityWarnings.size(), chapterNumber);
            }

            // P1-7: 悬念钩子选择 — 根据章纲+章节位置自动选择章首引子和章尾钩子
            try {
                String genreId = novel != null ? novel.getGenreId() : "";
                int totalChapters = novel != null && novel.getTotalChapters() != null
                        ? novel.getTotalChapters() : 100;
                HookSelection hookSelection = hookSystem.selectChapterHooks(
                        chapterPlan, genreId, chapterNumber, totalChapters, novelId);
                state.put("hook_selection", hookSelection);
                log.info("[HookSystem] 钩子选择完成: chapter={}, 章首={}, 章尾={}, 强度={}",
                        chapterNumber,
                        hookSelection.openingHook().chineseName(),
                        hookSelection.closingHook().chineseName(),
                        hookSelection.suspenseIntensity());

                // 将跨章悬念弧上下文追加到 context_text
                if (hookSelection.arcContext() != null && !hookSelection.arcContext().isEmpty()) {
                    String enhancedContext = contextText + "\n\n【跨章悬念弧上下文】\n"
                            + hookSelection.arcContext();
                    state.put("context_text", enhancedContext);
                }
            } catch (Exception e) {
                log.warn("[HookSystem] 钩子选择失败，跳过钩子注入: {}", e.getMessage());
            }

            // 构建章节控制卡（本章规划）— 从已有 PipelineState 提取，零额外 LLM 调用
            try {
                HookSelection hs = state.get("hook_selection", HookSelection.class);
                @SuppressWarnings("unchecked")
                List<Map<String, String>> profiles =
                    (List<Map<String, String>>) (Object) state.get("character_profiles", List.class);
                Map<String, Object> controlCard = buildChapterControlCard(
                        chapterPlan, hs, profiles, genreConfig);
                state.put("chapter_control_card", controlCard);
                log.info("[ControlCard] 章节控制卡已构建: chapter={}", chapterNumber);
            } catch (Exception e) {
                log.warn("[ControlCard] 控制卡构建失败: {}", e.getMessage());
            }

            // P1: 故事合同 — 加载章级合同并注入管线状态
            // 合同约束追加到 context_text 中，让 Writer 阶段获取结构化写作要求
            try {
                // 尝试获取已存在的活跃章级合同，若不存在则自动生成
                StoryContract chapterContract = storyContractService.getActiveContract(
                        novelId, "CHAPTER", chapterNumber);
                if (chapterContract == null) {
                    // 确保 MASTER 合同存在
                    StoryContract masterContract = storyContractService.getActiveContract(
                            novelId, "MASTER", null);
                    if (masterContract == null && novel != null && novel.getGenreId() != null) {
                        storyContractService.createMasterContract(
                                novelId, novel.getGenreId(), new String[0]);
                        log.info("[合同] MASTER 合同已自动创建: novel={}, genre={}",
                                novelId, novel.getGenreId());
                    }
                    // 生成章级合同
                    chapterContract = storyContractService.createChapterContract(novelId, chapterNumber);
                    log.info("[合同] CHAPTER 合同已自动创建: novel={}, chapter={}",
                            novelId, chapterNumber);
                }

                // 将合同对象注入 PipelineState，供管线各阶段引用
                state.put("chapter_contract", chapterContract);

                // 将合同约束转化为可读上下文，追加到 context_text
                String contractContext = storyContractService.buildContractContext(chapterContract);
                if (!contractContext.isEmpty()) {
                    String enhancedContext = state.get("context_text", String.class)
                            + "\n\n" + contractContext;
                    state.put("context_text", enhancedContext);
                    log.info("[合同] 合同上下文已注入: contract={}, contextLength={}",
                            chapterContract.getId(), contractContext.length());
                }
            } catch (Exception e) {
                log.warn("[合同] 合同加载/生成失败，跳过合同注入: {}", e.getMessage());
            }

            // 缓存 Chapter 对象避免 saveChapter 重复查询
            state.put("_chapter_entity", chapter);

            // 写入虚拟文件系统
            state.putFile("context_text.txt", contextText);
            state.putFile("chapter_plan.txt", chapterPlan);

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
                    result.put("adversarial_score", finalState.get("adversarial_score", Integer.class));
                    result.put("anti_ai_report", finalState.get("anti_ai_report", Object.class));
                    result.put("guard_results", finalState.get("guard_results", Object.class));
                    result.put("fix_guidance", finalState.get("fix_guidance", Object.class));
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

                    // 检查断点
                    CheckpointService.Checkpoint checkpoint = checkpointService.load(novelId, chapterNumber);
                    Flux<StageEvent> checkpointEvent = checkpoint != null
                        ? Flux.just(new StageEvent("checkpoint_found", "preflight",
                            StageOutput.of("checkpoint",
                                Map.of("novelId", novelId, "chapterNumber", chapterNumber,
                                    "lastStage", checkpoint.lastStage(),
                                    "savedAt", checkpoint.savedAt().toString()))))
                        : Flux.empty();

                    // 管线事件 + 每阶段保存断点
                    var stages = definition.stages();
                    int totalStages = stages.size();
                    int[] idx = {0};

                    return checkpointEvent.concatWith(
                        pipelineEngine.executeStream(definition, initialState)
                            .doOnNext(event -> {
                                if (!"token".equals(event.phase())) {
                                    checkpointService.save(novelId, chapterNumber,
                                        event.stageName(), idx[0]++, totalStages,
                                        initialState.getOrDefault("chapter_content", String.class, ""));
                                }
                            })
                    ).concatWith(
                                Mono.<StageEvent>fromRunnable(() -> {
                                    saveChapter(initialState);
                                    checkpointService.clear(novelId, chapterNumber);
                                })
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

            // 将钩子编排和控制卡 JSON 写入 transient 字段（随 GET 接口返回前端）
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                Object hookSel = state.get("hook_selection", Object.class);
                if (hookSel != null) {
                    cachedChapter.setHookSelectionJson(om.writeValueAsString(hookSel));
                }
                Object ctrlCard = state.get("chapter_control_card", Object.class);
                if (ctrlCard != null) {
                    cachedChapter.setChapterControlCardJson(om.writeValueAsString(ctrlCard));
                }
            } catch (Exception e) {
                log.warn("[SaveChapter] 摘要数据写入失败: {}", e.getMessage());
            }

            chapterRepo.save(cachedChapter);
            log.info("章节已保存(缓存): novel={}, chapter={}, words={}", novelId, chapterNumber, wordCount);
            // 伏笔检测
            detectAndResolveForeshadows(novelId, chapterNumber, content);
            // 增量记忆 + 连续性校验
            updateMemoryAndVerify(novelId, chapterNumber, cachedChapter.getTitle(), content, wordCount);
            // 码字统计
            try { writingStatsService.recordWriting(novelId, wordCount); } catch (Exception ignored) {}
            // P0-3: 闭环回写
            runChapterCommit(novelId, chapterNumber, content);
            // P1-7: 推进悬念弧进度
            try {
                hookSystem.advanceArcs(novelId, chapterNumber, false, true);
            } catch (Exception e) {
                log.warn("[HookSystem] 悬念弧推进失败: {}", e.getMessage());
            }
            // P1: 合同履约检查 — 验证章节是否覆盖了合同要求的结构化节点
            runContractFulfillmentCheck(state, content);
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
                    // 增量记忆 + 连续性校验
                    updateMemoryAndVerify(novelId, chapterNumber, chapter.getTitle(), content, wordCount);
                    // 码字统计
                    try { writingStatsService.recordWriting(novelId, wordCount); } catch (Exception ignored) {}
                    // P0-3: 闭环回写
                    runChapterCommit(novelId, chapterNumber, content);
                    // P1-7: 推进悬念弧进度
                    try {
                        hookSystem.advanceArcs(novelId, chapterNumber, false, true);
                    } catch (Exception e) {
                        log.warn("[HookSystem] 悬念弧推进失败: {}", e.getMessage());
                    }
                    // P1: 合同履约检查 — 验证章节是否覆盖了合同要求的结构化节点
                    runContractFulfillmentCheck(state, content);
                }, () -> log.error("章节不存在，无法保存: novel={}, chapter={}", novelId, chapterNumber));
    }

    /**
     * P0-3: 闭环回写 — 异步执行，失败不阻断章节保存
     */
    private void runChapterCommit(String novelId, int chapterNumber, String content) {
        try {
            Map<String, Object> metadata = Map.of(
                "generation_model", "deepseek-v4-pro",
                "pipeline_stages", "chapter_generation_pipeline",
                "trigger", "save_chapter"
            );
            ChapterCommit commit = chapterCommitService.buildCommit(novelId, chapterNumber, content, metadata);
            chapterCommitService.persistCommit(commit);
            chapterCommitService.applyProjections(commit);
            log.info("[闭环回写] 状态已同步: chapter={}, states={}, hooks={}, rules={}, debts={}",
                chapterNumber, commit.characterStateCount(), commit.hookCount(),
                commit.ruleCount(), commit.debtCount());
        } catch (Exception e) {
            log.error("[闭环回写] 回写失败(非致命): chapter={}", chapterNumber, e);
        }
    }

    /**
     * P1: 合同履约检查 — 检查章节是否覆盖了合同要求的结构化节点
     * 非致命操作，失败不阻断章节保存
     */
    private void runContractFulfillmentCheck(PipelineState state, String content) {
        try {
            StoryContract contract = state.get("chapter_contract", StoryContract.class);
            if (contract == null) {
                log.debug("[合同履约] 无活跃合同，跳过履约检查");
                return;
            }

            FulfillmentResult result = contractFulfillmentService.checkFulfillment(contract, content);
            log.info("[合同履约] 检查完成: contract={}, score={}, passed={}, " +
                    "planned={}, covered={}, missed={}, violations={}",
                    result.contractId(), String.format("%.1f", result.fulfillmentScore()), result.passed(),
                    result.plannedNodes(), result.coveredNodes(),
                    result.missedNodes(), result.forbiddenViolations().size());

            if (!result.missedDescriptions().isEmpty()) {
                log.warn("[合同履约] 遗漏节点: contract={}, missed={}",
                        result.contractId(), result.missedDescriptions());
            }
            if (!result.forbiddenViolations().isEmpty()) {
                log.warn("[合同履约] 禁区违规: contract={}, violations={}",
                        result.contractId(), result.forbiddenViolations());
            }
            if (!result.passed()) {
                log.warn("[合同履约] 履约未通过: contract={}, score={}",
                        result.contractId(), String.format("%.1f", result.fulfillmentScore()));
            }
        } catch (Exception e) {
            log.error("[合同履约] 履约检查异常(非致命): {}", e.getMessage());
        }
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

    /** 每章保存后更新增量记忆 + 连续性校验 + 自动生成章纲 */
    private void updateMemoryAndVerify(String novelId, int chapterNumber,
                                        String title, String content, int wordCount) {
        try {
            // 更新增量记忆
            memoryService.updateMemory(novelId, chapterNumber,
                title != null ? title : "第" + chapterNumber + "章", content, wordCount);
            // 确定性连续性校验
            List<String> violations = memoryService.verifyContinuity(novelId, chapterNumber, content);
            if (!violations.isEmpty()) {
                log.warn("[Continuity] 连续性校验发现 {} 个问题: chapter={}, violations={}",
                        violations.size(), chapterNumber, violations);
            }
            // 提取新角色（不再自动覆盖 writingPlan，避免重复生成时偏离大纲）
            autoExtractNewCharacters(novelId, chapterNumber, content);
        } catch (Exception e) {
            log.warn("[Memory] 记忆更新/连续性校验失败: {}", e.getMessage());
        }
    }

    /** 每章生成后自动生成章纲摘要，写入 Chapter.writingPlan */
    private void autoGenerateChapterPlan(String novelId, int chapterNumber,
                                          String title, String content) {
        try {
            var ch = chapterRepo.findFirstByNovelIdAndChapterNumber(novelId, chapterNumber).orElse(null);
            if (ch == null) return;
            // 如果作者已经手动写了章纲，不覆盖
            if (ch.getWritingPlan() != null && !ch.getWritingPlan().isBlank()) return;

            // 从正文提取摘要作为章纲
            String clean = content.replaceAll("<[^>]+>", "")
                .replaceAll("&#\\d+;", "").replaceAll("\\s+", "").trim();
            String plan = clean.length() > 150
                ? clean.substring(0, 150) + "…" : clean;
            ch.setWritingPlan(plan);
            chapterRepo.save(ch);
            log.info("[Auto] 章纲已自动生成: chapter={}", chapterNumber);
        } catch (Exception e) {
            log.warn("[Auto] 章纲生成失败: {}", e.getMessage());
        }
    }

    /**
     * 从新生成的章节中提取新角色并自动创建角色卡。
     * 同时更新已有角色的 lastAppearanceChapter（基于章节内容中的人名匹配）。
     */
    private void autoExtractNewCharacters(String novelId, int chapterNumber, String content) {
        ChatLanguageModel model = modelFactory.getSyncModel("deepseek", "deepseek-v4-pro");
        if (content == null || content.length() < 500) {
            // 即使没有 LLM，也更新已有角色出场章节
            updateExistingCharacterAppearances(novelId, chapterNumber, content);
            return;
        }

        try {
            String prompt = String.format("""
                从以下小说章节中提取新出场的角色。只提取本章首次出场、之前未出现过的角色。
                对于每个角色，给出：姓名、角色类型(PROTAGONIST/ANTAGONIST/SUPPORTING/MINOR)、简短描述(外貌+性格+身份)、重要度(1-10)。

                如果本章没有新角色出场，回复空的JSON数组。

                ## 章节内容（前1500字）
                %s

                请只输出JSON数组，格式如下：
                [{"name":"角色名","role":"SUPPORTING","description":"描述","importance":5}]
                """, content.length() > 1500 ? content.substring(0, 1500) : content);

            Response<AiMessage> response = model.generate(UserMessage.from(prompt));

            // 解析JSON
            String json = response.content().text().replaceAll("```json|```", "").trim();
            if (!json.startsWith("[")) {
                int start = json.indexOf('[');
                int end = json.lastIndexOf(']');
                if (start >= 0 && end > start) json = json.substring(start, end + 1);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newChars = objectMapper.readValue(json, List.class);

            if (!newChars.isEmpty()) {
                int added = 0;
                var existingNames = characterRepo.findByNovelIdAndIsDeadFalse(novelId).stream()
                    .map(c -> c.getName()).collect(Collectors.toSet());

                for (Map<String, Object> nc : newChars) {
                    String name = (String) nc.get("name");
                    if (name == null || name.isBlank() || existingNames.contains(name)) continue;

                    com.yunmo.domain.entity.Character character = new com.yunmo.domain.entity.Character();
                    character.setNovelId(novelId);
                    character.setName(name);
                    character.setDescription((String) nc.getOrDefault("description", ""));
                    try {
                        character.setRole(com.yunmo.common.enums.CharacterRole.valueOf(
                            ((String) nc.getOrDefault("role", "SUPPORTING")).toUpperCase()));
                    } catch (Exception ex) {
                        character.setRole(com.yunmo.common.enums.CharacterRole.SUPPORTING);
                    }
                    Object imp = nc.get("importance");
                    character.setImportance(imp instanceof Number n ? n.intValue() : 5);
                    character.setLastAppearanceChapter(chapterNumber);

                    characterRepo.save(character);
                    existingNames.add(name);
                    added++;
                }

                if (added > 0) {
                    log.info("[Auto] 自动提取 {} 个新角色: chapter={}", added, chapterNumber);
                }
            }
        } catch (Exception e) {
            log.warn("[Auto] 角色LLM提取失败: {}", e.getMessage());
        }

        // 无论 LLM 提取成功与否，基于内容匹配更新已有角色出场信息
        updateExistingCharacterAppearances(novelId, chapterNumber, content);
    }

    /** 基于章节内容中的人名匹配，更新已有角色的 lastAppearanceChapter */
    private void updateExistingCharacterAppearances(String novelId, int chapterNumber, String content) {
        try {
            var chars = characterRepo.findByNovelIdAndIsDeadFalse(novelId);
            String cleanContent = content.replaceAll("<[^>]+>", "");
            int updated = 0;
            for (var c : chars) {
                if (c.getName() == null || c.getName().length() < 2) continue;
                if (cleanContent.contains(c.getName())) {
                    if (c.getLastAppearanceChapter() == null || c.getLastAppearanceChapter() < chapterNumber) {
                        c.setLastAppearanceChapter(chapterNumber);
                        characterRepo.save(c);
                        updated++;
                    }
                }
            }
            if (updated > 0) {
                log.info("[Auto] 更新 {} 个已有角色出场信息: chapter={}", updated, chapterNumber);
            }
        } catch (Exception e) {
            log.warn("[Auto] 角色出场更新失败: {}", e.getMessage());
        }
    }

    /** 构建 RAG 检索查询：章纲 + 作者指示，比用前文更精准定位需要的参考素材 */
    private String buildRagQuery(String chapterPlan, String userFocus) {
        StringBuilder q = new StringBuilder();
        if (chapterPlan != null && !chapterPlan.isBlank()) {
            // 取章纲的关键部分，过滤掉纯结构标记
            String cleaned = chapterPlan
                    .replaceAll("【.*?】", " ")  // 去掉【卷】【章纲】等标记
                    .replaceAll("\\s+", " ")
                    .trim();
            if (cleaned.length() > 500) {
                cleaned = cleaned.substring(0, 500);
            }
            q.append(cleaned);
        }
        if (userFocus != null && !userFocus.isBlank()) {
            if (q.length() > 0) q.append(" ");
            q.append(userFocus);
        }
        return q.toString().trim();
    }

    /**
     * 构建章节的大纲注释。
     * 优先级：大纲树节点 > 作者手动 writingPlan > 默认标题。
     * 不再自动从生成内容截取 writingPlan（避免重复生成时偏离大纲）。
     */
    private String buildChapterPlan(String novelId, int chapterNumber, Chapter chapter) {
        // 1. 先从大纲树中获取与该章节关联的节点（优先保证与大纲对齐）
        var outlineNodes = outlineNodeService.getTree(novelId);
        var matchedNodes = outlineNodes.stream()
                .filter(n -> n.getChapterNumber() != null && n.getChapterNumber() == chapterNumber)
                .sorted(Comparator.comparing(OutlineNode::getLevel).thenComparing(OutlineNode::getSequenceOrder))
                .toList();

        if (matchedNodes.isEmpty()) {
            // 2. 大纲树无匹配 → fallback 到作者手动写的 writingPlan
            if (chapter.getWritingPlan() != null && !chapter.getWritingPlan().isBlank()) {
                return chapter.getWritingPlan();
            }
            // 3. 都没有 → 默认标题
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

    /**
     * 从已有数据构建章节控制卡（本章规划），零额外 LLM 调用。
     * 数据来源：hook系统选择结果 + 角色档案 + 类型禁词 + 章纲文本片段。
     */
    private Map<String, Object> buildChapterControlCard(
            String chapterPlan,
            HookSelection hookSelection,
            List<Map<String, String>> characterProfiles,
            Map<String, Object> genreConfig
    ) {
        Map<String, Object> card = new LinkedHashMap<>();

        // 角色列表（取前5位角色名）
        if (characterProfiles != null && !characterProfiles.isEmpty()) {
            List<String> names = characterProfiles.stream()
                    .map(p -> p.get("name"))
                    .filter(n -> n != null && !n.isEmpty())
                    .limit(5)
                    .toList();
            card.put("characters", names);
        } else {
            card.put("characters", List.of());
        }

        // 钩子信息
        if (hookSelection != null) {
            card.put("openingHook", hookSelection.openingHook().chineseName()
                    + "（" + hookSelection.openingHook().formulaNumber() + "式）");
            card.put("closingHook", hookSelection.closingHook().chineseName()
                    + "（" + hookSelection.closingHook().formulaNumber() + "式）");
            card.put("hookOps", "章首" + hookSelection.openingHook().chineseName()
                    + " → 章尾" + hookSelection.closingHook().chineseName());
            card.put("suspenseIntensity", hookSelection.suspenseIntensity());
        }

        // 类型禁词
        if (genreConfig != null) {
            @SuppressWarnings("unchecked")
            List<String> forbiddenTerms = (List<String>) genreConfig.get("forbidden_terms");
            if (forbiddenTerms != null && !forbiddenTerms.isEmpty()) {
                // 取前10个禁词作为提示
                card.put("forbiddenZones", forbiddenTerms.stream()
                        .limit(10).toList());
            }
        }

        // 章纲摘要（取前两行作为本章使命/核心冲突）
        if (chapterPlan != null && !chapterPlan.isEmpty()) {
            String[] lines = chapterPlan.split("\n");
            List<String> briefLines = new java.util.ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                briefLines.add(trimmed.length() > 60 ? trimmed.substring(0, 60) + "..." : trimmed);
                if (briefLines.size() >= 2) break;
            }
            if (!briefLines.isEmpty()) {
                card.put("mission", briefLines.get(0));
            }
            if (briefLines.size() >= 2) {
                card.put("coreConflict", briefLines.get(1));
            }
        }

        log.debug("[ControlCard] 构建完成: keys={}", card.keySet());
        return card;
    }
}
