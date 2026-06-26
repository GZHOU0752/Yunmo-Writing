package com.yunmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.common.config.AppProperties;
import com.yunmo.common.dto.LLMConfig;
import com.yunmo.common.dto.LLMMessage;
import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.Novel;
import com.yunmo.domain.entity.ReferenceMaterial;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.NovelRepository;
import com.yunmo.domain.repository.ReferenceMaterialRepository;
import com.yunmo.llm.provider.ProviderRegistry;
import com.yunmo.service.NovelCascadeService;
import com.yunmo.service.style.StyleAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 小说 CRUD API
 */
@RestController
@RequestMapping("/api/v1/novels")
public class NovelController {

    private static final Logger log = LoggerFactory.getLogger(NovelController.class);

    private final NovelRepository novelRepo;
    private final ChapterRepository chapterRepo;
    private final AppProperties appProperties;
    private final NovelCascadeService cascadeService;
    private final ReferenceMaterialRepository refRepo;
    private final ProviderRegistry providerRegistry;
    private final StyleAnalysisService styleAnalysisService;
    private final ObjectMapper mapper = new ObjectMapper();

    public NovelController(NovelRepository novelRepo, ChapterRepository chapterRepo,
                           AppProperties appProperties, NovelCascadeService cascadeService,
                           ReferenceMaterialRepository refRepo,
                           ProviderRegistry providerRegistry,
                           StyleAnalysisService styleAnalysisService) {
        this.novelRepo = novelRepo;
        this.chapterRepo = chapterRepo;
        this.appProperties = appProperties;
        this.cascadeService = cascadeService;
        this.refRepo = refRepo;
        this.providerRegistry = providerRegistry;
        this.styleAnalysisService = styleAnalysisService;
    }

