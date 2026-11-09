package com.htmlbuilder.generator.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * MCP 素材缓存：从 registry（MCP 同源）拉取的真实组件源码，
 * 由 AI 适配为原生 HTML/CSS/JS 后落库，避免每次预览都重复拉取与转译。
 */
@TableName("component_material")
public class ComponentMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String registry;
    private String item;
    private String name;
    private String description;
    private String category;
    private String slot;
    private String html;
    private String css;
    private String js;
    /** READY=可用的适配结果；FAILED=上次适配失败，下次请求时重试 */
    private String status;
    private String error;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegistry() { return registry; }
    public void setRegistry(String registry) { this.registry = registry; }
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }
    public String getCss() { return css; }
    public void setCss(String css) { this.css = css; }
    public String getJs() { return js; }
    public void setJs(String js) { this.js = js; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}