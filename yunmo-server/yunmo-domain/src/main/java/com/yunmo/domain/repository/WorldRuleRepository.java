package com.yunmo.domain.repository;

import com.yunmo.domain.entity.WorldRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 世界规则 Repository
 */
@Repository
public interface WorldRuleRepository extends JpaRepository<WorldRule, String> {

    /** 按小说查询所有世界规则 */
    List<WorldRule> findByNovelId(String novelId);

    /** 按小说和分类查询 */
    List<WorldRule> findByNovelIdAndCategory(String novelId, WorldRule.RuleCategory category);

    /** 按小说和状态查询 */
    List<WorldRule> findByNovelIdAndStatus(String novelId, WorldRule.RuleStatus status);

    /** 按规则名查找 */
    Optional<WorldRule> findByNovelIdAndRuleName(String novelId, String ruleName);

    /** 查询在某章揭示的规则 */
    List<WorldRule> findByNovelIdAndRevealedChapter(String novelId, Integer revealedChapter);
}
