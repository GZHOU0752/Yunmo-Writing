package com.yunmo.domain.repository;

import com.yunmo.domain.entity.Novel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NovelRepository extends JpaRepository<Novel, String> {
    List<Novel> findByUserIdOrderByCreatedAtDesc(String userId);
}
