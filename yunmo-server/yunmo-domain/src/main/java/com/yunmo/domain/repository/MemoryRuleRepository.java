package com.yunmo.domain.repository;

import com.yunmo.domain.entity.MemoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryRuleRepository extends JpaRepository<MemoryRule, String> {
    List<MemoryRule> findByNovelIdOrderByPriorityDesc(String novelId);
}
