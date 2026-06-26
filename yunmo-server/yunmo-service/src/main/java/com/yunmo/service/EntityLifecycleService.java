package com.yunmo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmo.domain.entity.Character;
import com.yunmo.domain.repository.CharacterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体生命周期服务 — 追踪角色/物品/地点的出场状态
 * 对标 WinkNovel 的 entity_manager + 终结实体警告
 *
 * 生命周期层级：
 *   ACTIVE — 最近 3 章内出场
 *   COOLING — 3-6 章未出场（摘要给 Writer 参考）
 *   COLD — 6+ 章未出场（压缩为一行提示）
 *   TERMINATED — 已死亡/销毁（Writer 禁止使用）
 */
@Service
public class EntityLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(EntityLifecycleService.class);

    private final CharacterRepository characterRepo;

    /** 冷却窗口：3章内为活跃 */
    private static final int COOLING_WINDOW = 3;
    /** 冷窗口：6章以上为冷 */
    private static final int COLD_WINDOW = 6;

    public EntityLifecycleService(CharacterRepository characterRepo) {
        this.characterRepo = characterRepo;
    }

    /**
     * 获取终结实体警告 — 告诉 Writer 哪些角色已死亡/退场，本章绝对不能使用
     */
    public List<String> getTerminatedEntityWarnings(String novelId, int currentChapter) {
        List<String> warnings = new ArrayList<>();
        try {
            // 已死亡的角色
            var deadChars = characterRepo.findByNovelIdAndIsDeadTrue(novelId);
            for (Character c : deadChars) {
                warnings.add(String.format("⚠️ 角色「%s」已死亡（第%d章），本章绝对不得出场或提及为活人",
                    c.getName(), c.getLastAppearanceChapter() != null ? c.getLastAppearanceChapter() : 0));
            }

            // 长期未出场但未死的角色：检查是否超过 COLD_WINDOW
            var aliveChars = characterRepo.findByNovelIdAndIsDeadFalse(novelId);
            for (Character c : aliveChars) {
                int lastApp = c.getLastAppearanceChapter() != null ? c.getLastAppearanceChapter() : 0;
                if (lastApp > 0 && (currentChapter - lastApp) > COLD_WINDOW) {
                    warnings.add(String.format("💤 角色「%s」已 %d 章未出场（最后出场：第%d章），如需出场请给出合理动机",
                        c.getName(), currentChapter - lastApp, lastApp));
                }
            }
        } catch (Exception e) {
            log.debug("[EntityLifecycle] 终结实体检查失败: {}", e.getMessage());
        }
        return warnings;
    }

    /**
     * 获取活跃/冷却实体摘要 — 供 Writer 了解哪些角色当前可用
     * @return 格式化的实体摘要文本
     */
    public String getEntitySummary(String novelId, int currentChapter) {
        StringBuilder sb = new StringBuilder();
        try {
            var chars = characterRepo.findByNovelIdAndIsDeadFalse(novelId);
            if (chars.isEmpty()) return "";

            List<Character> active = new ArrayList<>();
            List<Character> cooling = new ArrayList<>();
            List<Character> cold = new ArrayList<>();

            for (Character c : chars) {
                int lastApp = c.getLastAppearanceChapter() != null ? c.getLastAppearanceChapter() : 0;
                int gap = currentChapter - lastApp;
                if (gap <= COOLING_WINDOW) {
                    active.add(c);
                } else if (gap <= COLD_WINDOW) {
                    cooling.add(c);
                } else {
                    cold.add(c);
                }
            }

            if (!active.isEmpty()) {
                sb.append("## 活跃角色（最近3章内出场）\n");
                for (Character c : active) {
                    sb.append(formatCharSummary(c)).append("\n");
                }
                sb.append("\n");
            }

            if (!cooling.isEmpty()) {
                sb.append("## 冷却角色（3-6章未出场，可调用但需合理承接）\n");
                for (Character c : cooling) {
                    int gap = currentChapter - (c.getLastAppearanceChapter() != null ? c.getLastAppearanceChapter() : 0);
                    sb.append(String.format("- %s（%s，%d章未出场）\n", c.getName(), c.getRole().getDescription(), gap));
                }
                sb.append("\n");
            }

            if (!cold.isEmpty()) {
                sb.append("## 冷藏角色（6+章未出场，如需出场必须给合理动机）\n");
                for (Character c : cold) {
                    int gap = currentChapter - (c.getLastAppearanceChapter() != null ? c.getLastAppearanceChapter() : 0);
                    sb.append(String.format("- %s（%d章未出场）\n", c.getName(), gap));
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.debug("[EntityLifecycle] 实体摘要生成失败: {}", e.getMessage());
        }
        return sb.toString();
    }

    private String formatCharSummary(Character c) {
        StringBuilder s = new StringBuilder();
        s.append(String.format("- **%s**（%s）", c.getName(), c.getRole().getDescription()));
        if (c.getDescription() != null && !c.getDescription().isBlank()) {
            String desc = c.getDescription();
            if (desc.length() > 60) desc = desc.substring(0, 60) + "…";
            s.append(": ").append(desc);
        }
        if (c.getImportance() >= 8) {
            s.append(" [核心]");
        }
        return s.toString();
    }
}
