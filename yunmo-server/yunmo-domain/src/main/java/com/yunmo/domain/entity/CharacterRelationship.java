package com.yunmo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "character_relationships",
       uniqueConstraints = @UniqueConstraint(columnNames = {
           "novel_id", "source_character_id", "target_character_id", "relation_type"}))
public class CharacterRelationship extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(name = "source_character_id", nullable = false)
    private String sourceCharacterId;

    @Column(name = "target_character_id", nullable = false)
    private String targetCharacterId;

    @Column(nullable = false)
    private String relationType;

    @Column(columnDefinition = "TEXT")
    private String description;
}
