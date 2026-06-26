package com.yunmo.agent.pipeline;

/**
 * Pipeline 插件接口 — 所有管线阶段实现此接口后自动注册。
 * 可以通过 application.yml 配置管线顺序，也可通过 enabled() 控制开关。
 *
 * 参考: Novel-Claude 的 PluginManager 微内核架构
 */
public interface PipelinePlugin extends PipelineStage {

    /** 插件唯一标识，用于 application.yml 中配置管线顺序 */
    @Override
    String name();

    /** 是否默认启用（可在配置中覆盖） */
    default boolean enabledByDefault() {
        return true;
    }

    /** 插件优先级（数值越小越先执行，仅在同名配置不存在时生效） */
    default int defaultPriority() {
        return 100;
    }

    /** 在管线中的角色描述 */
    default String description() {
        return "";
    }
}
