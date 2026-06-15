package com.yunmo.agent.pipeline;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流水线全局状态 — 替代 Python LangGraph AgentState
 * 线程安全的可变上下文容器，Agent 间通过此对象传递结构化数据
 */
public class PipelineState {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    /** 虚拟文件系统 — 替代 Python files dict + file_reducer */
    private final ConcurrentHashMap<String, String> files = new ConcurrentHashMap<>();

    /**
     * 获取状态字段（带类型转换）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return type.cast(data.get(key));
    }

    /**
     * 获取状态字段（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
        Object value = data.get(key);
        return value != null ? type.cast(value) : defaultValue;
    }

    /**
     * 设置状态字段
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 批量合并状态
     */
    public void merge(Map<String, Object> newData) {
        data.putAll(newData);
    }

    /**
     * 写入虚拟文件（右侧优先合并语义，等价 Python file_reducer）
     */
    public void putFile(String path, String content) {
        files.put(path, content);
    }

    /**
     * 批量合并虚拟文件
     */
    public void mergeFiles(Map<String, String> newFiles) {
        files.putAll(newFiles);
    }

    /**
     * 读取虚拟文件
     */
    public String readFile(String path) {
        return files.get(path);
    }

    /**
     * 列出虚拟文件
     */
    public Map<String, String> listFiles() {
        return Collections.unmodifiableMap(files);
    }

    /**
     * 获取完整状态快照（只读）
     */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(data);
    }
}
