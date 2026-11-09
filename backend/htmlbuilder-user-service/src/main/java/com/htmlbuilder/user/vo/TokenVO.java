package com.htmlbuilder.user.vo;

public class TokenVO {

    private String accessToken;
    private String refreshToken;
    /** 登录用户的角色，前端据此显示管理入口 */
    private String role;
    private String status;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
