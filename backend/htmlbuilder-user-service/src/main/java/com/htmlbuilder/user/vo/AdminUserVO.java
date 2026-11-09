package com.htmlbuilder.user.vo;

/** 后台管理的用户视图 */
public class AdminUserVO {

    private Long id;
    private String nickname;
    private String username;
    private String role;
    private String status;
    private String banReason;
    private String bannedAt;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; }
    public String getBannedAt() { return bannedAt; }
    public void setBannedAt(String bannedAt) { this.bannedAt = bannedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
