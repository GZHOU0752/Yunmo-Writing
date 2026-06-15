package com.yunmo.domain.entity;

import com.yunmo.common.enums.OrganizationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", length = 20)
    private OrganizationType orgType = OrganizationType.OTHER;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "chapter_introduced")
    private Integer chapterIntroduced;
}
