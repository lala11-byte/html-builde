package com.htmlbuilder.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.common.result.Result;
import com.htmlbuilder.user.service.UserService;
import com.htmlbuilder.user.vo.AdminUserVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理：用户列表与封禁/解封。
 * 管理权限由 gateway 校验 JWT 角色后注入 X-User-Role，这里再做一次兜底校验，
 * 防止直接访问服务端口绕过网关。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<Page<AdminUserVO>> list(@RequestHeader(value = "X-User-Role", required = false) String role,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "12") int size) {
        requireAdmin(role);
        return Result.success(userService.listUsers(keyword, status, page, size));
    }

    @PutMapping("/{id}/ban")
    public Result<Void> ban(@RequestHeader(value = "X-User-Role", required = false) String role,
                            @PathVariable Long id,
                            @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(role);
        userService.banUser(id, body == null ? null : body.get("reason"));
        return Result.success(null);
    }

    @PutMapping("/{id}/unban")
    public Result<Void> unban(@RequestHeader(value = "X-User-Role", required = false) String role,
                              @PathVariable Long id) {
        requireAdmin(role);
        userService.unbanUser(id);
        return Result.success(null);
    }

    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@RequestHeader(value = "X-User-Role", required = false) String role,
                                   @RequestHeader(value = "X-User-Id", required = false) Long adminId,
                                   @PathVariable Long id,
                                   @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(role);
        userService.updateRole(adminId, id, body == null ? null : body.get("role"));
        return Result.success(null);
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@RequestHeader(value = "X-User-Role", required = false) String role,
                                      @PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(role);
        userService.resetPassword(id, body == null ? null : body.get("password"));
        return Result.success(null);
    }

    private void requireAdmin(String role) {
        if (!"admin".equals(role)) {
            throw new BusinessException(403, "无管理员权限");
        }
    }
}
