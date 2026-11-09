package com.htmlbuilder.generator.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 元素级修改请求：针对预览页中选中的某个元素做 AI 修改或组件替换/插入。
 * mode 取值：ai（默认，按 instruction 让模型改写）、replace / insertAfter / append（配合 componentId）。
 */
public class ElementPatchRequest {

    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /** 页面路径，来自 bridge 回传的 location.pathname，如 /teacher/index.html */
    private String page;

    /** bridge 生成的 CSS 选择器 */
    private String selector;

    /** 选中元素标签名（辅助信息） */
    private String tag;

    /** 选中元素当前 HTML（辅助信息，减少模型重新定位成本） */
    private String outerHTML;

    /** 自然语言修改指令（ai 模式下必填） */
    private String instruction;

    /** ai / replace / insertAfter / append / style / position */
    private String mode;

    /** 组件库组件 ID（replace / insertAfter / append / style 模式下必填） */
    private String componentId;

    /** 目标版本分支，缺省 main */
    private String branch;

    /** position 模式下的内联样式（如 position:absolute;left:120px;top:40px） */
    private String style;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getPage() { return page; }
    public void setPage(String page) { this.page = page; }
    public String getSelector() { return selector; }
    public void setSelector(String selector) { this.selector = selector; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getOuterHTML() { return outerHTML; }
    public void setOuterHTML(String outerHTML) { this.outerHTML = outerHTML; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getComponentId() { return componentId; }
    public void setComponentId(String componentId) { this.componentId = componentId; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
}