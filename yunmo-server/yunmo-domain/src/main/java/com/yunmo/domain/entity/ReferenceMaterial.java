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
}
