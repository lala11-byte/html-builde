package com.htmlbuilder.generator.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 页面快照：每次页面内容变动（元素修改 / 恢复 / 建分支）都会写入一条记录。
 * branch 默认 main，parent_id 指向同分支上的上一个快照，形成可回溯的版本链。
 */
@TableName("page_snapshot")
public class PageSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String page;
    private String branch;
    private Long parentId;
    private String message;
    private String content;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

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
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}