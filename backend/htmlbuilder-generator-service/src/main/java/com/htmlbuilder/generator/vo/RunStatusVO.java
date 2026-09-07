package com.htmlbuilder.generator.vo;

import java.util.List;

/**
 * 本地运行状态 VO
 */
public class RunStatusVO {
    private Boolean running;
    private Integer port;
    private String url;
    private List<String> logs;

    public Boolean getRunning() { return running; }
    public void setRunning(Boolean running) { this.running = running; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }
}
