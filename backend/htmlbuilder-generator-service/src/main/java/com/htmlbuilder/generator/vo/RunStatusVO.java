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
    /** 各角色端入口（单端口多入口，如 teacher/、student/、admin/） */
    private List<PortalVO> portals;

    public Boolean getRunning() { return running; }
    public void setRunning(Boolean running) { this.running = running; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }
    public List<PortalVO> getPortals() { return portals; }
    public void setPortals(List<PortalVO> portals) { this.portals = portals; }
}
