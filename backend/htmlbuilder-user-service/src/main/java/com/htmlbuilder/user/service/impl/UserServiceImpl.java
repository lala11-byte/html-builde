package com.htmlbuilder.user.service.impl;

import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.common.util.JwtUtil;
import com.htmlbuilder.user.dto.LoginDTO;
import com.htmlbuilder.user.dto.RegisterDTO;
import com.htmlbuilder.user.entity.User;
import com.htmlbuilder.user.mapper.UserMapper;
import com.htmlbuilder.user.service.UserService;
import com.htmlbuilder.user.vo.TokenVO;
import com.htmlbuilder.user.vo.UserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserServiceImpl implements UserService {

    @Value("${jwt.secret:htmlbuilder-default-secret-key-for-jwt-hs256}")
    private String jwtSecret;

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserVO register(RegisterDTO dto) {
        User existing = userMapper.selectByUsername(dto.getUsername());
        if (existing != null) {
            throw new BusinessException(1001, "用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(hashPassword(dto.getPassword()));
        userMapper.insert(user);

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        return vo;
    }

    @Override
    public TokenVO login(LoginDTO dto) {
        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null) {
            throw new BusinessException(1002, "用户名或密码错误");
        }

        String hashedInput = hashPassword(dto.getPassword());
        if (!hashedInput.equals(user.getPasswordHash())) {
            throw new BusinessException(1002, "用户名或密码错误");
        }

        return buildTokenVO(user.getId(), user.getUsername());
    }

    @Override
    public TokenVO refreshToken(String refreshToken) {
        if (!JwtUtil.isTokenValid(refreshToken, jwtSecret)) {
            throw new BusinessException(401, "token已过期，请重新登录");
        }
        Long userId = JwtUtil.getUserIdFromToken(refreshToken, jwtSecret);
        String username = JwtUtil.getUsernameFromToken(refreshToken, jwtSecret);
        return buildTokenVO(userId, username);
    }

    private TokenVO buildTokenVO(Long userId, String username) {
        TokenVO vo = new TokenVO();
        vo.setAccessToken(JwtUtil.generateAccessToken(userId, username, jwtSecret));
        vo.setRefreshToken(JwtUtil.generateRefreshToken(userId, username, jwtSecret));
        return vo;
    }

    private String hashPassword(String password) {
        return DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
    }
}