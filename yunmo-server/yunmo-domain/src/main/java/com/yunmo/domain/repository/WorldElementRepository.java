package com.yunmo.domain.repository;

import com.yunmo.domain.entity.WorldElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldElementRepository extends JpaRepository<WorldElement, String> {
    List<WorldElement> findByNovelId(String novelId);
}
