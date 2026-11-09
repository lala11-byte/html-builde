package com.htmlbuilder.project.service;

import com.htmlbuilder.project.dto.CreatePageDTO;
import com.htmlbuilder.project.dto.CreateProjectDTO;
import com.htmlbuilder.project.dto.UpdatePageDTO;
import com.htmlbuilder.project.dto.UpdateProjectDTO;
import com.htmlbuilder.project.vo.CommunityProjectVO;
import com.htmlbuilder.project.vo.LikeResultVO;
import com.htmlbuilder.project.vo.PageVO;
import com.htmlbuilder.project.vo.ProjectVO;

public interface ProjectService {
    ProjectVO createProject(Long userId, CreateProjectDTO dto);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProjectVO> listProjects(Long userId, int pageNum, int pageSize);
    /** 作品广场：其他用户已生成成品的项目；sort=time/like/comprehensive，keyword 搜索作品名或作者 */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<CommunityProjectVO> listCommunityProjects(Long userId, int pageNum, int pageSize, String sort, String keyword);
    /** 点赞 / 取消点赞（切换） */
    LikeResultVO toggleLike(Long userId, Long projectId);
    ProjectVO updateProject(Long userId, Long projectId, UpdateProjectDTO dto);
    void deleteProject(Long userId, Long projectId);
    PageVO createPage(Long userId, Long projectId, CreatePageDTO dto);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<PageVO> listPages(Long projectId, int pageNum, int pageSize);
    PageVO updatePage(Long projectId, Long pageId, UpdatePageDTO dto);
    void deletePage(Long projectId, Long pageId);
    void updateScreenshot(Long projectId, String screenshotUrl);
}