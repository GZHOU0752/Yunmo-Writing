package com.yunmo.service.style;

import java.util.List;

/**
 * 风格选择结果 — 每次章节写作的风格模块组合
 *
 * @param primary      主风格（必须）
 * @param secondary    次风格（可为null）
 * @param auxiliary    辅助风格（可为null）
 * @param styleContext 组合后的风格上下文文本，可直接注入Writer prompt
 * @param priorities   本章优先兑现项（从各风格合并）
 * @param warnings     伪风格警示（防止AI误用风格）
 */
public record StyleSelection(
        StyleModule primary,
        StyleModule secondary,
        StyleModule auxiliary,
        String styleContext,
        List<String> priorities,
        List<String> warnings
) {
    /**
     * 简洁构造 — 仅设主风格，自动生成上下文
     */
    public StyleSelection(StyleModule primary) {
        this(primary, null, null, "", List.of(), List.of());
    }

    /**
     * 是否有次风格
     */
    public boolean hasSecondary() {
        return secondary != null;
    }

    /**
     * 是否有辅助风格
     */
    public boolean hasAuxiliary() {
        return auxiliary != null;
    }

    /**
     * 涉及的所有风格模块（去重）
     */
    public List<StyleModule> allModules() {
        if (secondary == null && auxiliary == null) {
            return List.of(primary);
        }
        if (auxiliary == null) {
            return List.of(primary, secondary);
        }
        if (secondary == null) {
            return List.of(primary, auxiliary);
        }
        // 去重
        if (primary == auxiliary) {
            return List.of(primary, secondary);
        }
        if (secondary == auxiliary) {
            return List.of(primary, secondary);
        }
        return List.of(primary, secondary, auxiliary);
    }
}
