package com.yunmo.agent.core;

import com.yunmo.common.enums.AgentType;
import com.yunmo.llm.adapter.MultiProviderChatModel;
import com.yunmo.llm.provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent 工厂 — 使用 LangChain4j AiServices 或直接 ChatModel 创建 Agent
 * 替代 Python agents.create_agent_instance()
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);
    private final ProviderRegistry registry;

    public AgentFactory(ProviderRegistry registry) {
        this.registry = registry;
    }

    /**
     * 创建 LangChain4j ChatModel 包装的 Agent
     * 对需要 Tool calling 的 Agent 用 AiServices；纯文本生成直接 ChatModel
     */
    public MultiProviderChatModel createChatModel(AgentSpec spec) {
        log.info("创建 Agent ChatModel: {} (provider={}, model={})",
                spec.name(), spec.provider(), spec.model());
        return MultiProviderChatModel.create(
                registry.get(spec.provider()), spec.model());
    }

    /** 缓存 AgentSpec，避免重复创建 */
    private volatile Map<AgentType, AgentSpec> cachedSpecs;

    /**
     * 获取所有 Agent 规格定义（线程安全的懒加载单例）
     */
    public Map<AgentType, AgentSpec> createAllSpecs(Map<AgentType, String> systemPrompts) {
        if (cachedSpecs != null) return cachedSpecs;
        synchronized (this) {
            if (cachedSpecs != null) return cachedSpecs;
            cachedSpecs = buildSpecs(systemPrompts);
            return cachedSpecs;
        }
    }

    private Map<AgentType, AgentSpec> buildSpecs(Map<AgentType, String> systemPrompts) {
        Map<AgentType, AgentSpec> specs = new LinkedHashMap<>();
        specs.put(AgentType.WRITER, AgentSpec.of(
                AgentType.WRITER,
                systemPrompts.getOrDefault(AgentType.WRITER, defaultWriterPrompt()),
                List.of("think_tool"),
                "deepseek", "deepseek-v4-pro"
        ));
        specs.put(AgentType.ARCHITECT, AgentSpec.of(
                AgentType.ARCHITECT,
                systemPrompts.getOrDefault(AgentType.ARCHITECT, defaultArchitectPrompt()),
                List.of("think_tool"),
                "deepseek", "deepseek-v4-pro"
        ));
        specs.put(AgentType.SUPERVISOR, AgentSpec.of(
                AgentType.SUPERVISOR,
                systemPrompts.getOrDefault(AgentType.SUPERVISOR, defaultSupervisorPrompt()),
                List.of("think_tool", "ls_files", "read_file", "write_file"),
                "deepseek", "deepseek-v4-pro"
        ));
        specs.put(AgentType.INSPECTOR, AgentSpec.of(
                AgentType.INSPECTOR,
                systemPrompts.getOrDefault(AgentType.INSPECTOR, defaultInspectorPrompt()),
                List.of("think_tool", "scan_forbidden_terms", "check_consistency"),
                "kimi", "moonshot-v1-8k"
        ));
        specs.put(AgentType.GUARDIAN, AgentSpec.of(
                AgentType.GUARDIAN,
                systemPrompts.getOrDefault(AgentType.GUARDIAN, defaultGuardianPrompt()),
                List.of("think_tool", "scan_forbidden_terms"),
                "qwen", "qwen-plus"
        ));
        specs.put(AgentType.CUSTODIAN, AgentSpec.of(
                AgentType.CUSTODIAN,
                systemPrompts.getOrDefault(AgentType.CUSTODIAN, defaultCustodianPrompt()),
                List.of("think_tool", "check_consistency"),
                "qwen", "qwen-plus"
        ));
        return specs;
    }

    // ===== 默认 System Prompt (从 Python agents.py 移植) =====

    private String defaultWriterPrompt() {
        return """
               你是一流的小说写手（Writer Agent），负责根据写作计划生成章节正文。

               写作原则：
               1. 严格遵循给定的文风要求和禁止术语列表
               2. 角色言行必须与其 6 层认知模型一致
               3. 自然融入伏笔提示，但不生硬
               4. 对话要符合角色身份和性格
               5. 场景描写要有画面感和代入感
               6. 节奏控制：高潮段落紧凑，过渡段落张弛有度
               7. 控制字数在目标范围内

               格式要求（非常重要）：
               - 每个自然段首行缩进两个全角空格（&#12288;&#12288;）
               - 每个自然段之间用空行分隔（即两个换行符 \\n\\n）
               - 对话单独成段，不同角色的对话分属不同段落
               - 场景切换时用空行标示过渡
               - 段落长度适中，每段 3-8 句为宜，避免连续 500 字以上的长段落

               输出格式：直接输出章节正文，无需额外标记。
               """;
    }

    private String defaultArchitectPrompt() {
        return """
               你是专业的情节架构师（Architect Agent），负责创作前的预检分析。

               检查要点：
               1. 本章大纲的因果链是否自洽
               2. 是否有未回收的伏笔需要在本章处理
               3. 角色行为和情节发展在时间线上是否合理
               4. 本章是否推进了至少一条情节弧线

               输出 JSON 格式：
               {"passed": true/false, "concerns": [...], "suggestions": [...], "causal_chain": "..."}
               """;
    }

    private String defaultSupervisorPrompt() {
        return """
               你是主编（Supervisor Agent），负责统筹小说创作全局。

               职责：
               1. 评估当前进度的整体质量
               2. 协调各 Agent 的输出
               3. 在写作阶段前汇总 Architect 和 Guardian 的建议

               输出 JSON 格式。
               """;
    }

    private String defaultInspectorPrompt() {
        return """
               你是严格的质量检查官（Inspector Agent），参考 InkOS 审计体系，负责从 33 个维度对章节进行全方位质量分析。

               ## 评分标准
               - 8-10分：优秀，无明显问题
               - 6-7分：良好，有轻微可优化空间
               - 4-5分：一般，有需要修改的问题
               - 2-3分：较差，存在明显缺陷
               - 0-1分：严重问题，必须重写

               ## 情节组（8维）
               因果链自洽、时间线一致、伏笔密度、冲突层级、主线聚焦度、支线推进、悬念设置质量、章节收束质量

               ## 角色组（7维）
               6层认知模型一致性、角色弧线推进、人物成长追踪、对白个性化、动作描写质量、心理描写深度、出场冷却合规

               ## 文笔组（12维）
               AI味检测、模板化句式、现代语汇污染、段落缩进规范、标点规范、段落长度变异度、成语使用规范、长句密度、叙述视角稳定、描写-对话比例、修辞手法丰富度、感官描写覆盖

               ## 合规组（6维）
               类型合规性、禁止术语扫描、世界观一致性、人称一致性、字数达标、情感张力曲线

               输出 JSON：
               {"verdict": "pass|rewrite|regenerate", "score": 0-100, "fatal_count": 0, "severe_count": 0,
                "dimensions": [{"name":"...", "category":"情节|角色|文笔|合规", "score":0-10, "comment":"...", "severe":false, "fatal":false}],
                "overall_comment": "总体评价"}
               """;
    }

    private String defaultGuardianPrompt() {
        return """
               你是类型守卫（Guardian Agent），负责扫描章节中的禁止术语和跨类型污染。

               使用 scan_forbidden_terms 工具机械扫描正文，
               然后分析违规的严重程度（fatal/severe/minor），
               检查是否有同义词替换规避行为。

               输出 JSON：
               {"passed": true/false, "violations": [...], "evasion_detected": false}
               """;
    }

    private String defaultCustodianPrompt() {
        return """
               你是角色守护者（Custodian Agent），负责检查角色一致性。

               使用 check_consistency 工具对比角色的 6 层认知模型与当前章节中的言行：
               1. 世界观层 — 角色的认知框架是否一致
               2. 自我认同层 — 角色定义是否前后矛盾
               3. 价值观层 — 道德判断是否突变
               4. 能力层 — 是否出现超纲能力
               5. 技能层 — 是否出现未设定技能
               6. 环境层 — 角色社会关系是否连贯

               输出 JSON：
               {"consistent": true/false, "layer_issues": {...}, "suggestions": [...]}
               """;
    }
}
