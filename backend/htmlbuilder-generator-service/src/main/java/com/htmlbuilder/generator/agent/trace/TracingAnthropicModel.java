package com.htmlbuilder.generator.agent.trace;

/**
 * 已废弃：追踪现已通过 TracingNodeAction + TraceContext 实现节点级追踪。
 * 如需每个 LLM 调用作为子 run，可直接在 AnthropicChatLanguageModel.doChat()
 * 和 streamChat() 中注入 LangSmithTracer + TraceContext.peek()。
 */
// 保留文件占位，未来如需细化 LLM 级追踪可在此扩展