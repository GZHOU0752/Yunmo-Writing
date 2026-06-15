package com.yunmo.service;

import com.yunmo.domain.entity.Character;
import com.yunmo.domain.repository.CharacterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体冷却管理
 */
@Service
public class EntityCoolingService {

    private static final Logger log = LoggerFactory.getLogger(EntityCoolingService.class);
    private final CharacterRepository characterRepo;

    public EntityCoolingService(CharacterRepository characterRepo) {
        this.characterRepo = characterRepo;
    }

    /** 每章归档后更新角色冷却状态（仅保存变更的角色） */
    public void updateCooling(String novelId, int currentChapter) {
        var characters = characterRepo.findByNovelIdOrderByImportanceDesc(novelId);
        List<Character> changed = new ArrayList<>();

        for (var c : characters) {
            if (c.getIsDead()) continue;

            int importance = c.getImportance() != null ? c.getImportance() : 5;

            // 核心角色 (importance >= 9): 永不冷却
            if (importance >= 9) continue;

            // 主要角色 (importance >= 7): 6章降温, 12章冷藏
            // 次要角色: 3章降温, 8章冷藏
            int cooldownChapters = importance >= 7 ? 6 : 3;
            int freezeChapters = importance >= 7 ? 12 : 8;

            if (c.getLastAppearanceChapter() != null
                    && c.getLastAppearanceChapter() <= currentChapter - freezeChapters) {
                c.setCooldownUntilChapter(currentChapter + cooldownChapters);
                changed.add(c);
            }
        }

        if (!changed.isEmpty()) {
            characterRepo.saveAll(changed);
            log.info("角色冷却更新: novel={}, chapter={}, 变更角色数={}", novelId, currentChapter, changed.size());
        }
    }

    /** 获取当前可登场的角色 */
    public List<Character> getAvailableCharacters(String novelId, int currentChapter) {
        return characterRepo.findByNovelIdAndIsDeadFalse(novelId).stream()
                .filter(c -> c.getCooldownUntilChapter() == null
                        || c.getCooldownUntilChapter() <= currentChapter)
                .toList();
    }

    /** 标记角色出场 */
    public void markAppearance(String characterId, int chapterNumber) {
        characterRepo.findById(characterId).ifPresent(c -> {
            c.setLastAppearanceChapter(chapterNumber);
            c.setCooldownUntilChapter(null);
            characterRepo.save(c);
        });
    }
}
