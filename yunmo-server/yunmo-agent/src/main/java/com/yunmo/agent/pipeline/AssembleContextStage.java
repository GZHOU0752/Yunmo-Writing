package com.yunmo.agent.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 上下文组装阶段 — 替代 Python assemble_context_node
 * 验证 context_layers 和 chapter_plan 已在 API 层预先填入
 */
@Component
public class AssembleContextStage implements PipelinePlugin {
    @Override public int defaultPriority() { return 10; }

    private static final Logger log = LoggerFactory.getLogger(AssembleContextStage.class);

    @Override
    public StageOutput execute(PipelineState state) {
        log.info("[AssembleContext] 验证上下文完整性");

        // 验证关键字段已由 API 层填入
        String contextText = state.get("context_text", String.class);
        String chapterPlan = state.get("chapter_plan", String.class);

        if (contextText == null && chapterPlan == null) {
            // 上下文缺失，尝试从虚拟文件系统恢复
            log.warn("[AssembleContext] 上下文字段为空，尝试从虚拟文件系统恢复");
            contextText = state.readFile("context_text.txt");
            chapterPlan = state.readFile("chapter_plan.txt");
            if (contextText != null) state.put("context_text", contextText);
            if (chapterPlan != null) state.put("chapter_plan", chapterPlan);
        }

        log.info("[AssembleContext] 上下文组装完成 — context: {} chars, plan: {} chars",
                contextText != null ? contextText.length() : 0,
                chapterPlan != null ? chapterPlan.length() : 0);

        return StageOutput.empty();
    }

    @Override
    public String name() {
        return "assemble_context";
    }
}
