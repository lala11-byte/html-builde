package com.htmlbuilder.generator.dto;

import jakarta.validation.constraints.NotBlank;

public class GenerateRequest {

    @NotBlank(message = "需求描述不能为空")
    private String prompt;

    private Long pageId;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }
}