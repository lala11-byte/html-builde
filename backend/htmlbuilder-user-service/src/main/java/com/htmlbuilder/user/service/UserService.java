package com.htmlbuilder.user.service;

import com.htmlbuilder.user.dto.LoginDTO;
import com.htmlbuilder.user.dto.RegisterDTO;
import com.htmlbuilder.user.vo.TokenVO;
import com.htmlbuilder.user.vo.UserVO;

public interface UserService {

    UserVO register(RegisterDTO dto);

    TokenVO login(LoginDTO dto);

    TokenVO refreshToken(String refreshToken);
}