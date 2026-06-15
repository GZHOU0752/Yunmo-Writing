package com.yunmo.domain.repository;

import com.yunmo.domain.entity.ReferenceMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferenceMaterialRepository extends JpaRepository<ReferenceMaterial, String> {
    List<ReferenceMaterial> findByNovelIdOrderByCreatedAtDesc(String novelId);
    long countByNovelId(String novelId);
    void deleteByNovelId(String novelId);
}
