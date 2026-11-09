package com.htmlbuilder.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户-项目关联（user_db.user_project）
 * 项目本体仍由 project_db 维护，这里只保存用户维度的归属角色与个性化标记
 */
@TableName("user_project")
public class UserProject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    /** OWNER / EDITOR / VIEWER */
    private String role;
    private Boolean starred;
    private Boolean pinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Boolean getStarred() { return starred; }
    public void setStarred(Boolean starred) { this.starred = starred; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
