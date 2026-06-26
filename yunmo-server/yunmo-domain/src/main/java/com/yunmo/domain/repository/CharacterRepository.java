package com.yunmo.domain.repository;

import com.yunmo.domain.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterRepository extends JpaRepository<Character, String> {
    List<Character> findByNovelIdOrderByImportanceDesc(String novelId);
    List<Character> findByNovelIdAndIsDeadFalse(String novelId);
    List<Character> findByNovelIdAndIsDeadTrue(String novelId);
}
