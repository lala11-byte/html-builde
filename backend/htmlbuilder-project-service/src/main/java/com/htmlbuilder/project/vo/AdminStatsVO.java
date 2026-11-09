package com.htmlbuilder.project.vo;

/** 后台概览统计 */
public class AdminStatsVO {
    private Long userCount;
    private Long projectCount;
    private Long deletedProjectCount;
    private Long publishedCount;
    private Long unpublishedCount;
    private Long taskCount;
    private Long runningTaskCount;
    private Long completedTaskCount;
    private Long failedTaskCount;
    private Long cancelledTaskCount;
    private Long totalTokens;

    public Long getUserCount() { return userCount; }
    public void setUserCount(Long userCount) { this.userCount = userCount; }
    public Long getProjectCount() { return projectCount; }
    public void setProjectCount(Long projectCount) { this.projectCount = projectCount; }
    public Long getDeletedProjectCount() { return deletedProjectCount; }
    public void setDeletedProjectCount(Long deletedProjectCount) { this.deletedProjectCount = deletedProjectCount; }
    public Long getPublishedCount() { return publishedCount; }
    public void setPublishedCount(Long publishedCount) { this.publishedCount = publishedCount; }
    public Long getUnpublishedCount() { return unpublishedCount; }
    public void setUnpublishedCount(Long unpublishedCount) { this.unpublishedCount = unpublishedCount; }
    public Long getTaskCount() { return taskCount; }
    public void setTaskCount(Long taskCount) { this.taskCount = taskCount; }
    public Long getRunningTaskCount() { return runningTaskCount; }
    public void setRunningTaskCount(Long runningTaskCount) { this.runningTaskCount = runningTaskCount; }
    public Long getCompletedTaskCount() { return completedTaskCount; }
    public void setCompletedTaskCount(Long completedTaskCount) { this.completedTaskCount = completedTaskCount; }
    public Long getFailedTaskCount() { return failedTaskCount; }
    public void setFailedTaskCount(Long failedTaskCount) { this.failedTaskCount = failedTaskCount; }
    public Long getCancelledTaskCount() { return cancelledTaskCount; }
    public void setCancelledTaskCount(Long cancelledTaskCount) { this.cancelledTaskCount = cancelledTaskCount; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
}
