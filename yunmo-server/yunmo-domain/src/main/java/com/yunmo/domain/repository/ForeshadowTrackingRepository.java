package com.yunmo.domain.repository;

import com.yunmo.domain.entity.ForeshadowTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 伏笔追踪 Repository
 */
@Repository
public interface ForeshadowTrackingRepository extends JpaRepository<ForeshadowTracking, String> {

    /** 按小说查询所有伏笔追踪 */
    List<ForeshadowTracking> findByNovelId(String novelId);

    /** 按小说和状态查询 */
    List<ForeshadowTracking> findByNovelIdAndStatus(String novelId, ForeshadowTracking.HookStatus status);

    /** 按伏笔编号查找 */
    Optional<ForeshadowTracking> findByNovelIdAndHookId(String novelId, String hookId);

    /** 查询某章埋设的伏笔 */
    List<ForeshadowTracking> findByNovelIdAndPlantedChapter(String novelId, Integer plantedChapter);

    /** 查询预计在某章回收的伏笔 */
    List<ForeshadowTracking> findByNovelIdAndExpectedPayoffChapter(String novelId, Integer chapterNumber);

    /** 查询仍未回收的伏笔（按埋设章节升序） */
    List<ForeshadowTracking> findByNovelIdAndStatusNotOrderByPlantedChapterAsc(String novelId, ForeshadowTracking.HookStatus status);
}
