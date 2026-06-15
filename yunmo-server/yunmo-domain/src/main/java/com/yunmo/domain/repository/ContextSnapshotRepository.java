package com.yunmo.domain.repository;

import com.yunmo.domain.entity.ContextSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContextSnapshotRepository extends JpaRepository<ContextSnapshot, String> {
    Optional<ContextSnapshot> findByNovelIdAndChapterId(String novelId, String chapterId);
}
