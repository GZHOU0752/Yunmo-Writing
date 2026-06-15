package com.yunmo.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        return Mono.just(Map.<String, Object>of(
                "status", "ok",
                "timestamp", LocalDateTime.now().toString(),
                "server", "YunMo Server (Java 17 + Spring Boot 3.2)"
        ));
    }
}
