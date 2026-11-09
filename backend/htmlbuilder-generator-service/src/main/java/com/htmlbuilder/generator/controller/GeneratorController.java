package com.htmlbuilder.generator.controller;

import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.common.result.Result;
import com.htmlbuilder.generator.component.ComponentItem;
import com.htmlbuilder.generator.component.ComponentLibraryService;
import com.htmlbuilder.generator.dto.ElementPatchRequest;
import com.htmlbuilder.generator.dto.GenerateRequest;
import com.htmlbuilder.generator.dto.IterateRequest;
import com.htmlbuilder.generator.dto.SnapshotActionRequest;
import com.htmlbuilder.generator.entity.GenerationTask;
import com.htmlbuilder.generator.service.ElementPatchService;
import com.htmlbuilder.generator.service.GeneratorService;
import com.htmlbuilder.generator.service.LocalRunService;
import com.htmlbuilder.generator.service.PageSnapshotService;
import com.htmlbuilder.generator.service.PortalService;
import com.htmlbuilder.generator.service.ScreenshotService;
import com.htmlbuilder.generator.util.ProjectZipWriter;
import com.htmlbuilder.generator.vo.ElementPatchVO;
import com.htmlbuilder.generator.vo.PortalVO;
import com.htmlbuilder.generator.vo.RunStatusVO;
import com.htmlbuilder.generator.vo.SnapshotHistoryVO;
import com.htmlbuilder.generator.vo.SnapshotVO;
import com.htmlbuilder.generator.vo.TaskVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/v1/generator")
public class GeneratorController {

    private static final Logger log = LoggerFactory.getLogger(GeneratorController.class);

    private final GeneratorService generatorService;
    private final LocalRunService localRunService;
    private final ScreenshotService screenshotService;
    private final PortalService portalService;
    private final ComponentLibraryService componentLibraryService;
    private final ElementPatchService elementPatchService;
    private final PageSnapshotService pageSnapshotService;

    public GeneratorController(GeneratorService generatorService, LocalRunService localRunService,
                               ScreenshotService screenshotService, PortalService portalService,
                               ComponentLibraryService componentLibraryService,
                               ElementPatchService elementPatchService,
                               PageSnapshotService pageSnapshotService) {
        this.generatorService = generatorService;
        this.localRunService = localRunService;
        this.screenshotService = screenshotService;
        this.portalService = portalService;
        this.componentLibraryService = componentLibraryService;
        this.elementPatchService = elementPatchService;
        this.pageSnapshotService = pageSnapshotService;
    }

    /**
     * 预览生成的文件
     * - 优先：如果 localRunService 正在运行，重定向到 liveUrl
     * - 回退：静态文件服务（public/ 目录下的纯静态项目）
     */
    @GetMapping("/preview/{taskId}/**")
    public void preview(@PathVariable Long taskId, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        // 版本恢复会直接覆盖磁盘上的页面文件，必须禁用缓存，否则重进编辑器仍是恢复前的内容
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        // 优先：如果服务正在运行，重定向到 liveUrl
        Integer runningPort = localRunService.getPort(taskId);
        if (runningPort != null) {
            String queryString = request.getQueryString();
            String redirectUrl = "http://localhost:" + runningPort + request.getRequestURI()
                    .replace("/api/v1/generator/preview/" + taskId, "");
            if (queryString != null) {
                redirectUrl += "?" + queryString;
            }
            response.sendRedirect(redirectUrl);
            return;
        }

        // 回退：静态文件服务
        Path outputPath = generatorService.getOutputPath(taskId);
        if (outputPath == null || !Files.exists(outputPath)) {
            writePreviewPlaceholder(response, "该任务的生成结果尚未产出或临时目录已被清理，请重新生成后再预览。");
            return;
        }

        String fullPath = request.getRequestURI();
        String prefix = "/api/v1/generator/preview/" + taskId + "/";
        String relativePath = "";
        if (fullPath.length() > prefix.length()) {
            relativePath = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());
        }

