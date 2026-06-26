package com.yunmo.domain.dto;

import com.yunmo.domain.entity.CharacterState;
import com.yunmo.domain.entity.EmotionalDebt;
import com.yunmo.domain.entity.ForeshadowTracking;
import com.yunmo.domain.entity.WorldRule;

import java.util.List;
import java.util.Map;

/**
 * 章节提交数据对象 — 单章生成完成后的闭环回写数据包
 * 包含该章提取的所有状态变更、伏笔变化、世界规则更新、情感债变化
 * 使用 Record 以保证不可变性，避免回写过程中被意外修改
 *
 * @param novelId              小说ID
 * @param chapterNumber        章节号
 * @param chapterContent       章节原文
 * @param metadata             生成元数据（模型、管线阶段、质量分等）
 * @param characterStates      角色状态变更列表
 * @param foreshadowChanges    伏笔变化列表
 * @param worldRuleChanges     世界规则变更列表
 * @param emotionalDebtChanges 情感债变更列表
 * @param chapterSummary       章节摘要
 * @param characterStateCount  角色状态变更数
 * @param hookCount            伏笔变更数
 * @param ruleCount            世界规则变更数
 * @param debtCount            情感债变更数
 */
public record ChapterCommit(
    String novelId,
    int chapterNumber,
    String chapterContent,
    Map<String, Object> metadata,
    List<CharacterState> characterStates,
    List<ForeshadowTracking> foreshadowChanges,
    List<WorldRule> worldRuleChanges,
    List<EmotionalDebt> emotionalDebtChanges,
    String chapterSummary,
    int characterStateCount,
    int hookCount,
    int ruleCount,
    int debtCount
) {}
