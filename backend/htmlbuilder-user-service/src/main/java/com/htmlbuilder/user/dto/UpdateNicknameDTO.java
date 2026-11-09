package com.htmlbuilder.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateNicknameDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度2-20字符")
    private String nickname;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
