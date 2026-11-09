package com.htmlbuilder.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.project.dto.CreatePageDTO;
import com.htmlbuilder.project.dto.CreateProjectDTO;
import com.htmlbuilder.project.dto.UpdatePageDTO;
import com.htmlbuilder.project.dto.UpdateProjectDTO;
import com.htmlbuilder.project.entity.Page;
import com.htmlbuilder.project.entity.Project;
import com.htmlbuilder.project.entity.ProjectLike;
import com.htmlbuilder.project.mapper.PageMapper;
import com.htmlbuilder.project.mapper.ProjectLikeMapper;
import com.htmlbuilder.project.mapper.ProjectMapper;
import com.htmlbuilder.project.service.ProjectService;
import com.htmlbuilder.project.vo.CommunityProjectVO;
import com.htmlbuilder.project.vo.LikeResultVO;
import com.htmlbuilder.project.vo.PageVO;
import com.htmlbuilder.project.vo.ProjectVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final PageMapper pageMapper;
    private final ProjectLikeMapper projectLikeMapper;

    @Value("${user-db.url:jdbc:mysql://127.0.0.1:3306/user_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai}")
    private String userDbUrl;
    @Value("${user-db.username:root}")
    private String userDbUsername;
    @Value("${user-db.password:123456}")
    private String userDbPassword;

    public ProjectServiceImpl(ProjectMapper projectMapper, PageMapper pageMapper, ProjectLikeMapper projectLikeMapper) {
        this.projectMapper = projectMapper;
        this.pageMapper = pageMapper;
        this.projectLikeMapper = projectLikeMapper;
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
        // 用户自己删除的项目不再展示；被平台（管理员）删除的项目保留，用于工作台提示
        qw.eq("user_id", userId)
                .and(w -> w.eq("deleted", 0)
                        .or().isNull("deleted_by")
                        .or().ne("deleted_by", userId))
                .orderByDesc("created_at");

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
            vo.setScreenshotUrl(p.getScreenshotUrl());
            vo.setDeleted(p.getDeleted() != null && p.getDeleted() == 1);
            vo.setDeletedAt(p.getDeletedAt());
            vo.setCommunityStatus(p.getCommunityStatus());
            return vo;
        }).toList());
        return result;
    }

    @Override
    public void updateScreenshot(Long projectId, String screenshotUrl) {
        Project project = projectMapper.selectById(projectId);
        if (project != null) {
            project.setScreenshotUrl(screenshotUrl);
            projectMapper.updateById(project);
        }
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<CommunityProjectVO> listCommunityProjects(
            Long userId, int pageNum, int pageSize, String sort, String keyword) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Project> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        QueryWrapper<Project> qw = new QueryWrapper<>();
        // 成品判定：已生成过页面截图；只展示其他用户、未删除且已上架的项目
        qw.isNotNull("screenshot_url").ne("screenshot_url", "")
                .ne("user_id", userId)
                .eq("deleted", 0)
                .eq("community_status", "published");

        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            Set<Long> matchedUserIds = searchUserIds(kw);
            qw.and(w -> {
                w.like("name", kw);
                if (!matchedUserIds.isEmpty()) {
                    w.or().in("user_id", matchedUserIds);
                }
            });
        }

        // 排序：最新 / 最喜爱 / 综合（喜爱数权重 + 时间新鲜度）
        String order;
        switch (sort == null ? "" : sort) {
            case "like" -> order = "ORDER BY (SELECT COUNT(*) FROM project_like pl WHERE pl.project_id = project.id) DESC,"
                    + " COALESCE(published_at, updated_at) DESC, id DESC";
            case "comprehensive" -> order = "ORDER BY ((SELECT COUNT(*) FROM project_like pl WHERE pl.project_id = project.id) * 20"
                    + " - TIMESTAMPDIFF(HOUR, COALESCE(published_at, updated_at), NOW())) DESC,"
                    + " COALESCE(published_at, updated_at) DESC, id DESC";
            default -> order = "ORDER BY COALESCE(published_at, updated_at) DESC, id DESC";
        }
        qw.last(order);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Project> projectPage = projectMapper.selectPage(page, qw);
        List<Project> records = projectPage.getRecords();

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CommunityProjectVO> result =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(projectPage.getCurrent(), projectPage.getSize(), projectPage.getTotal());
        if (records.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        List<Long> projectIds = records.stream().map(Project::getId).toList();
        Set<Long> authorIds = records.stream().map(Project::getUserId).collect(Collectors.toSet());
        Map<Long, String> authorNames = loadUserNames(authorIds);
        Map<Long, Long> likeCounts = loadLikeCounts(projectIds);
        Set<Long> likedIds = projectLikeMapper.selectList(new QueryWrapper<ProjectLike>()
                        .in("project_id", projectIds).eq("user_id", userId))
                .stream().map(ProjectLike::getProjectId).collect(Collectors.toCollection(HashSet::new));

        result.setRecords(records.stream().map(p -> {
            CommunityProjectVO vo = new CommunityProjectVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setScreenshotUrl(p.getScreenshotUrl());
            vo.setAuthorId(p.getUserId());
            vo.setAuthorName(authorNames.getOrDefault(p.getUserId(), "匿名用户"));
            long count = pageMapper.selectCount(new QueryWrapper<Page>().eq("project_id", p.getId()));
            vo.setPageCount((int) count);
            vo.setLikeCount(likeCounts.getOrDefault(p.getId(), 0L));
            vo.setLiked(likedIds.contains(p.getId()));
            vo.setUpdatedAt(p.getUpdatedAt());
            return vo;
        }).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeResultVO toggleLike(Long userId, Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(2001, "项目不存在");
        }
        ProjectLike existing = projectLikeMapper.selectOne(new QueryWrapper<ProjectLike>()
                .eq("project_id", projectId).eq("user_id", userId));
        boolean liked;
        if (existing == null) {
            ProjectLike like = new ProjectLike();
            like.setProjectId(projectId);
            like.setUserId(userId);
            projectLikeMapper.insert(like);
            liked = true;
        } else {
            projectLikeMapper.deleteById(existing.getId());
            liked = false;
        }
        long count = projectLikeMapper.selectCount(new QueryWrapper<ProjectLike>().eq("project_id", projectId));
        return new LikeResultVO(liked, count);
    }

    private Map<Long, Long> loadLikeCounts(Collection<Long> projectIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (projectIds.isEmpty()) {
            return counts;
        }
        List<Map<String, Object>> rows = projectLikeMapper.selectMaps(new QueryWrapper<ProjectLike>()
                .select("project_id", "COUNT(*) AS cnt")
                .in("project_id", projectIds)
                .groupBy("project_id"));
        for (Map<String, Object> row : rows) {
            Object pid = row.get("project_id");
            Object cnt = row.get("cnt");
            if (pid instanceof Number p && cnt instanceof Number c) {
                counts.put(p.longValue(), c.longValue());
            }
        }
        return counts;
    }

    /**
     * 跨库读取用户昵称（user_db.user）：昵称优先，缺失时回退用户名
     * 读取失败不影响作品广场展示，统一显示「匿名用户」
     */
    private Map<Long, String> loadUserNames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, COALESCE(NULLIF(nickname, ''), username) AS name FROM user WHERE id IN (" + placeholders + ")";
        try (Connection conn = DriverManager.getConnection(userDbUrl, userDbUsername, userDbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Long id : userIds) {
                ps.setLong(index++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.put(rs.getLong("id"), rs.getString("name"));
                }
            }
        } catch (Exception e) {
            log.warn("查询用户昵称失败（作品广场将显示匿名用户）: {}", e.getMessage());
        }
        return names;
    }

    /**
     * 按用户名/昵称搜索作者 ID（跨库读取 user_db）
     */
    private Set<Long> searchUserIds(String keyword) {
        Set<Long> ids = new HashSet<>();
        String sql = "SELECT id FROM user WHERE username LIKE ? OR nickname LIKE ?";
        try (Connection conn = DriverManager.getConnection(userDbUrl, userDbUsername, userDbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
            }
        } catch (Exception e) {
            log.warn("搜索作者失败（仅按作品名搜索）: {}", e.getMessage());
        }
        return ids;
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
        // 软删除：保留数据，让工作台能向作者反馈「作品已被平台删除」
        project.setDeleted(1);
        project.setDeletedAt(java.time.LocalDateTime.now());
        project.setDeletedBy(userId);
        projectMapper.updateById(project);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectServiceImpl.class);

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