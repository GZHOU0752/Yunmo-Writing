package com.yunmo.domain.repository;

import com.yunmo.domain.entity.CharacterState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色状态快照 Repository
 */
@Repository
public interface CharacterStateRepository extends JpaRepository<CharacterState, String> {

    /** 按小说和角色查询状态历史（按章节升序） */
    List<CharacterState> findByNovelIdAndCharacterIdOrderByChapterNumberAsc(String novelId, String characterId);

    /** 按小说和章节查询该章所有角色的状态快照 */
    List<CharacterState> findByNovelIdAndChapterNumber(String novelId, Integer chapterNumber);

    /** 获取角色最新状态 */
    List<CharacterState> findTop1ByNovelIdAndCharacterIdOrderByChapterNumberDesc(String novelId, String characterId);
}
