package com.htmlbuilder.project.vo;

/** 后台：生成任务 token 用量明细 */
public class TokenTaskVO {
    private Long id;
    private Long userId;
    private Long projectId;
    private String projectName;
    private String prompt;
    private String status;
    private Integer modelCalls;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    /** 关联作品是否已被删除 */
    private Boolean deleted;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getModelCalls() { return modelCalls; }
    public void setModelCalls(Integer modelCalls) { this.modelCalls = modelCalls; }
    public Long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Long promptTokens) { this.promptTokens = promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Long completionTokens) { this.completionTokens = completionTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
