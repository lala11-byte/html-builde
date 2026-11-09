package com.htmlbuilder.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.common.result.Result;
import com.htmlbuilder.project.service.AdminService;
import com.htmlbuilder.project.vo.AdminProjectVO;
import com.htmlbuilder.project.vo.AdminStatsVO;
import com.htmlbuilder.project.vo.AdminTaskVO;
import com.htmlbuilder.project.vo.TokenTaskVO;
import com.htmlbuilder.project.vo.TokenUserVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理接口：概览、作品管理、社区上下架、token 用量。
 * gateway 会校验管理员角色并注入 X-User-Role / X-User-Id；这里做兜底校验。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public Result<AdminStatsVO> stats(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return Result.success(adminService.stats());
    }

    @GetMapping("/projects")
    public Result<Page<AdminProjectVO>> projects(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long userId,
                                                 @RequestParam(required = false) Integer deleted,
                                                 @RequestParam(required = false) String communityStatus,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "12") int size) {
        requireAdmin(role);
        return Result.success(adminService.listProjects(keyword, userId, deleted, communityStatus, page, size));
    }

    @PutMapping("/projects/{id}/delete")
    public Result<Void> delete(@RequestHeader(value = "X-User-Role", required = false) String role,
                               @RequestHeader(value = "X-User-Id", required = false) Long adminId,
                               @PathVariable Long id) {
        requireAdmin(role);
        adminService.deleteProject(adminId, id);
        return Result.success(null);
    }

    @PutMapping("/projects/{id}/restore")
    public Result<Void> restore(@RequestHeader(value = "X-User-Role", required = false) String role,
                                @PathVariable Long id) {
        requireAdmin(role);
        adminService.restoreProject(id);
        return Result.success(null);
    }

    @PutMapping("/projects/{id}/community")
    public Result<Void> community(@RequestHeader(value = "X-User-Role", required = false) String role,
                                  @PathVariable Long id,
                                  @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(role);
        adminService.setCommunityStatus(id, body == null ? null : body.get("status"));
        return Result.success(null);
    }

    @GetMapping("/tokens/users")
    public Result<Page<TokenUserVO>> tokenUsers(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "12") int size) {
        requireAdmin(role);
        return Result.success(adminService.listTokenUsers(keyword, page, size));
    }

    @GetMapping("/tokens/tasks")
    public Result<Page<TokenTaskVO>> tokenTasks(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                @RequestParam Long userId,
                                                @RequestParam(required = false) Integer deleted,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "12") int size) {
        requireAdmin(role);
        return Result.success(adminService.listTokenTasks(userId, deleted, page, size));
    }

    @GetMapping("/tasks")
    public Result<Page<AdminTaskVO>> tasks(@RequestHeader(value = "X-User-Role", required = false) String role,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "12") int size) {
        requireAdmin(role);
        return Result.success(adminService.listTasks(keyword, status, userId, page, size));
    }

    @PutMapping("/tasks/{id}/cancel")
    public Result<Void> cancelTask(@RequestHeader(value = "X-User-Role", required = false) String role,
                                   @PathVariable Long id) {
        requireAdmin(role);
        adminService.cancelTask(id);
        return Result.success(null);
    }

    @DeleteMapping("/tasks/{id}")
    public Result<Void> deleteTask(@RequestHeader(value = "X-User-Role", required = false) String role,
                                   @PathVariable Long id) {
        requireAdmin(role);
        adminService.deleteTask(id);
        return Result.success(null);
    }

    private void requireAdmin(String role) {
        if (!"admin".equals(role)) {
            throw new BusinessException(403, "无管理员权限");
        }
    }
}
