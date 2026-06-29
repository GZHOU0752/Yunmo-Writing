package com.yunmo.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 思考工具 — 让 Agent 在行动前先整理思路
 * 所有 Agent 都可以使用此工具进行思维链推理
 */
@Component
public class ThinkTool {

    @Tool("在做出决策或输出之前，先整理你的思路。写下你的分析过程、考虑的因素和推理步骤。")
    public String think(
            @P("你的思考内容，包括分析过程、考虑因素、推理步骤") String thought) {
        // 思考工具不产生副作用，仅用于结构化推理
        // 返回确认信息让 Agent 知道思考已记录
        return "思考已记录。请继续你的分析或输出。";
    }
}
