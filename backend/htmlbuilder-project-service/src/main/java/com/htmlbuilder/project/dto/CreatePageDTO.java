package com.htmlbuilder.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreatePageDTO {
    @NotBlank(message = "页面标题不能为空")
    @Size(min = 1, max = 100, message = "页面标题长度1-100字符")
    private String title;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}