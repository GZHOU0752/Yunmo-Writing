package com.yunmo.domain.repository;

import com.yunmo.common.enums.ForeshadowStatus;
import com.yunmo.domain.entity.Foreshadow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForeshadowRepository extends JpaRepository<Foreshadow, String> {
    List<Foreshadow> findByNovelId(String novelId);
    List<Foreshadow> findByNovelIdAndStatus(String novelId, ForeshadowStatus status);
}
