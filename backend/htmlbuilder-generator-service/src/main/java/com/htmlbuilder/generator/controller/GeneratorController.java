package com.htmlbuilder.generator.controller;

import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.common.result.Result;
import com.htmlbuilder.generator.dto.GenerateRequest;
import com.htmlbuilder.generator.entity.GenerationTask;
import com.htmlbuilder.generator.service.GeneratorService;
import com.htmlbuilder.generator.vo.TaskVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/generator")
public class GeneratorController {

    private static final Logger log = LoggerFactory.getLogger(GeneratorController.class);

    private final GeneratorService generatorService;

    public GeneratorController(GeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    /**
     * 提交生成任务
     */
    @PostMapping("/generate")
    public Result<TaskVO> generate(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody GenerateRequest request) {

        Long taskId = generatorService.submitTask(userId, request.getPrompt());

        TaskVO vo = new TaskVO();
        vo.setId(taskId);
        vo.setStatus("PENDING");
        vo.setProgress("任务已提交，等待执行...");

        return Result.success(vo);
    }

    /**
     * SSE 进度推送
     */
    @GetMapping(value = "/progress/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@PathVariable Long taskId) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10分钟超时

        // 先检查任务是否存在
        GenerationTask task = generatorService.getTask(taskId);
        if (task == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("任务不存在"));
                emitter.complete();
            } catch (IOException e) {
                log.warn("Failed to send error", e);
            }
            return emitter;
        }

        // 如果任务已完成，直接返回完成事件
        if ("COMPLETED".equals(task.getStatus())) {
            try {
                emitter.send(SseEmitter.event().name("complete")
                        .data("{\"status\":\"COMPLETED\",\"downloadUrl\":\"/api/v1/generator/download/" + taskId + "\"}"));
                emitter.complete();
            } catch (IOException e) {
                log.warn("Failed to send complete event", e);
            }
            return emitter;
        }

        // 如果任务已失败，返回错误
        if ("FAILED".equals(task.getStatus())) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("生成失败: " + (task.getErrorMessage() != null ? task.getErrorMessage() : "未知错误")));
                emitter.complete();
            } catch (IOException e) {
                log.warn("Failed to send error event", e);
            }
            return emitter;
        }

        // 启动异步执行
        generatorService.executeTaskAsync(taskId, emitter);

        return emitter;
    }

    /**
     * 下载生成的 ZIP 包
     */
    @GetMapping(value = "/download/{taskId}", produces = "application/zip")
    public void download(@PathVariable Long taskId, HttpServletResponse response) {
        Path outputPath = generatorService.getOutputPath(taskId);
        if (outputPath == null || !Files.exists(outputPath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try {
                response.getWriter().write("{\"code\":2001,\"message\":\"生成结果不存在或已被清理\"}");
            } catch (IOException e) {
                log.warn("Failed to write error response", e);
            }
            return;
        }

        try {
            response.setHeader("Content-Disposition", "attachment; filename=generated-website-" + taskId + ".zip");
            response.setContentType("application/zip");
            org.zeroturnaround.zip.ZipUtil.pack(outputPath.toFile(), response.getOutputStream());
        } catch (IOException e) {
            log.error("Failed to pack zip for task {}", taskId, e);
            throw new RuntimeException("打包下载失败", e);
        }
    }

    /**
     * 预览生成的网站（静态文件服务）
     * 访问路径: /api/v1/generator/preview/{taskId}/index.html
     */
    @GetMapping("/preview/{taskId}/**")
    public void preview(@PathVariable Long taskId, jakarta.servlet.http.HttpServletRequest request,
                        HttpServletResponse response) {
        Path outputPath = generatorService.getOutputPath(taskId);
        if (outputPath == null || !Files.exists(outputPath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 从请求路径中提取文件路径
        String fullPath = request.getRequestURI();
        String prefix = "/api/v1/generator/preview/" + taskId + "/";
        String relativePath = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());
        if (relativePath.isEmpty()) {
            relativePath = "index.html";
        }

        // 安全检查：防止路径穿越
        if (relativePath.contains("..")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Path filePath = outputPath.resolve("public").resolve(relativePath);
        if (!Files.exists(filePath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            // 根据扩展名设置 Content-Type
            String fileName = filePath.getFileName().toString();
            if (fileName.endsWith(".html")) {
                response.setContentType("text/html; charset=UTF-8");
            } else if (fileName.endsWith(".css")) {
                response.setContentType("text/css; charset=UTF-8");
            } else if (fileName.endsWith(".js")) {
                response.setContentType("application/javascript; charset=UTF-8");
            } else if (fileName.endsWith(".png")) {
                response.setContentType("image/png");
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                response.setContentType("image/jpeg");
            } else if (fileName.endsWith(".svg")) {
                response.setContentType("image/svg+xml");
            } else {
                response.setContentType("application/octet-stream");
            }

            Files.copy(filePath, response.getOutputStream());
        } catch (IOException e) {
            log.error("Failed to serve preview file: {}", filePath, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 获取任务状态
     */
    @GetMapping("/tasks/{taskId}")
    public Result<TaskVO> getTask(@PathVariable Long taskId) {
        GenerationTask task = generatorService.getTask(taskId);
        if (task == null) {
            return Result.fail(2001, "任务不存在");
        }

        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setStatus(task.getStatus());
        if ("COMPLETED".equals(task.getStatus())) {
            vo.setDownloadUrl("/api/v1/generator/download/" + taskId);
        }
        if ("FAILED".equals(task.getStatus())) {
            vo.setProgress(task.getErrorMessage());
        }

        return Result.success(vo);
    }
}