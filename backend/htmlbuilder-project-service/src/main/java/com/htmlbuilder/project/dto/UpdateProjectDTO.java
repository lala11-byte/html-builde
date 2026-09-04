package com.htmlbuilder.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProjectDTO {
    @NotBlank(message = "项目名称不能为空")
    @Size(min = 1, max = 50, message = "项目名称长度1-50字符")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}