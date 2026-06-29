package com.yunmo.domain.repository;

import com.yunmo.domain.entity.AgentModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AgentModelConfigRepository extends JpaRepository<AgentModelConfig, Long> {
    Optional<AgentModelConfig> findByAgentType(String agentType);
}
