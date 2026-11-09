package com.htmlbuilder.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htmlbuilder.user.dto.LoginDTO;
import com.htmlbuilder.user.dto.RegisterDTO;
import com.htmlbuilder.user.vo.AdminUserVO;
import com.htmlbuilder.user.vo.TokenVO;
import com.htmlbuilder.user.vo.UserVO;

public interface UserService {

    UserVO register(RegisterDTO dto);

    TokenVO login(LoginDTO dto);

    TokenVO refreshToken(String refreshToken);

    UserVO getUserInfo(Long userId);

    UserVO updateNickname(Long userId, String nickname);

    /** 后台：用户列表（支持用户名/昵称搜索与状态筛选） */
    Page<AdminUserVO> listUsers(String keyword, String status, int page, int size);

    /** 后台：封禁用户 */
    void banUser(Long userId, String reason);

    /** 后台：解封用户 */
    void unbanUser(Long userId);

    /** 后台：调整用户角色（admin / user） */
    void updateRole(Long adminId, Long userId, String role);

    /** 后台：重置用户密码 */
    void resetPassword(Long userId, String password);
}
