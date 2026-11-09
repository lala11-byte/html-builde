package com.htmlbuilder.project.controller;

import com.htmlbuilder.common.result.Result;
import com.htmlbuilder.project.dto.CreatePageDTO;
import com.htmlbuilder.project.dto.CreateProjectDTO;
import com.htmlbuilder.project.dto.UpdatePageDTO;
import com.htmlbuilder.project.dto.UpdateProjectDTO;
import com.htmlbuilder.project.service.ProjectService;
import com.htmlbuilder.project.vo.CommunityProjectVO;
import com.htmlbuilder.project.vo.LikeResultVO;
import com.htmlbuilder.project.vo.PageVO;
import com.htmlbuilder.project.vo.ProjectVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public Result<ProjectVO> create(@RequestHeader("X-User-Id") Long userId,
                                    @Valid @RequestBody CreateProjectDTO dto) {
        return Result.success(projectService.createProject(userId, dto));
    }

    @GetMapping
    public Result<Page<ProjectVO>> list(@RequestHeader("X-User-Id") Long userId,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return Result.success(projectService.listProjects(userId, page, size));
    }

    /** 作品广场：查看其他用户已生成的成品项目，支持最新/最喜爱/综合排序与搜索 */
    @GetMapping("/community")
    public Result<Page<CommunityProjectVO>> community(@RequestHeader("X-User-Id") Long userId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "12") int size,
                                                      @RequestParam(defaultValue = "comprehensive") String sort,
                                                      @RequestParam(required = false) String keyword) {
        return Result.success(projectService.listCommunityProjects(userId, page, size, sort, keyword));
    }

    /** 点赞 / 取消点赞 */
    @PostMapping("/{id}/like")
    public Result<LikeResultVO> like(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable Long id) {
        return Result.success(projectService.toggleLike(userId, id));
    }

    @PutMapping("/{id}")
    public Result<ProjectVO> update(@RequestHeader("X-User-Id") Long userId,
                                    @PathVariable Long id,
                                    @Valid @RequestBody UpdateProjectDTO dto) {
        return Result.success(projectService.updateProject(userId, id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable Long id) {
        projectService.deleteProject(userId, id);
        return Result.success(null);
    }

    @PutMapping("/{id}/screenshot")
    public Result<Void> updateScreenshot(@PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        projectService.updateScreenshot(id, body.get("screenshotUrl"));
        return Result.success(null);
    }

    @PostMapping("/{pid}/pages")
    public Result<PageVO> createPage(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable Long pid,
                                     @Valid @RequestBody CreatePageDTO dto) {
        return Result.success(projectService.createPage(userId, pid, dto));
    }

    @GetMapping("/{pid}/pages")
    public Result<Page<PageVO>> listPages(@PathVariable Long pid,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.success(projectService.listPages(pid, page, size));
    }

    @PutMapping("/{pid}/pages/{pageId}")
    public Result<PageVO> updatePage(@PathVariable Long pid,
                                     @PathVariable Long pageId,
                                     @Valid @RequestBody UpdatePageDTO dto) {
        return Result.success(projectService.updatePage(pid, pageId, dto));
    }

    @DeleteMapping("/{pid}/pages/{pageId}")
    public Result<Void> deletePage(@PathVariable Long pid, @PathVariable Long pageId) {
        projectService.deletePage(pid, pageId);
        return Result.success(null);
    }
}