package com.yunmo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 断点续写服务 — 每个管线阶段完成后持久化状态
 * 生成中断后可从上一个完成的阶段恢复
 */
@Service
public class CheckpointService {

    private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);
    private static final String CHECKPOINT_DIR = "data/checkpoints";
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public record Checkpoint(
        String novelId,
        int chapterNumber,
        String lastStage,
        int stageIndex,
        int totalStages,
        String streamedText,
        LocalDateTime savedAt
    ) {}

    /** 保存断点 */
    public void save(String novelId, int chapterNumber, String stageName,
                     int stageIndex, int totalStages, String streamedText) {
        try {
            Path file = checkpointPath(novelId, chapterNumber);
            Files.createDirectories(file.getParent());
            var cp = new Checkpoint(novelId, chapterNumber, stageName,
                stageIndex, totalStages,
                streamedText != null && streamedText.length() > 5000
                    ? streamedText.substring(streamedText.length() - 5000) : streamedText,
                LocalDateTime.now());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), cp);
        } catch (IOException e) {
            log.debug("断点保存失败: {}", e.getMessage());
        }
    }

    /** 检查是否存在未完成的断点 */
    public Checkpoint load(String novelId, int chapterNumber) {
        try {
            Path file = checkpointPath(novelId, chapterNumber);
            if (Files.exists(file)) {
                var cp = mapper.readValue(file.toFile(), Checkpoint.class);
                // 超过 2 小时的断点视为过期
                if (cp.savedAt().plusHours(2).isBefore(LocalDateTime.now())) {
                    Files.deleteIfExists(file);
                    return null;
                }
                return cp;
            }
        } catch (Exception e) {
            log.debug("断点读取失败: {}", e.getMessage());
        }
        return null;
    }

    /** 清除断点（生成正常完成后调用） */
    public void clear(String novelId, int chapterNumber) {
        try {
            Files.deleteIfExists(checkpointPath(novelId, chapterNumber));
        } catch (IOException e) {
            // ignore
        }
    }

    private Path checkpointPath(String novelId, int chapterNumber) {
        return Path.of(CHECKPOINT_DIR, novelId, "ch_" + chapterNumber + ".json");
    }
}
