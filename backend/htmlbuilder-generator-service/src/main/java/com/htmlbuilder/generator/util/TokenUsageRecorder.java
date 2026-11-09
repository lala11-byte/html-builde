package com.htmlbuilder.generator.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务级 token 用量记录器。
 *
 * 生成任务在独立线程中同步执行 langchain4j graph，模型调用也发生在同一线程，
 * 因此用 ThreadLocal 关联当前 taskId；每次 LLM 调用把 usage 累加到内存表，
 * 任务结束时由 GeneratorService 统一写回 generation_task。
 */
public final class TokenUsageRecorder {

    private TokenUsageRecorder() {
    }

    /** 当前线程正在执行的任务 ID */
    private static final ThreadLocal<Long> CURRENT_TASK = new ThreadLocal<>();
    /** taskId -> [调用次数, promptTokens, completionTokens, totalTokens] */
    private static final Map<Long, long[]> USAGE = new ConcurrentHashMap<>();

    public static void begin(Long taskId) {
        if (taskId == null) {
            return;
        }
        USAGE.put(taskId, new long[4]);
        CURRENT_TASK.set(taskId);
    }

    public static void clear() {
        CURRENT_TASK.remove();
    }

    /** 记录一次模型调用的真实用量 */
    public static void record(long promptTokens, long completionTokens) {
        Long taskId = CURRENT_TASK.get();
        if (taskId == null) {
            return;
        }
        long[] usage = USAGE.computeIfAbsent(taskId, k -> new long[4]);
        synchronized (usage) {
            usage[0]++;
            usage[1] += Math.max(0, promptTokens);
            usage[2] += Math.max(0, completionTokens);
            usage[3] += Math.max(0, promptTokens) + Math.max(0, completionTokens);
        }
    }

    /**
     * 优先使用响应中的 usage；没有 usage 时按字符数估算（中英混合约 3 字符/token）。
     */
    public static void recordResponse(Object usage, String promptText, String outputText) {
        long[] parsed = usageOf(usage);
        if (parsed != null && (parsed[0] > 0 || parsed[1] > 0)) {
            record(parsed[0], parsed[1]);
            return;
        }
        record(estimate(promptText), estimate(outputText));
    }

    /** 从 usage 对象读取 prompt/completion token，兼容 OpenAI 与 Anthropic 字段名 */
    public static long[] usageOf(Object usage) {
        if (!(usage instanceof Map<?, ?> map)) {
            return null;
        }
        Object prompt = map.containsKey("prompt_tokens") ? map.get("prompt_tokens") : map.get("input_tokens");
        Object completion = map.containsKey("completion_tokens") ? map.get("completion_tokens")
                : map.get("output_tokens");
        if (prompt == null && completion == null) {
            return null;
        }
        return new long[]{toLong(prompt), toLong(completion)};
    }

    /** 取出任务累计用量并清除，供任务结束时落库 */
    public static long[] finish(Long taskId) {
        if (taskId == null) {
            return null;
        }
        return USAGE.remove(taskId);
    }

    private static long estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, Math.round(text.length() / 3.0));
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
