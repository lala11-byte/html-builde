package com.htmlbuilder.project.vo;

/** 后台：按用户汇总的 token 用量 */
public class TokenUserVO {
    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private String status;
    private Long taskCount;
    private Long deletedTaskCount;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    /** 已删除作品关联任务的用量 */
    private Long deletedTokens;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTaskCount() { return taskCount; }
    public void setTaskCount(Long taskCount) { this.taskCount = taskCount; }
    public Long getDeletedTaskCount() { return deletedTaskCount; }
    public void setDeletedTaskCount(Long deletedTaskCount) { this.deletedTaskCount = deletedTaskCount; }
    public Long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Long promptTokens) { this.promptTokens = promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Long completionTokens) { this.completionTokens = completionTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    public Long getDeletedTokens() { return deletedTokens; }
    public void setDeletedTokens(Long deletedTokens) { this.deletedTokens = deletedTokens; }
}
