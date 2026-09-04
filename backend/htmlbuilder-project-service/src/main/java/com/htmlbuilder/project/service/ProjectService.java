package com.htmlbuilder.project.service;

import com.htmlbuilder.project.dto.CreatePageDTO;
import com.htmlbuilder.project.dto.CreateProjectDTO;
import com.htmlbuilder.project.dto.UpdatePageDTO;
import com.htmlbuilder.project.dto.UpdateProjectDTO;
import com.htmlbuilder.project.vo.PageVO;
import com.htmlbuilder.project.vo.ProjectVO;

public interface ProjectService {
    ProjectVO createProject(Long userId, CreateProjectDTO dto);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProjectVO> listProjects(Long userId, int pageNum, int pageSize);
    ProjectVO updateProject(Long userId, Long projectId, UpdateProjectDTO dto);
    void deleteProject(Long userId, Long projectId);
    PageVO createPage(Long userId, Long projectId, CreatePageDTO dto);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<PageVO> listPages(Long projectId, int pageNum, int pageSize);
    PageVO updatePage(Long projectId, Long pageId, UpdatePageDTO dto);
    void deletePage(Long projectId, Long pageId);
}