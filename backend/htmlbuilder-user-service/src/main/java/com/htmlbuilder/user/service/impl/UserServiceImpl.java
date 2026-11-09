package com.htmlbuilder.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.common.util.JwtUtil;
import com.htmlbuilder.user.dto.LoginDTO;
import com.htmlbuilder.user.dto.RegisterDTO;
import com.htmlbuilder.user.entity.User;
import com.htmlbuilder.user.mapper.UserMapper;
import com.htmlbuilder.user.service.UserService;
import com.htmlbuilder.user.vo.AdminUserVO;
import com.htmlbuilder.user.vo.TokenVO;
import com.htmlbuilder.user.vo.UserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UserServiceImpl implements UserService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        user.setNickname(dto.getNickname());
        user.setUsername(dto.getUsername());
        user.setPasswordHash(hashPassword(dto.getPassword()));
        user.setRole("user");
        user.setStatus("active");
        userMapper.insert(user);

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
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

        // 被封禁的账号禁止登录，并给出具体原因
        if ("banned".equals(user.getStatus())) {
            String reason = user.getBanReason();
            throw new BusinessException(1004, "账号已被封禁"
                    + (reason == null || reason.isBlank() ? "" : "：" + reason));
        }

        return buildTokenVO(user);
    }

    @Override
    public TokenVO refreshToken(String refreshToken) {
        if (!JwtUtil.isTokenValid(refreshToken, jwtSecret)) {
            throw new BusinessException(401, "token已过期，请重新登录");
        }
        Long userId = JwtUtil.getUserIdFromToken(refreshToken, jwtSecret);
        User user = requireUser(userId);
        // 刷新时重新校验封禁状态，避免封禁后仍能续期
        if ("banned".equals(user.getStatus())) {
            throw new BusinessException(1004, "账号已被封禁，请联系管理员");
        }
        return buildTokenVO(user);
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        return toUserVO(requireUser(userId));
    }

    @Override
    public UserVO updateNickname(Long userId, String nickname) {
        User user = requireUser(userId);
        user.setNickname(nickname);
        userMapper.updateById(user);
        return toUserVO(user);
    }

    @Override
    public Page<AdminUserVO> listUsers(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw));
        }
        if (status != null && !status.isBlank()) {
            qw.eq(User::getStatus, status);
        }
        qw.orderByDesc(User::getId);

        Page<User> source = userMapper.selectPage(new Page<>(page, size), qw);
        Page<AdminUserVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::toAdminVO).toList());
        return result;
    }

    @Override
    public void banUser(Long userId, String reason) {
        User user = requireUser(userId);
        if ("admin".equals(user.getRole())) {
            throw new BusinessException(1005, "不能封禁管理员账号");
        }
        user.setStatus("banned");
        user.setBanReason(reason == null || reason.isBlank() ? "违反平台规定" : reason.trim());
        user.setBannedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void unbanUser(Long userId) {
        User user = requireUser(userId);
        user.setStatus("active");
        user.setBanReason(null);
        user.setBannedAt(null);
        userMapper.updateById(user);
    }

    @Override
    public void updateRole(Long adminId, Long userId, String role) {
        if (!"admin".equals(role) && !"user".equals(role)) {
            throw new BusinessException(1006, "角色只能是 admin 或 user");
        }
        User user = requireUser(userId);
        if (adminId != null && adminId.equals(userId)) {
            throw new BusinessException(1006, "不能修改自己的角色");
        }
        if (role.equals(user.getRole())) {
            return;
        }
        if ("admin".equals(user.getRole())) {
            Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "admin"));
            if (adminCount != null && adminCount <= 1) {
                throw new BusinessException(1006, "至少需要保留一名管理员");
            }
        }
        user.setRole(role);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long userId, String password) {
        if (password == null || password.trim().length() < 6) {
            throw new BusinessException(1006, "密码长度至少 6 位");
        }
        User user = requireUser(userId);
        user.setPasswordHash(hashPassword(password));
        userMapper.updateById(user);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(1002, "用户不存在");
        }
        return user;
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setBanReason(user.getBanReason());
        return vo;
    }

    private AdminUserVO toAdminVO(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setBanReason(user.getBanReason());
        vo.setBannedAt(user.getBannedAt() == null ? "" : user.getBannedAt().format(TIME_FMT));
        vo.setCreatedAt(user.getCreatedAt() == null ? "" : user.getCreatedAt().format(TIME_FMT));
        return vo;
    }

    private TokenVO buildTokenVO(User user) {
        TokenVO vo = new TokenVO();
        vo.setAccessToken(JwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole(), jwtSecret));
        vo.setRefreshToken(JwtUtil.generateRefreshToken(user.getId(), user.getUsername(), jwtSecret));
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }

    private String hashPassword(String password) {
        return DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
    }
}
