package com.yunmo.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 全局异常处理器 — 统一处理 Controller 层抛出的异常
 * 避免堆栈信息直接暴露给前端，返回结构化错误 JSON
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return Mono.just(ResponseEntity.badRequest()
                .body(Map.<String, Object>of("error", e.getMessage() != null ? e.getMessage() : "参数错误")));
    }

    @ExceptionHandler(ClassCastException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleClassCast(ClassCastException e) {
        log.error("类型转换错误: {}", e.getMessage());
        return Mono.just(ResponseEntity.badRequest()
                .body(Map.<String, Object>of("error", "请求参数类型不正确")));
    }

    @ExceptionHandler(NullPointerException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleNPE(NullPointerException e) {
        log.error("空指针异常: {}", e.getMessage(), e);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.<String, Object>of("error", "服务器内部错误，请稍后重试")));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGeneral(Exception e) {
        log.error("未处理异常: {} — {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.<String, Object>of("error", "服务器内部错误，请稍后重试")));
    }
}