    @GetMapping
    public Mono<List<Novel>> list() {
        return Mono.fromCallable(() -> {
            var novels = novelRepo.findByUserIdOrderByCreatedAtDesc(appProperties.getDefaultUserId());
            // 填充从章节计算的实际字数和章数
            for (var novel : novels) {
                var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novel.getId());
                novel.setTotalChapters(chapters.size());
                novel.setWordCount(chapters.stream().mapToInt(c ->
                    c.getWordCount() != null ? c.getWordCount() : 0).sum());
            }
            return novels;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Novel>> get(@PathVariable String id) {
        return Mono.fromCallable(() ->
                novelRepo.findById(id)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<Novel> create(@RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            Novel novel = new Novel();
            novel.setTitle((String) body.get("title"));
            novel.setGenreId((String) body.getOrDefault("genre_id", "xuanhuan"));
            novel.setSynopsis((String) body.getOrDefault("synopsis", ""));
            novel.setWritingStyle((String) body.getOrDefault("writing_style", ""));
            if (body.containsKey("target_total_words")) {
                novel.setTargetTotalWords(((Number) body.get("target_total_words")).intValue());
            }
            novel.setUserId(appProperties.getDefaultUserId());
            return novelRepo.save(novel);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Novel>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() ->
                novelRepo.findById(id)
                        .map(novel -> {
                            if (body.containsKey("title")) novel.setTitle((String) body.get("title"));
                            if (body.containsKey("synopsis")) novel.setSynopsis((String) body.get("synopsis"));
                            if (body.containsKey("writing_style")) novel.setWritingStyle((String) body.get("writing_style"));
                            if (body.containsKey("outline")) novel.setOutline((String) body.get("outline"));
                            if (body.containsKey("genre_id")) novel.setGenreId((String) body.get("genre_id"));
                            if (body.containsKey("target_total_words")) {
                                novel.setTargetTotalWords(((Number) body.get("target_total_words")).intValue());
                            }
                            return ResponseEntity.ok(novelRepo.save(novel));
                        })
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<?>> delete(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            boolean deleted = cascadeService.cascadeDelete(id);
            if (deleted) {
                return ResponseEntity.ok(Map.<String, Object>of("deleted", true));
            }
            return ResponseEntity.notFound().build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** AI 生成全书大纲 */
    @PostMapping("/{novelId}/generate-outline")
    public Mono<Map<String, Object>> generateOutline(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            var novel = novelRepo.findById(novelId).orElse(null);
            if (novel == null) throw new IllegalArgumentException("小说不存在");

            var provider = providerRegistry.get("deepseek");
            if (provider == null) throw new RuntimeException("LLM 服务不可用");

            // 收集上下文
            StringBuilder context = new StringBuilder();
            context.append("书名：").append(novel.getTitle()).append("\n");
            if (novel.getOutline() != null && !novel.getOutline().isBlank()) {
                context.append("现有大纲：").append(novel.getOutline().substring(0, Math.min(200, novel.getOutline().length()))).append("\n");
            }
            if (novel.getSynopsis() != null && !novel.getSynopsis().isBlank()) {
                context.append("简介：").append(novel.getSynopsis()).append("\n");
            }
            context.append("类型：").append(novel.getGenreId()).append("\n");

            // 全书目标总字数约束
            int targetTotalWords = novel.getTargetTotalWords() != null
                    ? novel.getTargetTotalWords() : 1_500_000;
            double targetTotalWan = targetTotalWords / 10_000.0;
            int estimatedChapterCount = (int) Math.ceil(targetTotalWords / 2500.0);

            // 参考素材
            List<ReferenceMaterial> refs = refRepo.findByNovelIdOrderByCreatedAtDesc(novelId);
            if (!refs.isEmpty()) {
                context.append("\n参考素材：\n");
                for (ReferenceMaterial r : refs) {
                    context.append("- ").append(r.getFileName()).append("\n");
                }
            }

            // 已有章节
            List<Chapter> chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
            if (!chapters.isEmpty()) {
                context.append("\n已写章节（共").append(chapters.size()).append("章）：\n");
                for (Chapter ch : chapters.subList(0, Math.min(5, chapters.size()))) {
                    String plan = ch.getWritingPlan();
                    String title = ch.getTitle() != null ? ch.getTitle() : "第" + ch.getChapterNumber() + "章";
                    context.append("- ").append(title);
                    if (plan != null && !plan.isBlank()) {
                        context.append("：").append(plan.substring(0, Math.min(80, plan.length())));
                    }
                    context.append("\n");
                }
            }

            // 全书总字数约束
            context.append(String.format("\n【硬性约束】全书目标总字数不少于 %.0f 万字" +
                    "（约 %d 章），大纲规划时必须确保所有卷的章节数和字数之和满足此要求。\n",
                    targetTotalWan, estimatedChapterCount));

            String prompt = String.format("""
                你是一个网文作者，正在给自己新书列大纲。写给自己看的，不用正式，不用完整句子，要点式就行。

                已有信息：
                %s

                按以下格式写大纲（直接输出，不要多余的话）：

                第一卷：[卷名，如"圣魂村·开局"]（预计xx章，约xx字）
                - [事件1：谁做了什么，导致什么]
                - [事件2]
                - [事件3]
                - ...
                - 本卷高潮：[最大的冲突或转折是什么]
                - 卷末状态：[主角实力/势力/关系发生了什么变化]

                第二卷：[卷名]（预计xx章，约xx字）
                - [事件]
                - ...
                - 本卷高潮：
                - 卷末状态：

                （按需要写若干卷，确保所有卷的总字数之和≥硬性约束中的目标字数）

                爽点分布：
                - 第x章左右：第一次打脸
                - 第x章左右：获得重要机缘
                - 第x章左右：身份/实力曝光震惊全场
                - ...

                关键角色：
                - [角色名]：[标签式描述]，作用：[推动什么剧情]
                - ...

                结局落点：[用一两句话，燃/虐/圆满/反转？]

                要求：
                - 要点式，每行一个事件，不用连成段落
                - 具体到章级别的规划（至少精确到"前中后期"）
                - 爽点明确标注（打脸/升级/收获/曝光/复仇）
                - 字数估到万字级别
                - 每章字数按2500字计算，确保章数×2500 ≥ 全书目标总字数
                - 不要写"可以""也可以""或者"这种模棱两可的词，每个点给确定的方向
                """, context.toString());

            var response = provider.generate(
                List.of(LLMMessage.user(prompt)),
                LLMConfig.creative("deepseek-v4-pro")
            );

            String generatedOutline = response.content().trim();
            // 重新获取最新版本再保存，避免覆盖 LLM 调用期间的并发修改
            var freshNovel = novelRepo.findById(novelId).orElse(novel);
            freshNovel.setOutline(generatedOutline);
            novelRepo.save(freshNovel);
            log.info("全书大纲已自动保存: novel={}, length={}", novelId, generatedOutline.length());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outline", generatedOutline);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** AI 深度文风分析 */
    @PostMapping("/{novelId}/analyze-style")
    public Mono<Map<String, Object>> analyzeStyle(@PathVariable String novelId,
                                                   @RequestBody Map<String, Object> body) {
        String referenceText = (String) body.get("referenceText");
        if (referenceText == null || referenceText.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "error");
            error.put("message", "参考文本不能为空");
            return Mono.just(error);
        }
        return styleAnalysisService.analyzeStyle(novelId, referenceText);
    }
}
