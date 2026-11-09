package com.htmlbuilder.generator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class IterateRequest {

    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    @NotBlank(message = "追加需求描述不能为空")
    private String additionalPrompt;

    /** 自定义系统提示词（可选，为空时使用默认提示词） */
    private String customPrompt;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getAdditionalPrompt() { return additionalPrompt; }
    public void setAdditionalPrompt(String additionalPrompt) { this.additionalPrompt = additionalPrompt; }
    public String getCustomPrompt() { return customPrompt; }
    public void setCustomPrompt(String customPrompt) { this.customPrompt = customPrompt; }
}