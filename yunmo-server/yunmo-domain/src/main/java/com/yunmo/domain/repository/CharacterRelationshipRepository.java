package com.yunmo.domain.repository;

import com.yunmo.domain.entity.CharacterRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CharacterRelationshipRepository extends JpaRepository<CharacterRelationship, String> {
    List<CharacterRelationship> findByNovelId(String novelId);
    List<CharacterRelationship> findBySourceCharacterIdOrTargetCharacterId(String sourceId, String targetId);
}
