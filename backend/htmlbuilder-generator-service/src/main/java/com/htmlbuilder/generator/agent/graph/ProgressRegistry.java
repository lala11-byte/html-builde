package com.htmlbuilder.generator.agent.graph;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * progressCallback 注册表
 * callback 不可序列化，不能放入 AgentState，通过 taskId 在此注册表查找
 */
@Component
public class ProgressRegistry {

    private final Map<Long, Consumer<String>> callbacks = new ConcurrentHashMap<>();

    public void register(Long taskId, Consumer<String> callback) {
        callbacks.put(taskId, callback);
    }

    public Consumer<String> get(Long taskId) {
        return callbacks.get(taskId);
    }

    public void unregister(Long taskId) {
        callbacks.remove(taskId);
    }
}
