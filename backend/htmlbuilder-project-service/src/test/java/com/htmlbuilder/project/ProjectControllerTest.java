package com.htmlbuilder.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htmlbuilder.common.exception.GlobalExceptionHandler;
import com.htmlbuilder.project.dto.CreateProjectDTO;
import com.htmlbuilder.project.dto.UpdateProjectDTO;
import com.htmlbuilder.project.dto.CreatePageDTO;
import com.htmlbuilder.project.dto.UpdatePageDTO;
import com.htmlbuilder.project.service.ProjectService;
import com.htmlbuilder.project.vo.ProjectVO;
import com.htmlbuilder.project.vo.PageVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = com.htmlbuilder.project.controller.ProjectController.class)
@Import(GlobalExceptionHandler.class)
public class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    /* ========== M3-1: 创建项目 ========== */

    @Test
    void createProject_shouldReturnResultWithProjectVO() throws Exception {
        CreateProjectDTO dto = new CreateProjectDTO();
        dto.setName("我的项目");

        ProjectVO vo = new ProjectVO();
        vo.setId(1L);
        vo.setName("我的项目");
        vo.setPageCount(0);

        when(projectService.createProject(eq(1L), any(CreateProjectDTO.class))).thenReturn(vo);

        mockMvc.perform(post("/api/v1/projects")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("我的项目"));
    }

    /* ========== M3-2: 分页查询项目列表 ========== */

    @Test
    void listProjects_shouldReturnPaginatedProjects() throws Exception {
        ProjectVO vo = new ProjectVO();
        vo.setId(1L);
        vo.setName("测试项目");
        vo.setPageCount(3);

        Page<ProjectVO> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(vo));

        when(projectService.listProjects(eq(1L), eq(1), eq(10))).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/projects")
                .header("X-User-Id", "1")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].name").value("测试项目"));
    }

    /* ========== M3-3: 重命名项目 ========== */

    @Test
    void updateProject_shouldReturnUpdatedProject() throws Exception {
        UpdateProjectDTO dto = new UpdateProjectDTO();
        dto.setName("新名称");

        ProjectVO vo = new ProjectVO();
        vo.setId(1L);
        vo.setName("新名称");
        vo.setPageCount(0);

        when(projectService.updateProject(eq(1L), eq(1L), any(UpdateProjectDTO.class))).thenReturn(vo);

        mockMvc.perform(put("/api/v1/projects/1")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("新名称"));
    }

    /* ========== M3-4: 删除项目 ========== */

    @Test
    void deleteProject_shouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/1")
                .header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    /* ========== M3-5: 创建页面 ========== */

    @Test
    void createPage_shouldReturnPageVO() throws Exception {
        CreatePageDTO dto = new CreatePageDTO();
        dto.setTitle("首页");

        PageVO vo = new PageVO();
        vo.setId(1L);
        vo.setProjectId(1L);
        vo.setTitle("首页");
        vo.setSortOrder(0);

        when(projectService.createPage(eq(1L), eq(1L), any(CreatePageDTO.class))).thenReturn(vo);

        mockMvc.perform(post("/api/v1/projects/1/pages")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.title").value("首页"));
    }

    /* ========== M3-6: 分页查询页面列表 ========== */

    @Test
    void listPages_shouldReturnPaginatedPages() throws Exception {
        PageVO vo = new PageVO();
        vo.setId(1L);
        vo.setProjectId(1L);
        vo.setTitle("首页");
        vo.setSortOrder(0);

        Page<PageVO> pageResult = new Page<>(1, 20, 1);
        pageResult.setRecords(List.of(vo));

        when(projectService.listPages(eq(1L), eq(1), eq(20))).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/projects/1/pages")
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records[0].title").value("首页"));
    }

    /* ========== M3-7: 重命名页面 ========== */

    @Test
    void updatePage_shouldReturnUpdatedPage() throws Exception {
        UpdatePageDTO dto = new UpdatePageDTO();
        dto.setTitle("新标题");

        PageVO vo = new PageVO();
        vo.setId(1L);
        vo.setProjectId(1L);
        vo.setTitle("新标题");

        when(projectService.updatePage(eq(1L), eq(1L), any(UpdatePageDTO.class))).thenReturn(vo);

        mockMvc.perform(put("/api/v1/projects/1/pages/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.title").value("新标题"));
    }

    /* ========== M3-8: 删除页面 ========== */

    @Test
    void deletePage_shouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/1/pages/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    /* ========== M3-10: 参数校验 ========== */

    @Test
    void createProject_emptyName_shouldReturn400() throws Exception {
        CreateProjectDTO dto = new CreateProjectDTO();
        dto.setName("");

        mockMvc.perform(post("/api/v1/projects")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createProject_nameTooLong_shouldReturn400() throws Exception {
        CreateProjectDTO dto = new CreateProjectDTO();
        dto.setName("A".repeat(51));

        mockMvc.perform(post("/api/v1/projects")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createPage_emptyTitle_shouldReturn400() throws Exception {
        CreatePageDTO dto = new CreatePageDTO();
        dto.setTitle("");

        mockMvc.perform(post("/api/v1/projects/1/pages")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }
}