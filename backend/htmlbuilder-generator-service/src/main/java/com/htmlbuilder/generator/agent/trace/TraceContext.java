package com.htmlbuilder.generator.agent.trace;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * ThreadLocal 追踪上下文栈。
 * ForkJoinPool 的 worker 线程在执行 CompletableFuture.supplyAsync 时，
 * push() 和后续的 LLM 调用（peekRunId()）发生在同一线程，
 * 因此 ThreadLocal 是正确的选择。
 *
 * 层级结构：
 *   trace (website-generate-{id})     ← GeneratorService 压入
 *     → node (requirement_analysis)   ← TracingNodeAction 压入
 *       → llm (fast-chat)            ← 模型 chat() 调用时读取
 *       → llm (stream-chat)          ← 模型 streamChat() 调用时读取
 *     → node (planning)
 *       → llm (fast-chat)
 *     ...
 */
public class TraceContext {

    private static final ThreadLocal<Deque<String>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(String runId) {
        STACK.get().push(runId);
    }

    public static String pop() {
        Deque<String> stack = STACK.get();
        return stack.isEmpty() ? null : stack.pop();
    }

    public static String peekRunId() {
        Deque<String> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /** 清理当前线程的栈（节点执行完成后调用） */
    public static void clearCurrentThread() {
        STACK.remove();
    }
}