package com.htmlbuilder.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class BindProjectDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @Pattern(regexp = "OWNER|EDITOR|VIEWER", message = "角色仅支持 OWNER/EDITOR/VIEWER")
    private String role;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
