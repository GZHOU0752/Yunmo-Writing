package com.yunmo.domain.repository;

import com.yunmo.domain.entity.ChapterVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChapterVersionRepository extends JpaRepository<ChapterVersion, String> {
    List<ChapterVersion> findByChapterIdOrderByVersionNumberDesc(String chapterId);
    Optional<ChapterVersion> findTopByChapterIdOrderByVersionNumberDesc(String chapterId);
    int countByChapterId(String chapterId);
}
