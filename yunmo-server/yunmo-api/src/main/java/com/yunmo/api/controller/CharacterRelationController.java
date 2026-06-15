package com.yunmo.api.controller;

import com.yunmo.domain.entity.Character;
import com.yunmo.domain.entity.CharacterRelationship;
import com.yunmo.domain.repository.CharacterRelationshipRepository;
import com.yunmo.domain.repository.CharacterRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/relations")
public class CharacterRelationController {

    private final CharacterRepository charRepo;
    private final CharacterRelationshipRepository relRepo;

    public CharacterRelationController(CharacterRepository charRepo,
                                         CharacterRelationshipRepository relRepo) {
        this.charRepo = charRepo;
        this.relRepo = relRepo;
    }

    @GetMapping
    public Mono<Map<String, Object>> getGraph(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            List<Character> chars = charRepo.findByNovelIdOrderByImportanceDesc(novelId);
            List<CharacterRelationship> rels = relRepo.findByNovelId(novelId);

            Map<String, String> nameMap = new HashMap<>();
            for (Character c : chars) nameMap.put(c.getId(), c.getName());

            List<Map<String, Object>> nodes = new ArrayList<>();
            for (Character c : chars) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("id", c.getId());
                n.put("name", c.getName());
                n.put("role", c.getRole().name());
                n.put("importance", c.getImportance());
                nodes.add(n);
            }

            List<Map<String, Object>> edges = new ArrayList<>();
            for (CharacterRelationship r : rels) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("source", r.getSourceCharacterId());
                e.put("target", r.getTargetCharacterId());
                e.put("sourceName", nameMap.getOrDefault(r.getSourceCharacterId(), "?"));
                e.put("targetName", nameMap.getOrDefault(r.getTargetCharacterId(), "?"));
                e.put("relationType", r.getRelationType());
                e.put("description", r.getDescription());
                edges.add(e);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", nodes);
            result.put("edges", edges);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