        Path filePath;
        if (relativePath.isEmpty() || relativePath.equals("/")) {
            filePath = outputPath.resolve("public/index.html");
        } else {
            filePath = outputPath.resolve("public/" + relativePath);
        }

        // 目录请求（如 /preview/{id}/teacher/）：解析为该目录下的 index.html
        if (Files.isDirectory(filePath)) {
            if (!fullPath.endsWith("/")) {
                // 补斜杠，保证页面内 ./、../ 相对资源路径解析正确
                response.sendRedirect(fullPath + "/" + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
                return;
            }
            filePath = filePath.resolve("index.html");
        }

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            // 对不存在的路径，如果使用 SPA 模式返回 public/index.html
            Path indexPath = outputPath.resolve("public/index.html");
            if (Files.exists(indexPath)) {
                filePath = indexPath;
            } else {
                writePreviewPlaceholder(response, "预览文件不存在：" + relativePath + "，请确认生成结果完整后重试。");
                return;
            }
        }

        response.setContentType(getContentType(filePath.getFileName().toString()));
        Files.copy(filePath, response.getOutputStream());
    }

    /**
     * 预览不可用时返回 UTF-8 中文提示页（避免 getWriter 默认 ISO-8859-1 导致中文变问号）
     */
    private void writePreviewPlaceholder(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        response.getWriter().write("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>预览不可用</title>"
                + "<style>body{font-family:system-ui,-apple-system,sans-serif;display:flex;align-items:center;"
                + "justify-content:center;height:90vh;margin:0;color:#606266;background:#f5f7fa}</style></head>"
                + "<body><p style=\"max-width:520px;text-align:center;line-height:1.8\">" + message + "</p></body></html>");
    }

    private String getContentType(String filename) {
        if (filename.endsWith(".html") || filename.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (filename.endsWith(".css")) return "text/css";
        if (filename.endsWith(".js")) return "application/javascript";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".svg")) return "image/svg+xml";
        if (filename.endsWith(".ico")) return "image/x-icon";
        if (filename.endsWith(".woff2")) return "font/woff2";
        if (filename.endsWith(".woff")) return "font/woff";
        return "application/octet-stream";
    }

    /**
     * 提交生成任务
     */
    @PostMapping("/generate")
    public Result<TaskVO> generate(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody GenerateRequest request) {

        Long taskId = generatorService.submitTask(userId, request.getProjectId(), request.getPageId(), request.getPrompt(), request.getCustomPrompt());

        TaskVO vo = new TaskVO();
        vo.setId(taskId);
        vo.setStatus("PENDING");
        vo.setProgress("任务已提交，等待执行...");

        return Result.success(vo);
    }

    /**
     * 提交迭代优化任务（基于已有生成结果追加需求）
     */
    @PostMapping("/iterate")
    public Result<TaskVO> iterate(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody IterateRequest request) {

        Long taskId = generatorService.submitIterateTask(userId, request.getTaskId(), request.getAdditionalPrompt(), request.getCustomPrompt());

        TaskVO vo = new TaskVO();
        vo.setId(taskId);
        vo.setStatus("PENDING");
        vo.setProgress("迭代任务已提交，等待执行...");

        return Result.success(vo);
    }

    /**
     * SSE 进度推送
     */
    @GetMapping(value = "/progress/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@PathVariable Long taskId) {
        SseEmitter emitter = new SseEmitter(1_800_000L); // 30分钟超时（防止长任务生成期间 SSE 连接断开）

        // 清理回调：emitter 完成/超时/错误时从列表中移除自身
        emitter.onCompletion(() -> generatorService.removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> generatorService.removeEmitter(taskId, emitter));
        emitter.onError(e -> generatorService.removeEmitter(taskId, emitter));

        // 先检查任务是否存在
        GenerationTask task = generatorService.getTask(taskId);
        if (task == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("任务不存在"));
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to send error", e);
            }
            return emitter;
        }

        // 如果任务已完成，直接返回完成事件
        if ("COMPLETED".equals(task.getStatus())) {
            try {
                StringBuilder data = new StringBuilder("{\"status\":\"COMPLETED\",\"downloadUrl\":\"/api/v1/generator/download/")
                        .append(taskId).append("\",\"previewUrl\":\"").append(generatorService.buildPreviewUrl(taskId)).append('"');
                if (screenshotService.getScreenshotPath(taskId) != null) {
                    data.append(",\"screenshotUrl\":\"/api/v1/generator/screenshot/").append(taskId).append('"');
                }
                data.append('}');
                emitter.send(SseEmitter.event().name("complete").data(data.toString()));
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to send complete event", e);
            }
            return emitter;
        }

        // 如果任务已失败，返回错误
        if ("FAILED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
            String reason = "CANCELLED".equals(task.getStatus()) ? "任务已被管理员终止"
                    : (task.getErrorMessage() != null ? task.getErrorMessage() : "未知错误");
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("生成失败: " + reason));
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to send error event", e);
            }
            return emitter;
        }

        // 启动异步执行（内部会注册 emitter、回放已有日志）
        // RUNNING 状态：服务重启导致任务中断，走续接生成流程
        if ("RUNNING".equals(task.getStatus())) {
            generatorService.resumeTaskAsync(taskId, emitter);
        } else if ("PENDING".equals(task.getStatus()) && task.getProgressLog() != null && task.getProgressLog().contains("\"additionalPrompt\"")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode meta = mapper.readTree(task.getProgressLog());
                Long baseTaskId = meta.get("baseTaskId").asLong();
                String additionalPrompt = meta.get("additionalPrompt").asText();
                generatorService.executeIterateAsync(taskId, emitter, additionalPrompt, baseTaskId);
            } catch (Exception e) {
                log.error("Failed to parse iterate metadata", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("迭代任务参数解析失败"));
                    emitter.complete();
                } catch (Exception ex) {
                    log.warn("Failed to send error", ex);
                }
            }
        } else {
            generatorService.executeTaskAsync(taskId, emitter);
        }

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
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json; charset=UTF-8");
                response.getWriter().write("{\"code\":2001,\"message\":\"生成结果不存在或已被清理\"}");
            } catch (IOException e) {
                log.warn("Failed to write error response", e);
            }
            return;
        }

        try {
            response.setHeader("Content-Disposition", "attachment; filename=generated-website-" + taskId + ".zip");
            response.setContentType("application/zip");
            // 打包时剔除编辑器注入的元素选取脚本，保证下载产物干净
            ProjectZipWriter.write(outputPath, response.getOutputStream());
        } catch (IOException e) {
            log.error("Failed to pack zip for task {}", taskId, e);
            throw new RuntimeException("打包下载失败", e);
        }
    }

    /**
     * 组件库列表（仅元信息，用于左侧组件面板）
     */
    @GetMapping("/components")
    public Result<List<ComponentItem>> listComponents() {
        return Result.success(componentLibraryService.listMeta());
    }

    /**
     * 组件详情（含 html/css/js 片段）
     */
    @GetMapping("/components/{id}")
    public Result<ComponentItem> getComponent(@PathVariable String id) {
        ComponentItem item = componentLibraryService.get(id);
        if (item == null) {
            return Result.fail(2001, "组件不存在: " + id);
        }
        return Result.success(item);
    }

    /**
     * 元素级修改：AI 改写选中元素，或用组件库片段替换/插入
     */
    @PostMapping("/element/patch")
    public Result<ElementPatchVO> patchElement(@RequestBody ElementPatchRequest request) {
        return Result.success(elementPatchService.patch(request));
    }

    /**
     * 为历史任务补注入元素选取脚本（旧任务预览时按需调用）
     */
    @PostMapping("/element/prepare/{taskId}")
    public Result<String> prepareElementEdit(@PathVariable Long taskId) {
        elementPatchService.prepare(taskId);
        return Result.success("ok");
    }

    /**
     * 页面版本历史：分支列表 + 页面列表 + 快照列表（不含正文）
     * page 可选：传入时只返回该页面的版本
     */
    @GetMapping("/history/{taskId}")
    public Result<SnapshotHistoryVO> history(@PathVariable Long taskId,
                                             @RequestParam(required = false) String page) {
        return Result.success(pageSnapshotService.listHistory(taskId, page));
    }

    /**
     * 单个快照详情（含完整 HTML，用于预览）
     */
    @GetMapping("/history/snapshot/{id}")
    public Result<SnapshotVO> historyDetail(@PathVariable Long id) {
        return Result.success(pageSnapshotService.detail(id));
    }

    /**
     * 恢复到指定版本（写回文件并生成一条新快照）
     */
    @PostMapping("/history/{taskId}/restore")
    public Result<SnapshotVO> restoreSnapshot(@PathVariable Long taskId,
                                              @RequestBody SnapshotActionRequest request) {
        return Result.success(pageSnapshotService.restore(taskId, request.getSnapshotId()));
    }

    /**
     * 从指定版本创建新分支
     */
    @PostMapping("/history/{taskId}/branch")
    public Result<SnapshotVO> createSnapshotBranch(@PathVariable Long taskId,
                                                   @RequestBody SnapshotActionRequest request) {
        return Result.success(pageSnapshotService.branchFrom(taskId, request.getSnapshotId(), request.getName()));
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
            vo.setPreviewUrl(generatorService.buildPreviewUrl(taskId));
            if (screenshotService.getScreenshotPath(taskId) != null) {
                vo.setScreenshotUrl("/api/v1/generator/screenshot/" + taskId);
            }
        }
        if ("FAILED".equals(task.getStatus())) {
            vo.setProgress(task.getErrorMessage());
        }
        if ("CANCELLED".equals(task.getStatus())) {
            // 对外统一按失败处理，前端无需新增状态分支
            vo.setStatus("FAILED");
            vo.setProgress("任务已被管理员终止");
        }

        return Result.success(vo);
    }

    /**
     * 查询页面最新的已完成生成结果（用于页面加载时恢复预览）
     */
    @GetMapping("/pages/{pageId}/latest")
    public Result<TaskVO> getLatestByPage(@PathVariable Long pageId, @RequestParam Long projectId) {
        GenerationTask task = generatorService.getLatestByPageId(projectId, pageId);
        if (task == null) {
            return Result.success(null);
        }

        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setPrompt(task.getPrompt());
        if ("COMPLETED".equals(task.getStatus())) {
            vo.setDownloadUrl("/api/v1/generator/download/" + task.getId());
            vo.setPreviewUrl(generatorService.buildPreviewUrl(task.getId()));
            if (screenshotService.getScreenshotPath(task.getId()) != null) {
                vo.setScreenshotUrl("/api/v1/generator/screenshot/" + task.getId());
            }
        }
        if ("FAILED".equals(task.getStatus())) {
            vo.setProgress(task.getErrorMessage());
        }
        if ("CANCELLED".equals(task.getStatus())) {
            vo.setStatus("FAILED");
            vo.setProgress("任务已被管理员终止");
        }
        return Result.success(vo);
    }

    /**
     * 获取需求分析文档（JSON 格式）
     */
    @GetMapping("/requirement/{taskId}")
    public Result<String> getRequirement(@PathVariable Long taskId) {
        Path outputPath = generatorService.getOutputPath(taskId);
        if (outputPath == null || !Files.exists(outputPath)) {
            log.warn("[需求文档] taskId={} outputPath 不存在: {}, 尝试兜底扫描...", taskId, outputPath);
            // 兜底：扫描 OUTPUT_BASE_DIR 下所有子目录，查找 requirement.json
            String content = scanForRequirement(taskId);
            if (content != null) {
                return Result.success(content);
            }
            return Result.success(null);
        }
        Path reqPath = outputPath.resolve("requirement.json");
        if (!Files.exists(reqPath)) {
            log.warn("[需求文档] taskId={} requirement.json 文件不存在: {}", taskId, reqPath);
            return Result.success(null);
        }
        try {
            String content = Files.readString(reqPath);
            log.info("[需求文档] taskId={} 读取成功, 文件大小={} bytes", taskId, content.length());
            return Result.success(content);
        } catch (IOException e) {
            log.error("[需求文档] taskId={} 读取文件失败: {}", taskId, reqPath, e);
            return Result.success(null);
        }
    }

    /**
     * 兜底扫描：按 taskId 精确查找，找不到则返回 null（不跨项目扫描）
     */
    private String scanForRequirement(Long taskId) {
        try {
            Path baseDir = Path.of(System.getProperty("java.io.tmpdir"), "htmlbuilder-generated");
            if (!Files.exists(baseDir)) {
                log.warn("[需求文档-兜底] baseDir 不存在: {}", baseDir);
                return null;
            }
            Path taskDir = baseDir.resolve(String.valueOf(taskId));
            Path reqFile = taskDir.resolve("requirement.json");
            if (Files.exists(reqFile)) {
                String content = Files.readString(reqFile);
                log.info("[需求文档-兜底] taskId={} 精确匹配到 requirement.json: {} ({} bytes)", taskId, reqFile, content.length());
                return content;
            }
            log.warn("[需求文档-兜底] taskId={} 精确目录不存在: {}", taskId, taskDir);
        } catch (IOException e) {
            log.warn("[需求文档-兜底] taskId={} 扫描失败: {}", taskId, e.getMessage());
        }
        return null;
    }

    /**
     * 获取生成网站的截图
     */
    @GetMapping(value = "/screenshot/{taskId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> screenshot(@PathVariable Long taskId) {
        Path screenshotPath = screenshotService.getScreenshotPath(taskId);
        if (screenshotPath == null || !Files.exists(screenshotPath)) {
            throw new BusinessException(2001, "截图不存在或尚未生成");
        }
        // 截图会随时间被补拍覆盖，禁止浏览器缓存旧图
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(screenshotPath));
    }

    /**
     * 补拍/重拍生成网站截图（截图缺失或临时目录被误清后手动触发）
     */
    @PostMapping("/screenshot/{taskId}")
    public Result<String> retakeScreenshot(@PathVariable Long taskId) {
        GenerationTask task = generatorService.getTask(taskId);
        if (task == null) {
            return Result.fail(2001, "任务不存在");
        }
        Path outputPath = generatorService.getOutputPath(taskId);
        if (outputPath == null || !Files.exists(outputPath)) {
            return Result.fail(2002, "生成结果已被清理，无法截图，请重新生成");
        }
        Integer port = localRunService.getPort(taskId);
        screenshotService.takeScreenshot(taskId, port != null ? "http://localhost:" + port : null);
        Path shot = screenshotService.getScreenshotPath(taskId);
        if (shot == null) {
            return Result.fail(2003, "截图失败，请查看服务端日志");
        }
        return Result.success("/api/v1/generator/screenshot/" + taskId);
    }

    /**
     * 本机启动生成的网站（Node.js 后端 + SQLite 数据库自动初始化）
     * 首次启动自动执行 npm install
     */
    @PostMapping("/run/{taskId}/start")
    public Result<RunStatusVO> startRun(@PathVariable Long taskId) {
        RunStatusVO vo = localRunService.start(taskId);
        portalService.fillPortals(taskId, vo);
        return Result.success(vo);
    }

    /**
     * 停止本机运行的网站（离开编辑器页面时前端自动调用）
     */
    @PostMapping("/run/{taskId}/stop")
    public Result<RunStatusVO> stopRun(@PathVariable Long taskId) {
        RunStatusVO vo = localRunService.stop(taskId);
        portalService.fillPortals(taskId, vo);
        return Result.success(vo);
    }

    /**
     * 查询本机运行状态
     */
    @GetMapping("/run/{taskId}/status")
    public Result<RunStatusVO> runStatus(@PathVariable Long taskId) {
        RunStatusVO vo = localRunService.status(taskId);
        portalService.fillPortals(taskId, vo);
        return Result.success(vo);
    }

    /**
     * 查询生成网站的角色端入口（教师端/学生端/管理员端等）
     */
    @GetMapping("/portals/{taskId}")
    public Result<List<PortalVO>> portals(@PathVariable Long taskId) {
        return Result.success(portalService.list(taskId));
    }
}