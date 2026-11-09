package com.htmlbuilder.user.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 修改用户对某项目的关联信息（字段为空表示不改动）
 */
public class ProjectRelationDTO {

    @Pattern(regexp = "OWNER|EDITOR|VIEWER", message = "角色仅支持 OWNER/EDITOR/VIEWER")
    private String role;
    private Boolean starred;
    private Boolean pinned;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Boolean getStarred() { return starred; }
    public void setStarred(Boolean starred) { this.starred = starred; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
}
