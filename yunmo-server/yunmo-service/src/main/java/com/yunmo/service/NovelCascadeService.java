package com.yunmo.service;

import com.yunmo.domain.repository.*;
import com.yunmo.service.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小说级联删除服务 — 删除小说时同步清除所有关联数据
 */
@Service
public class NovelCascadeService {

    private static final Logger log = LoggerFactory.getLogger(NovelCascadeService.class);

    private final ChapterRepository chapterRepo;
    private final ChapterVersionRepository versionRepo;
    private final CharacterRepository characterRepo;
    private final WorldElementRepository worldRepo;
    private final ForeshadowRepository foreshadowRepo;
    private final MemoryRuleRepository memoryRepo;
    private final OutlineNodeRepository outlineRepo;
    private final ContextSnapshotRepository snapshotRepo;
    private final ReferenceMaterialRepository refMaterialRepo;
    private final VectorStoreService vectorStore;
    private final NovelRepository novelRepo;

    public NovelCascadeService(ChapterRepository chapterRepo,
                               ChapterVersionRepository versionRepo,
                               CharacterRepository characterRepo,
                               WorldElementRepository worldRepo,
                               ForeshadowRepository foreshadowRepo,
                               MemoryRuleRepository memoryRepo,
                               OutlineNodeRepository outlineRepo,
                               ContextSnapshotRepository snapshotRepo,
                               ReferenceMaterialRepository refMaterialRepo,
                               VectorStoreService vectorStore,
                               NovelRepository novelRepo) {
        this.chapterRepo = chapterRepo;
        this.versionRepo = versionRepo;
        this.characterRepo = characterRepo;
        this.worldRepo = worldRepo;
        this.foreshadowRepo = foreshadowRepo;
        this.memoryRepo = memoryRepo;
        this.outlineRepo = outlineRepo;
        this.snapshotRepo = snapshotRepo;
        this.refMaterialRepo = refMaterialRepo;
        this.vectorStore = vectorStore;
        this.novelRepo = novelRepo;
    }

    @Transactional
    public boolean cascadeDelete(String novelId) {
        if (!novelRepo.existsById(novelId)) return false;

        log.info("级联删除小说: {}", novelId);
        int count = 0;

        // 1. 章节版本（先删，版本依赖章节）
        var chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
        for (var ch : chapters) {
            try {
                var versions = versionRepo.findByChapterIdOrderByVersionNumberDesc(ch.getId());
                versionRepo.deleteAll(versions);
                count += versions.size();
            } catch (Exception e) { log.debug("清理版本: {}", e.getMessage()); }
        }

        // 2. 上下文快照
        for (var ch : chapters) {
            var snap = snapshotRepo.findByNovelIdAndChapterId(novelId, ch.getId());
            if (snap.isPresent()) {
                snapshotRepo.delete(snap.get());
                count++;
            }
        }

        // 3. 章节
        chapterRepo.deleteAll(chapters);
        count += chapters.size();

        // 4. 大纲
        var outlines = outlineRepo.findByNovelIdOrderBySequenceOrderAsc(novelId);
        outlineRepo.deleteAll(outlines);
        count += outlines.size();

        // 5. 角色
        var chars = characterRepo.findByNovelIdOrderByImportanceDesc(novelId);
        characterRepo.deleteAll(chars);
        count += chars.size();

        // 6. 世界观
        var worlds = worldRepo.findByNovelId(novelId);
        worldRepo.deleteAll(worlds);
        count += worlds.size();

        // 7. 伏笔
        var fshadows = foreshadowRepo.findByNovelId(novelId);
        foreshadowRepo.deleteAll(fshadows);
        count += fshadows.size();

        // 8. 记忆规则
        var rules = memoryRepo.findByNovelIdOrderByPriorityDesc(novelId);
        memoryRepo.deleteAll(rules);
        count += rules.size();

        // 9. 参考素材 + 向量
        var refs = refMaterialRepo.findByNovelIdOrderByCreatedAtDesc(novelId);
        refMaterialRepo.deleteAll(refs);
        count += refs.size();
        try { vectorStore.delete(novelId); } catch (Exception e) { log.debug("清理向量: {}", e.getMessage()); }

        // 最后删除小说
        novelRepo.deleteById(novelId);
        log.info("小说 {} 已删除，清理 {} 条关联数据", novelId, count);
        return true;
    }
}
