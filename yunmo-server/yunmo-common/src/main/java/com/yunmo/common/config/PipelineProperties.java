package com.yunmo.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Pipeline 插件配置 — 从 application.yml 读取管线顺序和禁用列表。
 *
 * 参考: Novel-Claude 的微内核插件配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunmo.pipeline")
public class PipelineProperties {

    /** 管线阶段执行顺序（按名称列表） */
    private List<String> stages = new ArrayList<>();

    /** 禁用的阶段名称列表 */
    private Set<String> disabled = new HashSet<>();
}
