package com.yunmo.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "reference_materials", indexes = {
    @Index(columnList = "novel_id, created_at")
})
public class ReferenceMaterial extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    /** 原始文件名 */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 分块数量 */
    @Column(name = "chunk_count")
    private Integer chunkCount;

    /** 状态：indexing | ready | error */
    @Column(length = 32)
    private String status;

    // ===== 智能触发字段 =====

    /** 触发模式：MANUAL(手动) | AUTO(始终激活) | KEYWORD(关键词触发) */
    @Column(name = "trigger_mode", length = 16)
    private String triggerMode = "MANUAL";

    /** 触发关键词（逗号分隔），仅 KEYWORD 模式生效 */
    @Column(name = "trigger_keywords", columnDefinition = "TEXT")
    private String triggerKeywords;

    /** 激活后冷却章节数（0 = 每次触发都激活） */
    @Column(name = "cooldown_chapters")
    private Integer cooldownChapters = 0;

    /** 优先级（数值越大越优先注入，默认 0） */
    @Column
    private Integer priority = 0;

    /** 上次激活的章节号 */
    @Column(name = "last_activated_chapter")
    private Integer lastActivatedChapter;
}
