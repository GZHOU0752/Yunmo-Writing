package com.yunmo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "conversation_messages")
public class ConversationMessage extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(length = 20)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "agent_type", length = 32)
    private String agentType;
}
