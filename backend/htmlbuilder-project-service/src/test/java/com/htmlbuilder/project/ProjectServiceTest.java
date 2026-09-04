package com.htmlbuilder.project;

import com.htmlbuilder.project.entity.Project;
import com.htmlbuilder.project.entity.Page;
import com.htmlbuilder.project.service.ProjectService;
import com.htmlbuilder.project.dto.CreateProjectDTO;
import com.htmlbuilder.project.dto.UpdateProjectDTO;
import com.htmlbuilder.project.dto.CreatePageDTO;
import com.htmlbuilder.project.dto.UpdatePageDTO;
import com.htmlbuilder.project.vo.ProjectVO;
import com.htmlbuilder.project.vo.PageVO;
import com.htmlbuilder.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
@ActiveProfiles("test")
public class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @MockBean
    private com.htmlbuilder.project.mapper.ProjectMapper projectMapper;

    @MockBean
    private com.htmlbuilder.project.mapper.PageMapper pageMapper;

    /* ========== M3-1: 创建项目 ========== */

    @Test
    void createProject_shouldReturnProjectVO() {
        CreateProjectDTO dto = new CreateProjectDTO();
        dto.setName("我的项目");

        when(projectMapper.insert(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });
        when(pageMapper.selectCount(any())).thenReturn(0L);

        ProjectVO result = projectService.createProject(1L, dto);
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("我的项目", result.getName());
        assertEquals(0, result.getPageCount());
    }

    /* ========== M3-2: 分页查询项目列表 ========== */

    @Test
    void listProjects_shouldReturnPaginatedProjects() {
        Project proj = new Project();
        proj.setId(1L);
        proj.setUserId(1L);
        proj.setName("测试项目");

        when(projectMapper.selectPage(any(), any())).thenReturn(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Project>(1, 10, 1).setRecords(List.of(proj))
        );
        when(pageMapper.selectCount(any())).thenReturn(3L);

        var result = projectService.listProjects(1L, 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("测试项目", result.getRecords().get(0).getName());
        assertEquals(3, result.getRecords().get(0).getPageCount());
    }

    /* ========== M3-3: 重命名项目 ========== */

    @Test
    void updateProject_shouldReturnProjectVO() {
        Project existing = new Project();
        existing.setId(1L);
        existing.setUserId(1L);
        existing.setName("旧名称");

        UpdateProjectDTO dto = new UpdateProjectDTO();
        dto.setName("新名称");

        when(projectMapper.selectById(1L)).thenReturn(existing);
        when(projectMapper.updateById(any(Project.class))).thenReturn(1);
        when(pageMapper.selectCount(any())).thenReturn(0L);

        ProjectVO result = projectService.updateProject(1L, 1L, dto);
        assertNotNull(result);
        assertEquals("新名称", result.getName());
    }

    @Test
    void updateProject_notFound_shouldThrowBusinessException() {
        UpdateProjectDTO dto = new UpdateProjectDTO();
        dto.setName("新名称");

        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> projectService.updateProject(1L, 999L, dto));
    }

    /* ========== M3-4: 删除项目级联删除页面 ========== */

    @Test
    void deleteProject_shouldDeleteAndCascadeDeletePages() {
        Project existing = new Project();
        existing.setId(1L);
        existing.setUserId(1L);

        when(projectMapper.selectById(1L)).thenReturn(existing);
        when(projectMapper.deleteById(1L)).thenReturn(1);
        when(pageMapper.delete(any())).thenReturn(2);

        assertDoesNotThrow(() -> projectService.deleteProject(1L, 1L));
        verify(pageMapper).delete(any());
        verify(projectMapper).deleteById(1L);
    }

    @Test
    void deleteProject_notFound_shouldThrowBusinessException() {
        when(projectMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> projectService.deleteProject(1L, 999L));
    }

    /* ========== M3-5: 创建页面 ========== */

    @Test
    void createPage_shouldReturnPageVO() {
        Project existing = new Project();
        existing.setId(1L);
        existing.setUserId(1L);

        CreatePageDTO dto = new CreatePageDTO();
        dto.setTitle("首页");

        when(projectMapper.selectById(1L)).thenReturn(existing);
        when(pageMapper.selectCount(any())).thenReturn(0L);
        when(pageMapper.insert(any(Page.class))).thenAnswer(inv -> {
            Page p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });

        PageVO result = projectService.createPage(1L, 1L, dto);
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("首页", result.getTitle());
        assertEquals(1L, result.getProjectId());
    }

    @Test
    void createPage_projectNotFound_shouldThrowBusinessException() {
        CreatePageDTO dto = new CreatePageDTO();
        dto.setTitle("首页");

        when(projectMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> projectService.createPage(1L, 999L, dto));
    }

    /* ========== M3-6: 分页查询页面列表 ========== */

    @Test
    void listPages_shouldReturnPaginatedPages() {
        Page pageEntity = new Page();
        pageEntity.setId(1L);
        pageEntity.setProjectId(1L);
        pageEntity.setTitle("首页");
        pageEntity.setSortOrder(0);

        when(pageMapper.selectPage(any(), any())).thenReturn(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Page>(1, 20, 1).setRecords(List.of(pageEntity))
        );

        var result = projectService.listPages(1L, 1, 20);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals("首页", result.getRecords().get(0).getTitle());
    }

    /* ========== M3-7: 重命名页面 ========== */

    @Test
    void updatePage_shouldReturnPageVO() {
        Page existing = new Page();
        existing.setId(1L);
        existing.setProjectId(1L);
        existing.setTitle("旧标题");

        UpdatePageDTO dto = new UpdatePageDTO();
        dto.setTitle("新标题");

        when(pageMapper.selectById(1L)).thenReturn(existing);
        when(pageMapper.updateById(any(Page.class))).thenReturn(1);

        PageVO result = projectService.updatePage(1L, 1L, dto);
        assertNotNull(result);
        assertEquals("新标题", result.getTitle());
    }

    @Test
    void updatePage_notFound_shouldThrowBusinessException() {
        UpdatePageDTO dto = new UpdatePageDTO();
        dto.setTitle("新标题");

        when(pageMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> projectService.updatePage(1L, 999L, dto));
    }

    /* ========== M3-8: 删除页面 ========== */

    @Test
    void deletePage_shouldDelete() {
        Page existing = new Page();
        existing.setId(1L);
        existing.setProjectId(1L);

        when(pageMapper.selectById(1L)).thenReturn(existing);
        when(pageMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> projectService.deletePage(1L, 1L));
    }

    @Test
    void deletePage_notFound_shouldThrowBusinessException() {
        when(pageMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> projectService.deletePage(1L, 999L));
    }

    /* ========== M3-9: 权限校验 ========== */

    @Test
    void updateProject_notOwner_shouldThrowBusinessException() {
        Project existing = new Project();
        existing.setId(1L);
        existing.setUserId(2L); // 属于另一个用户

        UpdateProjectDTO dto = new UpdateProjectDTO();
        dto.setName("新名称");

        when(projectMapper.selectById(1L)).thenReturn(existing);

        assertThrows(BusinessException.class, () -> projectService.updateProject(1L, 1L, dto));
    }

    @Test
    void deleteProject_notOwner_shouldThrowBusinessException() {
        Project existing = new Project();
        existing.setId(1L);
        existing.setUserId(2L);

        when(projectMapper.selectById(1L)).thenReturn(existing);
        assertThrows(BusinessException.class, () -> projectService.deleteProject(1L, 1L));
    }

    @Test
    void createPage_notOwner_shouldThrowBusinessException() {
        Project existing = new Project();
        existing.setId(1L);
        existing.setUserId(2L);

        CreatePageDTO dto = new CreatePageDTO();
        dto.setTitle("首页");

        when(projectMapper.selectById(1L)).thenReturn(existing);
        assertThrows(BusinessException.class, () -> projectService.createPage(1L, 1L, dto));
    }
}