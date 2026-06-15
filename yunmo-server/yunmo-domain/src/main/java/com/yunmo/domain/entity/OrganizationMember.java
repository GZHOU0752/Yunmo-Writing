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
@Table(name = "organization_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "character_id"}))
public class OrganizationMember extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "character_id", nullable = false)
    private String characterId;

    private String position;

    @Column(name = "joined_chapter")
    private Integer joinedChapter;
}
