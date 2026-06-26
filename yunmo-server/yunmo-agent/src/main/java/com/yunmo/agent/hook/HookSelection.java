package com.yunmo.agent.hook;

import java.util.List;

/**
 * 钩子选择结果 — 包含一章完整的钩子策略
 *
 * @param openingHook      章首引子类型
 * @param openingPrompt    章首引子生成 Prompt
 * @param closingHook      章尾钩子类型
 * @param closingPrompt    章尾钩子生成 Prompt
 * @param suspenseIntensity 悬念强度 (1-5)
 * @param arcContext       跨章悬念弧上下文
 * @param previousHooks    前3章使用的钩子（用于避免重复）
 */
public record HookSelection(
        HookType openingHook,
        String openingPrompt,
        HookType closingHook,
        String closingPrompt,
        int suspenseIntensity,
        String arcContext,
        List<HookType> previousHooks
) {
    /**
     * 紧凑型工厂方法（无弧上下文，无前章钩子记录）
     */
    public static HookSelection of(HookType opening, HookType closing, int intensity) {
        return new HookSelection(opening, opening.promptTemplate(), closing, closing.promptTemplate(),
                intensity, "", List.of());
    }

    /**
     * 完整工厂方法
     */
    public static HookSelection full(HookType opening, String openingPrompt,
                                      HookType closing, String closingPrompt,
                                      int intensity, String arcContext,
                                      List<HookType> previousHooks) {
        return new HookSelection(opening, openingPrompt, closing, closingPrompt,
                intensity, arcContext, previousHooks);
    }
}
