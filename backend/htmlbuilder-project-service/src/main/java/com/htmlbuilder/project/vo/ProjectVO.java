package com.htmlbuilder.project.vo;

import java.time.LocalDateTime;

public class ProjectVO {
    private Long id;
    private String name;
    private Integer pageCount;
    private String screenshotUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 是否已被平台/用户删除（软删除） */
    private Boolean deleted;
    private LocalDateTime deletedAt;
    /** 社区状态：published / unpublished */
    private String communityStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public String getCommunityStatus() { return communityStatus; }
    public void setCommunityStatus(String communityStatus) { this.communityStatus = communityStatus; }
}
