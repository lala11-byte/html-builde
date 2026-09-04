package com.htmlbuilder.generator.dto;

import jakarta.validation.constraints.NotBlank;

public class GenerateRequest {

    @NotBlank(message = "需求描述不能为空")
    private String prompt;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
}