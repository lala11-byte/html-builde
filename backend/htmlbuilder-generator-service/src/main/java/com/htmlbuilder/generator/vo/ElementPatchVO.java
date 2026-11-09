package com.htmlbuilder.generator.vo;

public class ElementPatchVO {

    private String page;
    private String selector;
    private String mode;
    private String componentId;
    private String summary;
    private String previewUrl;

    public String getPage() { return page; }
    public void setPage(String page) { this.page = page; }
    public String getSelector() { return selector; }
    public void setSelector(String selector) { this.selector = selector; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getComponentId() { return componentId; }
    public void setComponentId(String componentId) { this.componentId = componentId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
}