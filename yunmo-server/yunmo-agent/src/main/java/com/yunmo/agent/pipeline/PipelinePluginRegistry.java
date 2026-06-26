package com.yunmo.agent.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Pipeline 插件注册中心 — 自动发现所有 PipelinePlugin Bean，
 * 支持从 application.yml 配置管线顺序。
 *
 * 配置示例（application.yml）：
 * yunmo.pipeline.stages:
 *   - assemble_context
 *   - debate_outline
 *   - preflight
 *   - pleasure_beat
 *   - write_chapter
 *   - polish_chapter
 *   - adversarial_edit
 */
@Component
public class PipelinePluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PipelinePluginRegistry.class);

    private final Map<String, PipelinePlugin> plugins = new LinkedHashMap<>();

    /** Spring 自动注入所有 PipelinePlugin Bean */
    public PipelinePluginRegistry(List<PipelinePlugin> pluginList) {
        for (PipelinePlugin plugin : pluginList) {
            plugins.put(plugin.name(), plugin);
            log.info("[PipelinePlugin] 注册插件: {} (enabled={}, priority={})",
                plugin.name(), plugin.enabledByDefault(), plugin.defaultPriority());
        }
    }

    /** 获取所有注册的插件 */
    public Map<String, PipelinePlugin> all() {
        return Collections.unmodifiableMap(plugins);
    }

    /** 按名称获取 */
    public PipelinePlugin get(String name) {
        return plugins.get(name);
    }

    /**
     * 根据配置的顺序列表返回有序插件。
     * 如果 stages 为 null 或空，按照 defaultPriority 排序。
     */
    public List<PipelinePlugin> ordered(List<String> stageOrder, Set<String> disabled) {
        List<PipelinePlugin> result = new ArrayList<>();

        if (stageOrder != null && !stageOrder.isEmpty()) {
            // 按配置顺序
            for (String name : stageOrder) {
                if (disabled != null && disabled.contains(name)) {
                    log.info("[PipelinePlugin] 插件已禁用(配置): {}", name);
                    continue;
                }
                PipelinePlugin plugin = plugins.get(name);
                if (plugin != null) {
                    result.add(plugin);
                } else {
                    log.warn("[PipelinePlugin] 配置中的插件不存在: {}", name);
                }
            }
        } else {
            // 按优先级排序
            result = plugins.values().stream()
                .filter(p -> disabled == null || !disabled.contains(p.name()))
                .filter(PipelinePlugin::enabledByDefault)
                .sorted(Comparator.comparingInt(PipelinePlugin::defaultPriority))
                .toList();
        }

        log.info("[PipelinePlugin] 管线顺序: {} ({} plugins)",
            result.stream().map(PipelinePlugin::name).toList(), result.size());
        return result;
    }

    /** 获取有序插件（默认顺序，不禁用任何插件） */
    public List<PipelinePlugin> ordered() {
        return ordered(null, Set.of());
    }
}
