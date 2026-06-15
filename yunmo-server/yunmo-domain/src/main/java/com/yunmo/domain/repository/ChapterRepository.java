package com.yunmo.domain.repository;

import com.yunmo.domain.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, String> {
    List<Chapter> findByNovelIdOrderByChapterNumberAsc(String novelId);
    Optional<Chapter> findFirstByNovelIdAndChapterNumber(String novelId, Integer chapterNumber);
    long countByNovelId(String novelId);

    /** 查询前N章（按章节号排序），避免全量加载所有章节的历史数据 */
    @Query("SELECT c FROM Chapter c WHERE c.novelId = :novelId " +
           "AND c.chapterNumber < :currentChapter " +
           "ORDER BY c.chapterNumber DESC")
    List<Chapter> findPreviousChapters(@Param("novelId") String novelId,
                                       @Param("currentChapter") int currentChapter);
}
