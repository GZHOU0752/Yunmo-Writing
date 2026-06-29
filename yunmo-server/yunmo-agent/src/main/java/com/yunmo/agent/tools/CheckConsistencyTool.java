package com.yunmo.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 角色一致性检查工具 — 配合 Custodian Agent 使用
 * 检查角色的 6 层认知模型与当前章节中的言行是否一致
 */
@Component
public class CheckConsistencyTool {

    /**
     * 检查角色在章节中的一致性
     * 6层认知模型：世界观层、自我认同层、价值观层、能力层、技能层、环境层
     */
    @Tool("检查角色在章节中的行为是否与角色设定一致，返回一致性分析结果。" +
          "检查6层认知模型：世界观层、自我认同层、价值观层、能力层、技能层、环境层。")
    public String checkConsistency(
            @P("角色设定描述（JSON格式，包含name、role、personality、abilities等字段）") String characterProfile,
            @P("待检查的章节正文内容") String chapterContent) {

        // 这是一个机械检查工具，实际一致性分析由 LLM 在 prompt 中完成
        // 此工具提供结构化的检查框架
        return """
                {
                  "check_layers": [
                    {"layer": "世界观层", "description": "角色的认知框架是否一致"},
                    {"layer": "自我认同层", "description": "角色定义是否前后矛盾"},
                    {"layer": "价值观层", "description": "道德判断是否突变"},
                    {"layer": "能力层", "description": "是否出现超纲能力"},
                    {"layer": "技能层", "description": "是否出现未设定技能"},
                    {"layer": "环境层", "description": "角色社会关系是否连贯"}
                  ],
                  "instruction": "请逐层分析角色在章节中的表现是否与设定一致，输出JSON格式的检查结果"
                }
                """;
    }
}
