package com.yunmo.domain.repository;

import com.yunmo.domain.entity.OutlineNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutlineNodeRepository extends JpaRepository<OutlineNode, String> {
    List<OutlineNode> findByNovelIdOrderBySequenceOrderAsc(String novelId);
    List<OutlineNode> findByNovelIdAndChapterNumber(String novelId, Integer chapterNumber);
    List<OutlineNode> findByNovelIdAndParentIdOrderBySequenceOrderAsc(String novelId, String parentId);
    List<OutlineNode> findByNovelIdAndLevelOrderBySequenceOrderAsc(String novelId, Integer level);
    List<OutlineNode> findByNovelIdAndParentIdIsNullOrderBySequenceOrderAsc(String novelId);
    long countByNovelIdAndParentId(String novelId, String parentId);
    void deleteByNovelIdAndParentId(String novelId, String parentId);
}
