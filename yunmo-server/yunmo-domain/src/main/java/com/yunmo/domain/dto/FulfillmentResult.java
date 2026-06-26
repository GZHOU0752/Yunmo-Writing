package com.yunmo.domain.dto;

import java.util.List;

/**
 * 合同履约结果 — 检查章节是否完成了合同要求
 *
 * 包含节点覆盖对比、禁区扫描结果、履约分数
 * 使用 Record 保证不可变性
 *
 * @param contractId          合同ID
 * @param plannedNodes        计划覆盖节点数
 * @param coveredNodes        实际覆盖节点数
 * @param missedNodes         遗漏节点数
 * @param extraNodes          额外节点数（不在合同中的内容）
 * @param missedDescriptions  遗漏节点描述列表
 * @param forbiddenViolations 禁区违规描述列表
 * @param fulfillmentScore    履约分数 0-100（低于60为不通过）
 * @param passed              是否通过（分数>=60且无禁区违规）
 */
public record FulfillmentResult(
    String contractId,
    int plannedNodes,
    int coveredNodes,
    int missedNodes,
    int extraNodes,
    List<String> missedDescriptions,
    List<String> forbiddenViolations,
    double fulfillmentScore,
    boolean passed
) {
    /**
     * 创建快速通过结果（无合同或合同为空时使用）
     */
    public static FulfillmentResult pass(String contractId) {
        return new FulfillmentResult(
            contractId, 0, 0, 0, 0,
            List.of(), List.of(), 100.0, true
        );
    }

    /**
     * 计算节点覆盖率（百分比）
     */
    public double coverageRate() {
        if (plannedNodes == 0) return 100.0;
        return (double) coveredNodes / plannedNodes * 100.0;
    }
}
