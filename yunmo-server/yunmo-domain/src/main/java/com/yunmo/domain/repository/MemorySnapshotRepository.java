package com.yunmo.domain.repository;

import com.yunmo.domain.entity.MemorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 记忆快照仓储 — 按章节号降序获取最新快照。
 */
@Repository
public interface MemorySnapshotRepository extends JpaRepository<MemorySnapshot, String> {

    /**
     * 获取小说最新的记忆快照（按章节号降序）
     */
    Optional<MemorySnapshot> findTopByNovelIdOrderByChapterNumberDesc(String novelId);
}
