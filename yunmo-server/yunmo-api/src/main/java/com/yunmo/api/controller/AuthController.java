package com.yunmo.api.controller;

import com.yunmo.common.config.AppProperties;
import com.yunmo.common.util.JwtUtil;
import com.yunmo.domain.entity.User;
import com.yunmo.domain.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final AppProperties appProperties;
    private final StringRedisTemplate redis;

    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private static final String REDIS_KEY_PREFIX = "yunmo:token:";
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepo, AppProperties appProperties,
                          StringRedisTemplate redis) {
        this.userRepo = userRepo;
        this.appProperties = appProperties;
        this.redis = redis;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, Object>>> register(@RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String email = (String) body.get("email");
            String password = (String) body.get("password");
            String displayName = (String) body.getOrDefault("display_name", email);

            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.<String, Object>of("error", "邮箱和密码不能为空"));
            }
            if (userRepo.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(Map.<String, Object>of("error", "该邮箱已注册"));
            }

            User user = new User();
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setPasswordHash(passwordEncoder.encode(password));
            user = userRepo.save(user);

            String token = JwtUtil.create(getSecret(), user.getId(), TOKEN_TTL.toMillis());

            // 存入 Redis
            redis.opsForValue().set(REDIS_KEY_PREFIX + token, user.getId(), TOKEN_TTL);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("user", toUserMap(user));
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, Object>>> login(@RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String email = (String) body.get("email");
            String password = (String) body.get("password");

            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.<String, Object>of("error", "邮箱和密码不能为空"));
            }

            var userOpt = userRepo.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401)
                        .body(Map.<String, Object>of("error", "邮箱或密码错误"));
            }

            User user = userOpt.get();
            if (!verifyPassword(password, user.getPasswordHash())) {
                return ResponseEntity.status(401)
                        .body(Map.<String, Object>of("error", "邮箱或密码错误"));
            }

            String token = JwtUtil.create(getSecret(), user.getId(), TOKEN_TTL.toMillis());

            // 存入 Redis
            redis.opsForValue().set(REDIS_KEY_PREFIX + token, user.getId(), TOKEN_TTL);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("user", toUserMap(user));
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, Object>>> logout(@RequestHeader("Authorization") String auth) {
        return Mono.fromCallable(() -> {
            if (auth != null && auth.startsWith("Bearer ")) {
                redis.delete(REDIS_KEY_PREFIX + auth.substring(7));
            }
            return ResponseEntity.ok(Map.<String, Object>of("message", "已退出"));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/account")
    public Mono<ResponseEntity<Map<String, Object>>> deleteAccount(@RequestHeader("Authorization") String auth) {
        return Mono.fromCallable(() -> {
            if (auth == null || !auth.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.<String, Object>of("error", "未认证"));
            }
            String token = auth.substring(7);
            String userId = JwtUtil.verifyAndGetSubject(getSecret(), token);
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.<String, Object>of("error", "令牌无效或已过期"));
            }
            var user = userRepo.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.<String, Object>of("error", "用户不存在"));
            }
            // 删除用户及其所有数据
            userRepo.delete(user);
            redis.delete(REDIS_KEY_PREFIX + token);
            return ResponseEntity.ok(Map.<String, Object>of("message", "账号已注销"));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<Map<String, Object>>> me(@RequestHeader("Authorization") String auth) {
        return Mono.fromCallable(() -> {
            if (auth == null || !auth.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.<String, Object>of("error", "未认证"));
            }
            String token = auth.substring(7);
            String userId = JwtUtil.verifyAndGetSubject(getSecret(), token);
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.<String, Object>of("error", "令牌无效或已过期"));
            }
            // Redis 验证：如果 token 已被删除（用户退出），拒绝访问
            if (Boolean.FALSE.equals(redis.hasKey(REDIS_KEY_PREFIX + token))) {
                return ResponseEntity.status(401).body(Map.<String, Object>of("error", "令牌已失效"));
            }
            return userRepo.findById(userId)
                    .map(u -> ResponseEntity.ok(toUserMap(u)))
                    .orElse(ResponseEntity.status(401).body(Map.<String, Object>of("error", "用户不存在")));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String getSecret() {
        return appProperties.getSecretKey();
    }

    /**
     * 验证密码 — 兼容 BCrypt 新格式和 SHA-256 旧格式
     * 旧用户登录成功后自动迁移到 BCrypt 格式
     */
    private boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null) return false;
        // BCrypt 哈希以 "$2a$" / "$2b$" 开头
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$")) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }
        // 兼容旧的 SHA-256 哈希
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String legacyHash = Base64.getEncoder().encodeToString(hash);
            return legacyHash.equals(storedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("display_name", user.getDisplayName());
        return map;
    }
}
