package com.yunmo.api.controller;

import com.yunmo.domain.entity.Character;
import com.yunmo.domain.repository.CharacterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/characters")
public class CharacterController {

    private final CharacterRepository characterRepo;

    public CharacterController(CharacterRepository characterRepo) {
        this.characterRepo = characterRepo;
    }

    @GetMapping
    public Mono<List<Character>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() ->
                characterRepo.findByNovelIdOrderByImportanceDesc(novelId)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Character>> get(@PathVariable String novelId, @PathVariable String id) {
        return Mono.fromCallable(() ->
                characterRepo.findById(id)
                        .filter(c -> c.getNovelId().equals(novelId))
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<Character> create(@PathVariable String novelId, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            Character c = new Character();
            c.setNovelId(novelId);
            c.setName((String) body.get("name"));
            c.setRole(com.yunmo.common.enums.CharacterRole.valueOf(
                    ((String) body.getOrDefault("role", "SUPPORTING")).toUpperCase()));
            c.setDescription((String) body.getOrDefault("description", ""));
            c.setImportance(body.containsKey("importance")
                    ? ((Number) body.get("importance")).intValue() : 5);
            return characterRepo.save(c);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Character>> update(
            @PathVariable String novelId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() ->
                characterRepo.findById(id)
                        .filter(c -> c.getNovelId().equals(novelId))
                        .map(c -> {
                            if (body.containsKey("name")) c.setName((String) body.get("name"));
                            if (body.containsKey("description")) c.setDescription((String) body.get("description"));
                            if (body.containsKey("importance"))
                                c.setImportance(((Number) body.get("importance")).intValue());
                            if (body.containsKey("layer1_worldview"))
                                c.setLayer1Worldview((String) body.get("layer1_worldview"));
                            if (body.containsKey("layer2_identity"))
                                c.setLayer2Identity((String) body.get("layer2_identity"));
                            if (body.containsKey("layer3_values"))
                                c.setLayer3Values((String) body.get("layer3_values"));
                            if (body.containsKey("layer4_abilities"))
                                c.setLayer4Abilities((String) body.get("layer4_abilities"));
                            if (body.containsKey("layer5_skills"))
                                c.setLayer5Skills((String) body.get("layer5_skills"));
                            if (body.containsKey("layer6_environment"))
                                c.setLayer6Environment((String) body.get("layer6_environment"));
                            if (body.containsKey("current_state"))
                                c.setCurrentState((Map) body.get("current_state"));
                            return ResponseEntity.ok(characterRepo.save(c));
                        })
                        .orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String novelId, @PathVariable String id) {
        return Mono.fromCallable(() -> {
            var c = characterRepo.findById(id);
            if (c.isPresent() && c.get().getNovelId().equals(novelId)) {
                characterRepo.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }
            return ResponseEntity.notFound().<Void>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
