package com.htmlbuilder.generator.vo;

/**
 * 生成网站的角色端入口 VO
 */
public class PortalVO {
    /** 端标识，如 teacher / student / admin */
    private String key;
    /** 端名称，如 教师端 / 学生端 / 管理员端 */
    private String name;
    /** 访问路径片段，如 "teacher/"；根选择页为 "" */
    private String path;
    /** 完整访问地址（运行中为 liveUrl，否则为静态预览地址） */
    private String url;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}