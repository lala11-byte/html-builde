package com.htmlbuilder.user.controller;

import com.htmlbuilder.common.result.Result;
import com.htmlbuilder.user.dto.BindProjectDTO;
import com.htmlbuilder.user.dto.ProjectRelationDTO;
import com.htmlbuilder.user.service.UserProjectService;
import com.htmlbuilder.user.vo.UserProjectVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户维度的项目管理（归属角色、收藏、置顶）
 */
@RestController
@RequestMapping("/api/v1/users/me/projects")
public class UserProjectController {

    private final UserProjectService userProjectService;

    public UserProjectController(UserProjectService userProjectService) {
        this.userProjectService = userProjectService;
    }

    @GetMapping
    public Result<List<UserProjectVO>> list(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userProjectService.listMine(userId));
    }

    @PostMapping
    public Result<UserProjectVO> bind(@RequestHeader("X-User-Id") Long userId,
                                     @Valid @RequestBody BindProjectDTO dto) {
        return Result.success(userProjectService.bind(userId, dto));
    }

    @PutMapping("/{projectId}")
    public Result<UserProjectVO> update(@RequestHeader("X-User-Id") Long userId,
                                       @PathVariable Long projectId,
                                       @Valid @RequestBody ProjectRelationDTO dto) {
        return Result.success(userProjectService.update(userId, projectId, dto));
    }

    @DeleteMapping("/{projectId}")
    public Result<Void> unbind(@RequestHeader("X-User-Id") Long userId,
                              @PathVariable Long projectId) {
        userProjectService.unbind(userId, projectId);
        return Result.success(null);
    }
}
