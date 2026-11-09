package com.htmlbuilder.user.vo;

public class UserVO {

    private Long id;
    private String nickname;
    private String username;
    private String role;
    private String status;
    private String banReason;

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
}
