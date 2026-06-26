package com.yunmo.agent.pipeline;

import com.yunmo.agent.core.AgentFactory;
import com.yunmo.agent.core.AgentSpec;
import com.yunmo.common.enums.AgentType;
import com.yunmo.llm.adapter.MultiProviderChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 爽点设计阶段 — 在 preflight 之后、write_chapter 之前执行。
 * 设计本章的 压抑→铺垫→爆发→兑现→钩子 情绪结构，
 * 注入到 Writer 的 prompt 中。
 */
@Component
public class PleasureBeatStage implements PipelinePlugin {
    @Override public int defaultPriority() { return 40; }

    private static final Logger log = LoggerFactory.getLogger(PleasureBeatStage.class);
    private final AgentFactory agentFactory;

    public PleasureBeatStage(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    @Override
    public String name() {
        return "pleasure_beat";
    }

    @Override
    public StageOutput execute(PipelineState state) {
        Map<AgentType, AgentSpec> specs = agentFactory.createAllSpecs(Map.of());
        AgentSpec spec = specs.get(AgentType.PLEASURE_BEAT);
        MultiProviderChatModel model = agentFactory.createChatModel(spec);

        String chapterPlan = state.get("chapter_plan", String.class);
        String contextText = state.get("context_text", String.class);
        String userFocus = state.get("user_focus", String.class);
        int chapterNumber = state.get("chapter_number", Integer.class);
        int targetWordCount = state.get("target_word_count", Integer.class);

        String prompt = buildPrompt(chapterNumber, chapterPlan, contextText, userFocus, targetWordCount);
        log.info("[PleasureBeat] 开始设计爽点结构 — chapter={}", chapterNumber);

        var genResponse = model.generate(
            SystemMessage.from(spec.systemPrompt()),
            UserMessage.from(prompt)
        );
        String response = genResponse.content().text();
        // 去掉可能的 markdown 代码块标记
        response = response.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pleasure_beat", response);
        data.put("pleasure_beat_raw", response);
        log.info("[PleasureBeat] 爽点设计完成 — chapter={}, length={}", chapterNumber, response.length());
        return StageOutput.withFiles(data, null);
    }

    private String buildPrompt(int chapterNumber, String chapterPlan, String context,
                                String userFocus, int targetWordCount) {
        return String.format("""
            为第%d章设计爽点情绪节奏结构。

            ## 章节大纲
            %s

            ## 前文上下文
            %s

            ## 用户特别指示
            %s

            ## 目标字数
            %d字

            请按爽点闭环公式（压抑→铺垫→爆发→兑现→钩子）设计本章的情绪节奏，
            输出JSON格式。结尾钩子必须用"新信息+危险/机遇预告+情感悬念"公式。
            """, chapterNumber, chapterPlan, truncate(context, 2000),
            userFocus != null && !userFocus.isEmpty() ? userFocus : "无特殊指示",
            targetWordCount);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
