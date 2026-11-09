package com.htmlbuilder.project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.project.entity.Project;
import com.htmlbuilder.project.entity.ProjectLike;
import com.htmlbuilder.project.mapper.PageMapper;
import com.htmlbuilder.project.mapper.ProjectLikeMapper;
import com.htmlbuilder.project.mapper.ProjectMapper;
import com.htmlbuilder.project.vo.AdminProjectVO;
import com.htmlbuilder.project.vo.AdminStatsVO;
import com.htmlbuilder.project.vo.AdminTaskVO;
import com.htmlbuilder.project.vo.TokenTaskVO;
import com.htmlbuilder.project.vo.TokenUserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台管理服务：作品管理、社区上下架、token 用量统计。
 * 统计需要跨 user_db / generator_db 读取数据，沿用项目里已有的 JDBC 直连方式。
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectMapper projectMapper;
    private final PageMapper pageMapper;
    private final ProjectLikeMapper projectLikeMapper;

    @Value("${user-db.url:jdbc:mysql://127.0.0.1:3306/user_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai}")
    private String userDbUrl;
    @Value("${user-db.username:root}")
    private String userDbUsername;
    @Value("${user-db.password:123456}")
    private String userDbPassword;

    @Value("${generator-db.url:jdbc:mysql://127.0.0.1:3306/generator_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai}")
    private String generatorDbUrl;
    @Value("${generator-db.username:root}")
    private String generatorDbUsername;
    @Value("${generator-db.password:123456}")
    private String generatorDbPassword;

    public AdminService(ProjectMapper projectMapper, PageMapper pageMapper,
                        ProjectLikeMapper projectLikeMapper) {
        this.projectMapper = projectMapper;
        this.pageMapper = pageMapper;
        this.projectLikeMapper = projectLikeMapper;
    }

    public AdminStatsVO stats() {
        AdminStatsVO vo = new AdminStatsVO();
        vo.setUserCount(queryLong(userDbUrl, userDbUsername, userDbPassword, "SELECT COUNT(*) FROM user"));
        vo.setProjectCount(projectMapper.selectCount(null));
        vo.setDeletedProjectCount(projectMapper.selectCount(new QueryWrapper<Project>().eq("deleted", 1)));
        vo.setPublishedCount(projectMapper.selectCount(new QueryWrapper<Project>()
                .eq("deleted", 0).eq("community_status", "published")));
        vo.setUnpublishedCount(projectMapper.selectCount(new QueryWrapper<Project>()
                .eq("deleted", 0).eq("community_status", "unpublished")));
        vo.setTaskCount(queryLong(generatorDbUrl, generatorDbUsername, generatorDbPassword,
                "SELECT COUNT(*) FROM generation_task"));
        Map<String, Long> taskStatus = queryTaskStatusCounts();
        vo.setRunningTaskCount(taskStatus.getOrDefault("RUNNING", 0L) + taskStatus.getOrDefault("PENDING", 0L));
        vo.setCompletedTaskCount(taskStatus.getOrDefault("COMPLETED", 0L));
        vo.setFailedTaskCount(taskStatus.getOrDefault("FAILED", 0L));
        vo.setCancelledTaskCount(taskStatus.getOrDefault("CANCELLED", 0L));
        vo.setTotalTokens(queryLong(generatorDbUrl, generatorDbUsername, generatorDbPassword,
                "SELECT COALESCE(SUM(total_tokens), 0) FROM generation_task"));
        return vo;
    }

    /** 按状态统计生成任务数量 */
    private Map<String, Long> queryTaskStatusCounts() {
        Map<String, Long> counts = new HashMap<>();
        String sql = "SELECT status, COUNT(*) AS cnt FROM generation_task GROUP BY status";
        try (Connection conn = DriverManager.getConnection(generatorDbUrl, generatorDbUsername, generatorDbPassword);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("status"), rs.getLong("cnt"));
            }
        } catch (Exception e) {
            log.warn("统计任务状态失败: {}", e.getMessage());
        }
        return counts;
    }

    public Page<AdminProjectVO> listProjects(String keyword, Long userId, Integer deleted,
                                             String communityStatus, int pageNum, int pageSize) {
        Page<Project> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Project> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like("name", keyword.trim());
        }
        if (userId != null) {
            qw.eq("user_id", userId);
        }
        if (deleted != null) {
            qw.eq("deleted", deleted);
        }
        if (communityStatus != null && !communityStatus.isBlank()) {
            qw.eq("community_status", communityStatus);
        }
        qw.orderByDesc("updated_at");
        Page<Project> source = projectMapper.selectPage(page, qw);

        Map<Long, String> names = loadUserNames(source.getRecords().stream()
                .map(Project::getUserId).collect(Collectors.toSet()));
        Map<Long, Long> likeCounts = loadLikeCounts(source.getRecords().stream()
                .map(Project::getId).toList());

        Page<AdminProjectVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(p -> {
            AdminProjectVO vo = new AdminProjectVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setUserId(p.getUserId());
            vo.setAuthorName(names.getOrDefault(p.getUserId(), "用户" + p.getUserId()));
            vo.setScreenshotUrl(p.getScreenshotUrl());
            vo.setPageCount(pageMapper.selectCount(
                    new QueryWrapper<com.htmlbuilder.project.entity.Page>().eq("project_id", p.getId())).intValue());
            vo.setDeleted(p.getDeleted() != null && p.getDeleted() == 1);
            vo.setDeletedAt(fmt(p.getDeletedAt()));
            vo.setDeletedBy(p.getDeletedBy());
            vo.setCommunityStatus(p.getCommunityStatus());
            vo.setPublishedAt(fmt(p.getPublishedAt()));
            vo.setCreatedAt(fmt(p.getCreatedAt()));
            vo.setUpdatedAt(fmt(p.getUpdatedAt()));
            vo.setLikeCount(likeCounts.getOrDefault(p.getId(), 0L));
            return vo;
        }).toList());
        return result;
    }

    public void deleteProject(Long adminId, Long projectId) {
        Project project = requireProject(projectId);
        project.setDeleted(1);
        project.setDeletedAt(LocalDateTime.now());
        project.setDeletedBy(adminId);
        projectMapper.updateById(project);
    }

    public void restoreProject(Long projectId) {
        Project project = requireProject(projectId);
        project.setDeleted(0);
        project.setDeletedAt(null);
        project.setDeletedBy(null);
        projectMapper.updateById(project);
    }

    public void setCommunityStatus(Long projectId, String status) {
        if (!"published".equals(status) && !"unpublished".equals(status)) {
            throw new BusinessException(2002, "社区状态只能是 published 或 unpublished");
        }
        Project project = requireProject(projectId);
        project.setCommunityStatus(status);
        if ("published".equals(status) && project.getPublishedAt() == null) {
            project.setPublishedAt(LocalDateTime.now());
        }
        projectMapper.updateById(project);
    }

    /** 按用户汇总 token 用量（支持用户名/昵称搜索） */
    public Page<TokenUserVO> listTokenUsers(String keyword, int pageNum, int pageSize) {
        Set<Long> filterIds = null;
        if (keyword != null && !keyword.isBlank()) {
            filterIds = searchUserIds(keyword.trim());
            if (filterIds.isEmpty()) {
                return new Page<>(pageNum, pageSize, 0);
            }
        }
        String filter = filterIds == null ? "" : " AND t.user_id IN ("
                + filterIds.stream().map(id -> String.valueOf(id)).collect(Collectors.joining(",")) + ")";

        long total = queryLong(generatorDbUrl, generatorDbUsername, generatorDbPassword,
                "SELECT COUNT(DISTINCT t.user_id) FROM generation_task t"
                        + " LEFT JOIN project_db.project p ON p.id = t.project_id WHERE 1=1" + filter);

        Page<TokenUserVO> result = new Page<>(pageNum, pageSize, total);
        List<TokenUserVO> rows = new ArrayList<>();
        String sql = "SELECT t.user_id, COUNT(*) AS task_count,"
                + " SUM(COALESCE(t.total_tokens, 0)) AS total_tokens,"
                + " SUM(COALESCE(t.prompt_tokens, 0)) AS prompt_tokens,"
                + " SUM(COALESCE(t.completion_tokens, 0)) AS completion_tokens,"
                + " SUM(CASE WHEN p.deleted = 1 THEN 1 ELSE 0 END) AS deleted_task_count,"
                + " SUM(CASE WHEN p.deleted = 1 THEN COALESCE(t.total_tokens, 0) ELSE 0 END) AS deleted_tokens"
                + " FROM generation_task t LEFT JOIN project_db.project p ON p.id = t.project_id"
                + " WHERE 1=1" + filter
                + " GROUP BY t.user_id ORDER BY total_tokens DESC LIMIT ? OFFSET ?";
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        try (Connection conn = DriverManager.getConnection(generatorDbUrl, generatorDbUsername, generatorDbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TokenUserVO vo = new TokenUserVO();
                    vo.setUserId(rs.getLong("user_id"));
                    vo.setTaskCount(rs.getLong("task_count"));
                    vo.setPromptTokens(rs.getLong("prompt_tokens"));
                    vo.setCompletionTokens(rs.getLong("completion_tokens"));
                    vo.setTotalTokens(rs.getLong("total_tokens"));
                    vo.setDeletedTaskCount(rs.getLong("deleted_task_count"));
                    vo.setDeletedTokens(rs.getLong("deleted_tokens"));
                    rows.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("查询 token 用量失败: {}", e.getMessage());
        }

        Map<Long, String[]> userInfos = loadUserInfos(rows.stream().map(TokenUserVO::getUserId).toList());
        for (TokenUserVO vo : rows) {
            String[] info = userInfos.get(vo.getUserId());
            vo.setUsername(info == null ? "" : info[0]);
            vo.setNickname(info == null ? "" : info[1]);
            vo.setRole(info == null ? "user" : info[2]);
            vo.setStatus(info == null ? "unknown" : info[3]);
        }
        result.setRecords(rows);
        return result;
    }

    /** 单个用户的任务 token 明细；deleted 可筛选关联作品是否已删除 */
    public Page<TokenTaskVO> listTokenTasks(Long userId, Integer deleted, int pageNum, int pageSize) {
        if (userId == null) {
            throw new BusinessException(2002, "请先选择用户");
        }
        String filter = deleted == null ? "" : " AND p.deleted = " + (deleted == 1 ? 1 : 0);
        long total = queryLong(generatorDbUrl, generatorDbUsername, generatorDbPassword,
                "SELECT COUNT(*) FROM generation_task t LEFT JOIN project_db.project p ON p.id = t.project_id"
                        + " WHERE t.user_id = " + userId + filter);

        Page<TokenTaskVO> result = new Page<>(pageNum, pageSize, total);
        List<TokenTaskVO> rows = new ArrayList<>();
        String sql = "SELECT t.id, t.user_id, t.project_id, t.prompt, t.status,"
                + " COALESCE(t.model_calls, 0) AS model_calls,"
                + " COALESCE(t.prompt_tokens, 0) AS prompt_tokens,"
                + " COALESCE(t.completion_tokens, 0) AS completion_tokens,"
                + " COALESCE(t.total_tokens, 0) AS total_tokens, t.created_at,"
                + " p.name AS project_name, COALESCE(p.deleted, 0) AS project_deleted"
                + " FROM generation_task t LEFT JOIN project_db.project p ON p.id = t.project_id"
                + " WHERE t.user_id = " + userId + filter
                + " ORDER BY t.created_at DESC LIMIT ? OFFSET ?";
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        try (Connection conn = DriverManager.getConnection(generatorDbUrl, generatorDbUsername, generatorDbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TokenTaskVO vo = new TokenTaskVO();
                    vo.setId(rs.getLong("id"));
                    vo.setUserId(rs.getLong("user_id"));
                    long projectId = rs.getLong("project_id");
                    vo.setProjectId(rs.wasNull() ? null : projectId);
                    vo.setProjectName(rs.getString("project_name"));
                    vo.setPrompt(snippet(rs.getString("prompt")));
                    vo.setStatus(rs.getString("status"));
                    vo.setModelCalls(rs.getInt("model_calls"));
                    vo.setPromptTokens(rs.getLong("prompt_tokens"));
                    vo.setCompletionTokens(rs.getLong("completion_tokens"));
                    vo.setTotalTokens(rs.getLong("total_tokens"));
                    vo.setDeleted(rs.getInt("project_deleted") == 1);
                    vo.setCreatedAt(fmt(rs.getTimestamp("created_at")));
                    rows.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("查询任务 token 明细失败: {}", e.getMessage());
        }
        result.setRecords(rows);
        return result;
    }

    /** 生成任务管理：分页查询（支持状态、用户、关键词筛选） */
    public Page<AdminTaskVO> listTasks(String keyword, String status, Long userId, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = ?");
            args.add(status.trim());
        }
        if (userId != null) {
            where.append(" AND t.user_id = ?");
            args.add(userId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim() + "%";
            where.append(" AND (t.prompt LIKE ? OR p.name LIKE ? OR u.username LIKE ? OR u.nickname LIKE ?)");
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        String from = " FROM generation_task t"
                + " LEFT JOIN project_db.project p ON p.id = t.project_id"
                + " LEFT JOIN user_db.user u ON u.id = t.user_id";

        long total = queryCount("SELECT COUNT(*)" + from + where, args);
        Page<AdminTaskVO> result = new Page<>(Math.max(pageNum, 1), pageSize, total);
        List<AdminTaskVO> rows = new ArrayList<>();
        String sql = "SELECT t.id, t.user_id, t.project_id, t.page_id, t.prompt, t.status, t.error_message,"
                + " COALESCE(t.model_calls, 0) AS model_calls,"
                + " COALESCE(t.prompt_tokens, 0) AS prompt_tokens,"
                + " COALESCE(t.completion_tokens, 0) AS completion_tokens,"
                + " COALESCE(t.total_tokens, 0) AS total_tokens,"
                + " t.created_at, t.updated_at, p.name AS project_name, p.deleted AS project_deleted,"
                + " u.username, u.nickname"
                + from + where + " ORDER BY t.id DESC LIMIT ? OFFSET ?";
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        try (Connection conn = DriverManager.getConnection(generatorDbUrl, generatorDbUsername, generatorDbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Object arg : args) {
                ps.setObject(index++, arg);
            }
            ps.setInt(index++, pageSize);
            ps.setInt(index, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminTaskVO vo = new AdminTaskVO();
                    vo.setId(rs.getLong("id"));
                    vo.setUserId(rs.getLong("user_id"));
                    vo.setUsername(rs.getString("username"));
                    vo.setNickname(rs.getString("nickname"));
                    long projectId = rs.getLong("project_id");
                    vo.setProjectId(rs.wasNull() ? null : projectId);
                    vo.setProjectName(rs.getString("project_name"));
                    long pageId = rs.getLong("page_id");
                    vo.setPageId(rs.wasNull() ? null : pageId);
                    vo.setPrompt(snippet(rs.getString("prompt")));
                    vo.setStatus(rs.getString("status"));
                    vo.setErrorMessage(rs.getString("error_message"));
                    vo.setModelCalls(rs.getInt("model_calls"));
                    vo.setPromptTokens(rs.getLong("prompt_tokens"));
                    vo.setCompletionTokens(rs.getLong("completion_tokens"));
                    vo.setTotalTokens(rs.getLong("total_tokens"));
                    vo.setProjectDeleted(rs.getInt("project_deleted") == 1);
                    vo.setCreatedAt(fmt(rs.getTimestamp("created_at")));
                    vo.setUpdatedAt(fmt(rs.getTimestamp("updated_at")));
                    rows.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("查询生成任务失败: {}", e.getMessage());
        }
        result.setRecords(rows);
        return result;
    }

    /** 终止排队中/运行中的任务 */
    public void cancelTask(Long taskId) {
        try (Connection conn = DriverManager.getConnection(generatorDbUrl, generatorDbUsername, generatorDbPassword)) {
            String status = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM generation_task WHERE id = ?")) {
                ps.setLong(1, taskId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        status = rs.getString(1);
                    }
                }
            }
            if (status == null) {
                throw new BusinessException(2001, "任务不存在");
            }
            if (!"PENDING".equals(status) && !"RUNNING".equals(status)) {
                throw new BusinessException(2003, "只有排队中或运行中的任务可以终止");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE generation_task SET status = 'CANCELLED', error_message = ?, updated_at = NOW()"
                            + " WHERE id = ? AND status IN ('PENDING', 'RUNNING')")) {
                ps.setString(1, "管理员已终止该任务");
                ps.setLong(2, taskId);
                ps.executeUpdate();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("终止任务失败 [{}]: {}", taskId, e.getMessage());
            throw new BusinessException(5001, "终止任务失败：" + e.getMessage());
        }
    }

    /** 删除失败/已终止的任务及其页面快照 */
    public void deleteTask(Long taskId) {
        try (Connection conn = DriverManager.getConnection(generatorDbUrl, generatorDbUsername, generatorDbPassword)) {
            String status = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM generation_task WHERE id = ?")) {
                ps.setLong(1, taskId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        status = rs.getString(1);
                    }
                }
            }
            if (status == null) {
                throw new BusinessException(2001, "任务不存在");
            }
            if (!"FAILED".equals(status) && !"CANCELLED".equals(status)) {
                throw new BusinessException(2004, "只能删除失败或已终止的任务");
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM page_snapshot WHERE task_id = ?")) {
                ps.setLong(1, taskId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM generation_task WHERE id = ?")) {
                ps.setLong(1, taskId);
                ps.executeUpdate();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("删除任务失败 [{}]: {}", taskId, e.getMessage());
            throw new BusinessException(5001, "删除任务失败：" + e.getMessage());
        }
    }

    private long queryCount(String sql, List<Object> args) {
        try (Connection conn = DriverManager.getConnection(generatorDbUrl, generatorDbUsername, generatorDbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Object arg : args) {
                ps.setObject(index++, arg);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            log.warn("统计生成任务失败: {}", e.getMessage());
            return 0L;
        }
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(2001, "项目不存在");
        }
        return project;
    }

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
            log.warn("搜索用户失败: {}", e.getMessage());
        }
        return ids;
    }

    private Map<Long, String> loadUserNames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, COALESCE(NULLIF(nickname, ''), username) AS name FROM user WHERE id IN ("
                + placeholders + ")";
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
            log.warn("查询用户昵称失败: {}", e.getMessage());
        }
        return names;
    }

    /** 返回 [username, nickname, role, status] */
    private Map<Long, String[]> loadUserInfos(Collection<Long> userIds) {
        Map<Long, String[]> infos = new HashMap<>();
        if (userIds.isEmpty()) {
            return infos;
        }
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, username, nickname, role, status FROM user WHERE id IN (" + placeholders + ")";
        try (Connection conn = DriverManager.getConnection(userDbUrl, userDbUsername, userDbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Long id : userIds) {
                ps.setLong(index++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    infos.put(rs.getLong("id"), new String[]{
                            rs.getString("username"), rs.getString("nickname"),
                            rs.getString("role"), rs.getString("status")});
                }
            }
        } catch (Exception e) {
            log.warn("查询用户信息失败: {}", e.getMessage());
        }
        return infos;
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

    private long queryLong(String url, String username, String password, String sql) {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception e) {
            log.warn("统计查询失败 [{}]: {}", sql, e.getMessage());
            return 0L;
        }
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_FMT);
    }

    private String fmt(Timestamp time) {
        return time == null ? "" : time.toLocalDateTime().format(TIME_FMT);
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() > 80 ? t.substring(0, 80) + "…" : t;
    }
}
