package com.yunmo.api.controller;

import com.yunmo.domain.entity.AgentModelConfig;
import com.yunmo.domain.repository.AgentModelConfigRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config/agent-models")
public class AgentModelConfigController {

    private final AgentModelConfigRepository repository;

    public AgentModelConfigController(AgentModelConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Mono<List<AgentModelConfig>> getAll() {
        return Mono.fromCallable(repository::findAll)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping
    public Mono<List<AgentModelConfig>> updateAll(@RequestBody List<AgentModelConfig> configs) {
        return Mono.fromCallable(() -> {
            for (AgentModelConfig config : configs) {
                var existing = repository.findByAgentType(config.getAgentType());
                if (existing.isPresent()) {
                    var e = existing.get();
                    e.setProvider(config.getProvider());
                    e.setModel(config.getModel());
                    e.setEnabled(config.isEnabled());
                    repository.save(e);
                } else {
                    repository.save(config);
                }
            }
            return repository.findAll();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/reset")
    public Mono<List<AgentModelConfig>> reset() {
        return Mono.fromCallable(() -> {
            repository.deleteAll();
            // Return empty - AgentFactory will use defaults
            return repository.findAll();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
