package com.htmlbuilder.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.project.dto.CreatePageDTO;
import com.htmlbuilder.project.dto.CreateProjectDTO;
import com.htmlbuilder.project.dto.UpdatePageDTO;
import com.htmlbuilder.project.dto.UpdateProjectDTO;
import com.htmlbuilder.project.entity.Page;
import com.htmlbuilder.project.entity.Project;
import com.htmlbuilder.project.mapper.PageMapper;
import com.htmlbuilder.project.mapper.ProjectMapper;
import com.htmlbuilder.project.service.ProjectService;
import com.htmlbuilder.project.vo.PageVO;
import com.htmlbuilder.project.vo.ProjectVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final PageMapper pageMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper, PageMapper pageMapper) {
        this.projectMapper = projectMapper;
        this.pageMapper = pageMapper;
    }

    @Override
    public ProjectVO createProject(Long userId, CreateProjectDTO dto) {
        Project project = new Project();
        project.setUserId(userId);
        project.setName(dto.getName());
        projectMapper.insert(project);

        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setPageCount(0);
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
        return vo;
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProjectVO> listProjects(Long userId, int pageNum, int pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Project> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        QueryWrapper<Project> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("created_at");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Project> projectPage = projectMapper.selectPage(page, qw);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProjectVO> result =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(projectPage.getCurrent(), projectPage.getSize(), projectPage.getTotal());
        result.setRecords(projectPage.getRecords().stream().map(p -> {
            ProjectVO vo = new ProjectVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            long count = pageMapper.selectCount(new QueryWrapper<Page>().eq("project_id", p.getId()));
            vo.setPageCount((int) count);
            vo.setCreatedAt(p.getCreatedAt());
            vo.setUpdatedAt(p.getUpdatedAt());
            return vo;
        }).toList());
        return result;
    }

    @Override
    public ProjectVO updateProject(Long userId, Long projectId, UpdateProjectDTO dto) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(2001, "项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该项目");
        }
        project.setName(dto.getName());
        projectMapper.updateById(project);

        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        long count = pageMapper.selectCount(new QueryWrapper<Page>().eq("project_id", project.getId()));
        vo.setPageCount((int) count);
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long userId, Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(2001, "项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该项目");
        }
        pageMapper.delete(new QueryWrapper<Page>().eq("project_id", projectId));
        projectMapper.deleteById(projectId);
    }

    @Override
    public PageVO createPage(Long userId, Long projectId, CreatePageDTO dto) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(2001, "项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该项目");
        }

        long maxSort = pageMapper.selectCount(new QueryWrapper<Page>().eq("project_id", projectId));

        Page page = new Page();
        page.setProjectId(projectId);
        page.setTitle(dto.getTitle());
        page.setSortOrder((int) maxSort);
        page.setComponentTree("[]");
        pageMapper.insert(page);

        PageVO vo = new PageVO();
        vo.setId(page.getId());
        vo.setProjectId(page.getProjectId());
        vo.setTitle(page.getTitle());
        vo.setSortOrder(page.getSortOrder());
        vo.setCreatedAt(page.getCreatedAt());
        vo.setUpdatedAt(page.getUpdatedAt());
        return vo;
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<PageVO> listPages(Long projectId, int pageNum, int pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Page> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        QueryWrapper<Page> qw = new QueryWrapper<>();
        qw.eq("project_id", projectId).orderByAsc("sort_order");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Page> pagePage = pageMapper.selectPage(page, qw);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PageVO> result =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pagePage.getCurrent(), pagePage.getSize(), pagePage.getTotal());
        result.setRecords(pagePage.getRecords().stream().map(p -> {
            PageVO vo = new PageVO();
            vo.setId(p.getId());
            vo.setProjectId(p.getProjectId());
            vo.setTitle(p.getTitle());
            vo.setSortOrder(p.getSortOrder());
            vo.setCreatedAt(p.getCreatedAt());
            vo.setUpdatedAt(p.getUpdatedAt());
            return vo;
        }).toList());
        return result;
    }

    @Override
    public PageVO updatePage(Long projectId, Long pageId, UpdatePageDTO dto) {
        Page page = pageMapper.selectById(pageId);
        if (page == null) {
            throw new BusinessException(2002, "页面不存在");
        }
        page.setTitle(dto.getTitle());
        pageMapper.updateById(page);

        PageVO vo = new PageVO();
        vo.setId(page.getId());
        vo.setProjectId(page.getProjectId());
        vo.setTitle(page.getTitle());
        vo.setSortOrder(page.getSortOrder());
        vo.setCreatedAt(page.getCreatedAt());
        vo.setUpdatedAt(page.getUpdatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePage(Long projectId, Long pageId) {
        Page page = pageMapper.selectById(pageId);
        if (page == null) {
            throw new BusinessException(2002, "页面不存在");
        }
        pageMapper.deleteById(pageId);
    }
}