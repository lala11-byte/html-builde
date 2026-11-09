package com.htmlbuilder.project.vo;

/** 后台：生成任务视图 */
public class AdminTaskVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private Long projectId;
    private String projectName;
    private Long pageId;
    private String prompt;
    private String status;
    private String errorMessage;
    private Integer modelCalls;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Boolean projectDeleted;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getModelCalls() { return modelCalls; }
    public void setModelCalls(Integer modelCalls) { this.modelCalls = modelCalls; }
    public Long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Long promptTokens) { this.promptTokens = promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Long completionTokens) { this.completionTokens = completionTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    public Boolean getProjectDeleted() { return projectDeleted; }
    public void setProjectDeleted(Boolean projectDeleted) { this.projectDeleted = projectDeleted; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}