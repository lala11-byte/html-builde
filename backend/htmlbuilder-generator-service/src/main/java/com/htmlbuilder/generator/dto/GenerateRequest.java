package com.htmlbuilder.generator.dto;

import jakarta.validation.constraints.NotBlank;

public class GenerateRequest {

    @NotBlank(message = "需求描述不能为空")
    private String prompt;

    private Long projectId;

    private Long pageId;

    /** 自定义系统提示词（可选，为空时使用默认提示词） */
    private String customPrompt;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }
    public String getCustomPrompt() { return customPrompt; }
    public void setCustomPrompt(String customPrompt) { this.customPrompt = customPrompt; }
}