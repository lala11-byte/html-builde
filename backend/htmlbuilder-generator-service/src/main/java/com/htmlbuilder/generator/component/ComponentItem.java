package com.htmlbuilder.generator.component;

/**
 * 组件库条目：纯 HTML/CSS/JS 片段，可直接插入或替换页面元素。
 * kind=style 表示只提供视觉样式（把 class 与 data-hb-* 附到选中元素上），block 表示完整结构区块。
 */
public class ComponentItem {

    private String id;
    private String name;
    private String category;
    private String description;
    private String kind;
    /** 区块用途：top（页面顶部）/ bottom（页面末尾）/ flow（内容段，按顺序插入） */
    private String slot;
    /**
     * 样式作用域：target 表示美学配方，CSS 里的 & 会替换成选中元素的选择器，
     * 只改变选中元素外观、不注入任何平台内容；为空表示传统的「附加 class」样式组件。
     */
    private String scope;
    /** 素材来源：shadcn 表示从 MCP registry 拉取的真实组件（按需适配），为空表示内置配方 */
    private String source;
    private String html;
    private String css;
    private String js;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }
    public String getCss() { return css; }
    public void setCss(String css) { this.css = css; }
    public String getJs() { return js; }
    public void setJs(String js) { this.js = js; }
}