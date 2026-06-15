package com.yunmo.api.controller;

import com.yunmo.common.enums.OrganizationType;
import com.yunmo.domain.entity.Organization;
import com.yunmo.domain.repository.OrganizationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/organizations")
public class OrganizationController {

    private final OrganizationRepository repo;

    public OrganizationController(OrganizationRepository repo) { this.repo = repo; }

    @GetMapping
    public Mono<List<Organization>> list(@PathVariable String novelId) {
        return Mono.fromCallable(() -> repo.findByNovelId(novelId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<Organization> create(@PathVariable String novelId,
                                      @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            Organization o = new Organization();
            o.setNovelId(novelId);
            o.setName((String) body.get("name"));
            o.setOrgType(OrganizationType.valueOf(
                    ((String) body.getOrDefault("org_type", "OTHER")).toUpperCase()));
            o.setDescription((String) body.getOrDefault("description", ""));
            return repo.save(o);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Organization>> update(
            @PathVariable String novelId, @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> repo.findById(id)
                .filter(o -> o.getNovelId().equals(novelId))
                .map(o -> {
                    if (body.containsKey("name")) o.setName((String) body.get("name"));
                    if (body.containsKey("description"))
                        o.setDescription((String) body.get("description"));
                    return ResponseEntity.ok(repo.save(o));
                }).orElse(ResponseEntity.notFound().build())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String novelId,
                                              @PathVariable String id) {
        return Mono.fromCallable(() -> {
            if (repo.findById(id).filter(o -> o.getNovelId().equals(novelId)).isPresent()) {
                repo.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }
            return ResponseEntity.notFound().<Void>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
