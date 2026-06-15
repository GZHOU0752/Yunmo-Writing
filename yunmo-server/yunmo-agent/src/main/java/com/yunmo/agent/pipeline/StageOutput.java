package com.yunmo.agent.pipeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 阶段输出
 */
public record StageOutput(Map<String, Object> data, Map<String, String> files) {

    public static StageOutput of(String key, Object value) {
        return new StageOutput(Map.of(key, value), Collections.emptyMap());
    }

    public static StageOutput empty() {
        return new StageOutput(Collections.emptyMap(), Collections.emptyMap());
    }

    public static StageOutput withFiles(Map<String, Object> data, Map<String, String> files) {
        return new StageOutput(
                data != null ? data : Collections.emptyMap(),
                files != null ? files : Collections.emptyMap()
        );
    }
}
