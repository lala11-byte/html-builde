package com.htmlbuilder.generator.vo;

/**
 * 历史快照视图。列表接口不返回 content，详情接口返回。
 */
public class SnapshotVO {

    private Long id;
    private Long taskId;
    private String page;
    private String branch;
    private Long parentId;
    private String message;
    private String createdAt;
    private String content;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getPage() { return page; }
    public void setPage(String page) { this.page = page; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}