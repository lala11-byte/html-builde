package com.htmlbuilder.generator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htmlbuilder.generator.agent.graph.GenerationGraphFactory;
import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import com.htmlbuilder.generator.agent.graph.node.FixCodeNode;
import com.htmlbuilder.generator.agent.trace.LangSmithRun;
import com.htmlbuilder.generator.agent.trace.LangSmithTracer;
import com.htmlbuilder.generator.agent.trace.TraceRegistry;
import com.htmlbuilder.generator.entity.GenerationTask;
import com.htmlbuilder.generator.mapper.GenerationTaskMapper;
import com.htmlbuilder.generator.util.TokenUsageRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public class GeneratorService {

    private static final Logger log = LoggerFactory.getLogger(GeneratorService.class);

    private final GenerationGraphFactory graphFactory;
    private final ProgressRegistry progressRegistry;
    private final FixCodeNode fixCodeNode;
    private final GenerationTaskMapper taskMapper;
    private final ScreenshotService screenshotService;
    private final LocalRunService localRunService;
    private final LangSmithTracer langSmithTracer;
    private final TraceRegistry traceRegistry;
    private final BridgeInjectionService bridgeInjectionService;
    private final Map<Long, List<SseEmitter>> taskEmitters = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> progressLogs = new ConcurrentHashMap<>();
    private final Set<Long> executingTasks = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String OUTPUT_BASE_DIR = System.getProperty("java.io.tmpdir") + "/htmlbuilder-generated/";
    private static final int MAX_AUTO_FIX_RETRIES = 3;

    public GeneratorService(GenerationGraphFactory graphFactory, ProgressRegistry progressRegistry,
                            FixCodeNode fixCodeNode, GenerationTaskMapper taskMapper,
                            ScreenshotService screenshotService, LocalRunService localRunService,
                            LangSmithTracer langSmithTracer, TraceRegistry traceRegistry,
                            BridgeInjectionService bridgeInjectionService) {
        this.graphFactory = graphFactory;
        this.progressRegistry = progressRegistry;
        this.fixCodeNode = fixCodeNode;
        this.taskMapper = taskMapper;
        this.screenshotService = screenshotService;
        this.localRunService = localRunService;
        this.langSmithTracer = langSmithTracer;
        this.traceRegistry = traceRegistry;
        this.bridgeInjectionService = bridgeInjectionService;
    }

    /**
     * 提交生成任务
     * 三条路径：
     *  1. 同一 project+page 已有 PENDING/RUNNING 任务 → 复用（无需新建）
     *  2. 同一 project+page 已有 COMPLETED/FAILED 且 prompt 未变 → 复用（无需新建，重启即可）
     *  3. prompt 已变更（新需求） → 清理旧 COMPLETED/FAILED 任务，新建任务
     */
    public Long submitTask(Long userId, Long projectId, Long pageId, String prompt, String customPrompt) {
        if (projectId != null) {
            // 路径1：已有进行中的任务，直接复用
            GenerationTask activeTask = taskMapper.selectOne(
                    new LambdaQueryWrapper<GenerationTask>()
                            .eq(GenerationTask::getProjectId, projectId)
                            .eq(pageId != null, GenerationTask::getPageId, pageId)
                            .in(GenerationTask::getStatus, "PENDING", "RUNNING")
                            .orderByDesc(GenerationTask::getCreatedAt)
                            .last("LIMIT 1")
            );
            if (activeTask != null) {
                log.info("项目 {} 已有进行中的任务 {}，复用现有任务，不重复创建", projectId, activeTask.getId());
                return activeTask.getId();
            }

            // 路径2：已有已完成的任务，检查 prompt 是否变更（仅 COMPLETED，FAILED 不应复用）
            GenerationTask latestCompleted = taskMapper.selectOne(
                    new LambdaQueryWrapper<GenerationTask>()
                            .eq(GenerationTask::getProjectId, projectId)
                            .eq(pageId != null, GenerationTask::getPageId, pageId)
                            .eq(GenerationTask::getStatus, "COMPLETED")
                            .orderByDesc(GenerationTask::getCreatedAt)
                            .last("LIMIT 1")
            );
            if (latestCompleted != null && prompt != null && prompt.equals(latestCompleted.getPrompt())) {
                // prompt 未变，复用已有任务（网站只是中断了，重启即可）
                log.info("项目 {} 已有已完成任务 {}，prompt 未变更，复用已有任务", projectId, latestCompleted.getId());
                return latestCompleted.getId();
            }

            // 路径3：新需求（含 FAILED 任务重试）→ 清理旧的 COMPLETED/FAILED 任务
            // latestCompleted 可能为 null（只有 FAILED 无 COMPLETED 时），需独立查旧任务来清理
            List<GenerationTask> oldTasks = taskMapper.selectList(
                    new LambdaQueryWrapper<GenerationTask>()
                            .eq(GenerationTask::getProjectId, projectId)
                            .eq(pageId != null, GenerationTask::getPageId, pageId)
                            .in(GenerationTask::getStatus, "COMPLETED", "FAILED")
            );
            if (!oldTasks.isEmpty()) {
                for (GenerationTask old : oldTasks) {
                    log.info("项目 {} 需求变更/重试，清理旧任务 id={}, status={}", projectId, old.getId(), old.getStatus());
                    taskMapper.deleteById(old.getId());
                }
            }
        }

        GenerationTask task = new GenerationTask();
        task.setUserId(userId);
        task.setProjectId(projectId);
        task.setPageId(pageId);
        task.setPrompt(prompt);
        task.setCustomPrompt(customPrompt);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task.getId();
    }

    /**
     * 提交迭代优化任务（复用已有任务的输出目录和上下文）
     */
    public Long submitIterateTask(Long userId, Long existingTaskId, String additionalPrompt, String customPrompt) {
        GenerationTask existingTask = taskMapper.selectById(existingTaskId);
        if (existingTask == null) {
            throw new RuntimeException("原任务不存在");
        }

        GenerationTask task = new GenerationTask();
        task.setUserId(userId);
        task.setProjectId(existingTask.getProjectId());
        task.setPageId(existingTask.getPageId());
        task.setPrompt(existingTask.getPrompt()); // 保留原始 prompt
        task.setCustomPrompt(customPrompt != null ? customPrompt : existingTask.getCustomPrompt());
        task.setStatus("PENDING");
        task.setOutputPath(existingTask.getOutputPath()); // 复用已有的输出目录
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // 把 additionalPrompt 存到 progressLog 字段（临时存储）
        task.setProgressLog("{\"additionalPrompt\":\"" + escapeJson(additionalPrompt) + "\",\"baseTaskId\":" + existingTaskId + "}");
        taskMapper.updateById(task);

        return task.getId();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * 异步执行生成任务（支持多 emitter 广播 + 进度日志持久化）
     */
    public void executeTaskAsync(Long taskId, SseEmitter emitter) {
        // 注册 emitter 到该任务的 emitter 列表
        taskEmitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        // 如果已有历史进度日志，立即回放给新连接的 emitter（优先内存，回退 DB）
        List<String> existingLogs = getProgressLogs(taskId);
        if (existingLogs != null && !existingLogs.isEmpty()) {
            executor.submit(() -> {
                for (String logMsg : existingLogs) {
                    try {
                        emitter.send(SseEmitter.event().name("progress").data(logMsg));
                    } catch (Exception e) {
                        // emitter 已完成或断开，停止回放
                        break;
                    }
                }
            });
        }

        // 如果任务已经开始执行，不再重复提交
        if (!executingTasks.add(taskId)) {
            return;
        }

        executor.submit(() -> {
            TokenUsageRecorder.begin(taskId);
            // 进度缓冲区：累积到 20000 字符再通过 SSE 广播，避免过于频繁的输出
            StringBuilder progressBuffer = new StringBuilder();
            final int PROGRESS_FLUSH_THRESHOLD = 20000;
            LangSmithRun trace = LangSmithRun.NOOP;
            try {
                GenerationTask task = taskMapper.selectById(taskId);
                if (task == null) {
                    sendError(taskId, "任务不存在");
                    return;
                }

                if ("CANCELLED".equals(task.getStatus())) {
                    sendError(taskId, "任务已被管理员终止");
                    return;
                }
                task.setStatus("RUNNING");
                task.setUpdatedAt(LocalDateTime.now());
                // 提前设置 outputPath（使用数据库 taskId 作为目录名），确保后续 API 可定位到需求文档
                String outputPath = Path.of(OUTPUT_BASE_DIR, String.valueOf(taskId)).toString();
                task.setOutputPath(outputPath);
                taskMapper.updateById(task);

                // === LangSmith 追踪: 创建顶层 Trace ===
                var traceInputs = new LinkedHashMap<String, Object>();
                traceInputs.put("prompt", task.getPrompt());
                traceInputs.put("userId", task.getUserId());
                traceInputs.put("taskId", taskId);
                if (task.getCustomPrompt() != null && !task.getCustomPrompt().isBlank()) {
                    traceInputs.put("customPrompt", task.getCustomPrompt());
                }
                trace = langSmithTracer.startTrace("website-generate-" + taskId, traceInputs);
                traceRegistry.register(taskId, trace);

                // 从 DB 加载已有进度日志（服务重启后恢复进度），追加到新日志列表前
                List<String> logs = new ArrayList<>();
                List<String> existingDbLogs = getProgressLogs(taskId);
                if (existingDbLogs != null && !existingDbLogs.isEmpty()) {
                    logs.addAll(existingDbLogs);
                }
                progressLogs.put(taskId, logs);

                Consumer<String> progressCallback = msg -> {
                    logs.add(msg);
                    progressBuffer.append(msg).append("\n");
                    // 缓冲区达到阈值时批量广播
                    if (progressBuffer.length() >= PROGRESS_FLUSH_THRESHOLD) {
                        flushProgressBuffer(taskId, progressBuffer);
                    }
                    // 每 5 条日志持久化一次
                    if (logs.size() % 5 == 0) {
                        try {
                            saveProgressLog(taskId, logs);
                        } catch (Exception ex) {
                            log.warn("保存进度日志失败: {}", ex.getMessage());
                        }
                    }
                };

                // 注册进度回调
                progressRegistry.register(taskId, progressCallback);

                // 构建初始状态，通过 langgraph StateGraph 执行生成
                GenerationState stateForPath = new GenerationState(Map.of());
                Map<String, Object> initData = new HashMap<>();
                initData.put(GenerationState.MODE, "GENERATE");
                initData.put(GenerationState.TASK_ID, taskId);
                initData.put(GenerationState.PROMPT, task.getPrompt());
                initData.put(GenerationState.CUSTOM_PROMPT, task.getCustomPrompt() != null ? task.getCustomPrompt() : "");
                initData.put(GenerationState.PROJECT_DIR, outputPath);
                graphFactory.getGraph().invoke(initData).orElse(stateForPath);
                // 确保输出路径正确（graph 可能更新了 projectDir）
                outputPath = stateForPath.projectDir().isBlank() ? outputPath : stateForPath.projectDir();
                // 注入编辑器元素选取脚本（仅 iframe 预览生效，导出时自动剔除）
                installBridge(outputPath);

                // 最终持久化进度日志
                saveProgressLog(taskId, logs);
                // 任务完成时 flush 剩余缓冲区
                flushProgressBuffer(taskId, progressBuffer);

                // 代码审查已由 StateGraph 的 code_review 节点完成，无需额外调用

                if (isTaskCancelled(taskId)) {
                    broadcastAndCloseEmitters(taskId, "error", "任务已被管理员终止");
                    return;
                }
                task.setStatus("COMPLETED");
                task.setOutputPath(outputPath);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                // 自动启动本地服务（先启动，再截图，确保截图能捕获完整渲染内容）
                autoStartWithFix(taskId, outputPath, logs, progressCallback);
                // 启动后持久化和 flush 运行日志
                saveProgressLog(taskId, logs);
                flushProgressBuffer(taskId, progressBuffer);

                // 截图（服务启动后，通过 live URL 截图）
                try {
                    Integer port = localRunService.getPort(taskId);
                    String liveUrl = (port != null) ? "http://localhost:" + port : null;
                    screenshotService.takeScreenshot(taskId, liveUrl);
                } catch (Exception ex) {
                    log.warn("截图失败（不影响任务完成）: {}", ex.getMessage());
                }

                // === LangSmith 追踪: 标记成功 ===
                var traceOutput = new LinkedHashMap<String, Object>();
                traceOutput.put("outputPath", outputPath);
                traceOutput.put("status", "COMPLETED");
                traceOutput.put("taskId", taskId);
                trace.end(traceOutput);

                broadcastAndCloseEmitters(taskId, "complete", buildCompleteData(taskId));

            } catch (Exception e) {
                log.error("Task execution failed: {}", taskId, e);
                // 失败时 flush 剩余缓冲区，让用户看到进度
                flushProgressBuffer(taskId, progressBuffer);

                // === LangSmith 追踪: 标记失败 ===
                trace.endWithError(e.getClass().getSimpleName() + ": " + e.getMessage());
                try {
                    GenerationTask task = taskMapper.selectById(taskId);
                    if (task != null && !"CANCELLED".equals(task.getStatus())) {
                        task.setStatus("FAILED");
                        task.setErrorMessage(e.getMessage());
                        task.setUpdatedAt(LocalDateTime.now());
                        taskMapper.updateById(task);
                    }
                    broadcastAndCloseEmitters(taskId, "error", "生成失败: " + e.getMessage());
                } catch (Exception ex) {
                    log.warn("Failed to send error event", ex);
                }
            } finally {
                persistTokenUsage(taskId);
                traceRegistry.unregister(taskId);
                executingTasks.remove(taskId);
            }
        });
    }

    /**
     * 异步执行迭代优化任务
     */
    public void executeIterateAsync(Long taskId, SseEmitter emitter, String additionalPrompt, Long baseTaskId) {
        taskEmitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        if (!executingTasks.add(taskId)) {
            return;
        }

        executor.submit(() -> {
            TokenUsageRecorder.begin(taskId);
            // 进度缓冲区：累积到 20000 字符再通过 SSE 广播
            StringBuilder progressBuffer = new StringBuilder();
            final int PROGRESS_FLUSH_THRESHOLD = 20000;
            try {
                GenerationTask task = taskMapper.selectById(taskId);
                if (task == null) {
                    sendError(taskId, "任务不存在");
                    return;
                }

                if ("CANCELLED".equals(task.getStatus())) {
                    sendError(taskId, "任务已被管理员终止");
                    return;
                }
                task.setStatus("RUNNING");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                // 从 DB 加载已有进度日志（服务重启后恢复进度）
                List<String> logs = new ArrayList<>();
                List<String> existingDbLogs = getProgressLogs(taskId);
                if (existingDbLogs != null && !existingDbLogs.isEmpty()) {
                    logs.addAll(existingDbLogs);
                }
                progressLogs.put(taskId, logs);

                Consumer<String> progressCallback = msg -> {
                    logs.add(msg);
                    progressBuffer.append(msg).append("\n");
                    if (progressBuffer.length() >= PROGRESS_FLUSH_THRESHOLD) {
                        flushProgressBuffer(taskId, progressBuffer);
                    }
                    if (logs.size() % 5 == 0) {
                        try {
                            saveProgressLog(taskId, logs);
                        } catch (Exception ex) {
                            log.warn("保存进度日志失败: {}", ex.getMessage());
                        }
                    }
                };

                // 注册进度回调
                progressRegistry.register(taskId, progressCallback);

                String outputPath = task.getOutputPath();

                // 构建初始状态，通过 langgraph StateGraph 执行迭代
                Map<String, Object> initData = new HashMap<>();
                initData.put(GenerationState.MODE, "ITERATE");
                initData.put(GenerationState.TASK_ID, taskId);
                initData.put(GenerationState.PROMPT, task.getPrompt());
                initData.put(GenerationState.ADDITIONAL_PROMPT, additionalPrompt != null ? additionalPrompt : "");
                initData.put(GenerationState.CUSTOM_PROMPT, task.getCustomPrompt() != null ? task.getCustomPrompt() : "");
                initData.put(GenerationState.PROJECT_DIR, outputPath);
                initData.put(GenerationState.EXISTING_CODE_CONTEXT, com.htmlbuilder.generator.agent.graph.CodeIOUtil.collectExistingCodeContext(outputPath));
                graphFactory.getGraph().invoke(initData);

                // 注入编辑器元素选取脚本（仅 iframe 预览生效，导出时自动剔除）
                installBridge(outputPath);

                saveProgressLog(taskId, logs);
                flushProgressBuffer(taskId, progressBuffer);

                // 代码审查已由 StateGraph 的 code_review 节点完成，无需额外调用

                if (isTaskCancelled(taskId)) {
                    broadcastAndCloseEmitters(taskId, "error", "任务已被管理员终止");
                    return;
                }
                task.setStatus("COMPLETED");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                // 自动启动本地服务（先启动，再截图）
                autoStartWithFix(taskId, outputPath, logs, progressCallback);
                // 启动后持久化和 flush 运行日志
                saveProgressLog(taskId, logs);
                flushProgressBuffer(taskId, progressBuffer);

                // 截图（服务启动后，通过 live URL 截图）
                try {
                    Integer port = localRunService.getPort(taskId);
                    String liveUrl = (port != null) ? "http://localhost:" + port : null;
                    screenshotService.takeScreenshot(taskId, liveUrl);
                } catch (Exception ex) {
                    log.warn("截图失败（不影响任务完成）: {}", ex.getMessage());
                }

                broadcastAndCloseEmitters(taskId, "complete", buildCompleteData(taskId));

            } catch (Exception e) {
                log.error("Iterate task execution failed: {}", taskId, e);
                // 失败时 flush 剩余缓冲区
                flushProgressBuffer(taskId, progressBuffer);
                try {
                    GenerationTask task = taskMapper.selectById(taskId);
                    if (task != null && !"CANCELLED".equals(task.getStatus())) {
                        task.setStatus("FAILED");
                        task.setErrorMessage(e.getMessage());
                        task.setUpdatedAt(LocalDateTime.now());
                        taskMapper.updateById(task);
                    }
                    broadcastAndCloseEmitters(taskId, "error", "迭代失败: " + e.getMessage());
                } catch (Exception ex) {
                    log.warn("Failed to send error event", ex);
                }
            } finally {
                persistTokenUsage(taskId);
                traceRegistry.unregister(taskId);
                executingTasks.remove(taskId);
            }
        });
    }

    /**
     * 续接生成（服务重启后恢复 RUNNING 任务，从已有产物继续生成）
     */
    public void resumeTaskAsync(Long taskId, SseEmitter emitter) {
        taskEmitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        if (!executingTasks.add(taskId)) {
            return;
        }

        executor.submit(() -> {
            TokenUsageRecorder.begin(taskId);
            StringBuilder progressBuffer = new StringBuilder();
            final int PROGRESS_FLUSH_THRESHOLD = 20000;
            try {
                GenerationTask task = taskMapper.selectById(taskId);
                if (task == null) {
                    sendError(taskId, "任务不存在");
                    return;
                }

                if ("CANCELLED".equals(task.getStatus())) {
                    sendError(taskId, "任务已被管理员终止");
                    return;
                }
                task.setStatus("RUNNING");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                List<String> logs = new ArrayList<>();
                List<String> existingDbLogs = getProgressLogs(taskId);
                if (existingDbLogs != null && !existingDbLogs.isEmpty()) {
                    logs.addAll(existingDbLogs);
                }
                progressLogs.put(taskId, logs);

                Consumer<String> progressCallback = msg -> {
                    logs.add(msg);
                    progressBuffer.append(msg).append("\n");
                    if (progressBuffer.length() >= PROGRESS_FLUSH_THRESHOLD) {
                        flushProgressBuffer(taskId, progressBuffer);
                    }
                    if (logs.size() % 5 == 0) {
                        try {
                            saveProgressLog(taskId, logs);
                        } catch (Exception ex) {
                            log.warn("保存进度日志失败: {}", ex.getMessage());
                        }
                    }
                };

                // 确定项目目录
                String projectDir;
                if (task.getOutputPath() != null && !task.getOutputPath().isEmpty()) {
                    projectDir = task.getOutputPath();
                } else {
                    projectDir = OUTPUT_BASE_DIR + taskId;
                    task.setOutputPath(projectDir);
                    taskMapper.updateById(task);
                }

                // 注册进度回调
                progressRegistry.register(taskId, progressCallback);

                // 构建初始状态，通过 langgraph StateGraph 执行续接生成
                Map<String, Object> initData = new HashMap<>();
                initData.put(GenerationState.MODE, "RESUME");
                initData.put(GenerationState.TASK_ID, taskId);
                initData.put(GenerationState.PROMPT, task.getPrompt());
                initData.put(GenerationState.CUSTOM_PROMPT, task.getCustomPrompt() != null ? task.getCustomPrompt() : "");
                initData.put(GenerationState.PROJECT_DIR, projectDir);
                initData.put(GenerationState.EXISTING_CODE_CONTEXT, com.htmlbuilder.generator.agent.graph.CodeIOUtil.collectExistingCodeContext(projectDir));
                GenerationState resultState = graphFactory.getGraph().invoke(initData).orElse(null);
                String outputPath = (resultState != null && !resultState.projectDir().isBlank())
                        ? resultState.projectDir() : projectDir;

                // 注入编辑器元素选取脚本（仅 iframe 预览生效，导出时自动剔除）
                installBridge(outputPath);

                saveProgressLog(taskId, logs);
                flushProgressBuffer(taskId, progressBuffer);

                // 代码审查已由 StateGraph 的 code_review 节点完成，无需额外调用

                if (isTaskCancelled(taskId)) {
                    broadcastAndCloseEmitters(taskId, "error", "任务已被管理员终止");
                    return;
                }
                task.setStatus("COMPLETED");
                task.setOutputPath(outputPath);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                // 自动启动本地服务（先启动，再截图）
                autoStartWithFix(taskId, outputPath, logs, progressCallback);
                // 启动后持久化和 flush 运行日志
                saveProgressLog(taskId, logs);
                flushProgressBuffer(taskId, progressBuffer);

                // 截图（服务启动后，通过 live URL 截图）
                try {
                    Integer port = localRunService.getPort(taskId);
                    String liveUrl = (port != null) ? "http://localhost:" + port : null;
                    screenshotService.takeScreenshot(taskId, liveUrl);
                } catch (Exception ex) {
                    log.warn("截图失败（不影响任务完成）: {}", ex.getMessage());
                }

                broadcastAndCloseEmitters(taskId, "complete", buildCompleteData(taskId));

            } catch (Exception e) {
                log.error("Resume task execution failed: {}", taskId, e);
                flushProgressBuffer(taskId, progressBuffer);
                try {
                    GenerationTask task = taskMapper.selectById(taskId);
                    if (task != null && !"CANCELLED".equals(task.getStatus())) {
                        task.setStatus("FAILED");
                        task.setErrorMessage(e.getMessage());
                        task.setUpdatedAt(LocalDateTime.now());
                        taskMapper.updateById(task);
                    }
                    broadcastAndCloseEmitters(taskId, "error", "续接生成失败: " + e.getMessage());
                } catch (Exception ex) {
                    log.warn("Failed to send error event", ex);
                }
            } finally {
                persistTokenUsage(taskId);
                traceRegistry.unregister(taskId);
                executingTasks.remove(taskId);
            }
        });
    }

    /** 管理员是否已终止该任务（异步执行中的协作式取消检查） */
    private boolean isTaskCancelled(Long taskId) {
        try {
            GenerationTask current = taskMapper.selectById(taskId);
            return current != null && "CANCELLED".equals(current.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 自动启动 + 语法修复 + 运行修复
     * 阶段1：先做语法校验，有语法错误就 AI 修复（最多 5 次）
     * 阶段2：语法通过后，启动服务，失败就 AI 修复（最多 3 次）
     */
    private void autoStartWithFix(Long taskId, String projectDir, List<String> logs, Consumer<String> progressCallback) {
        Path dir = Path.of(projectDir);
        if (!Files.exists(dir.resolve("server.js"))) {
            progressCallback.accept("[运行] 项目中没有 server.js，跳过启动");
            return;
        }

        // 阶段1：语法校验 + 修复循环（最多 5 次）
        int maxSyntaxFixes = 5;
        for (int attempt = 1; attempt <= maxSyntaxFixes; attempt++) {
            progressCallback.accept("[运行] 正在校验 server.js 语法（第" + attempt + "次）...");
            String syntaxError = localRunService.checkSyntaxOnly(taskId, dir);
            if (syntaxError == null) {
                progressCallback.accept("[运行] server.js 语法校验通过");
                break;
            }
            progressCallback.accept("[校验] 语法错误:\n" + syntaxError);
            logs.add("[校验] 语法错误: " + syntaxError);

            if (attempt < maxSyntaxFixes) {
                progressCallback.accept("[修复] 第 " + attempt + " 次尝试 AI 修复语法错误...");
                try {
                    fixCodeNode.fix(projectDir, syntaxError, progressCallback);
                    saveProgressLog(taskId, logs);
                } catch (Exception fixEx) {
                    progressCallback.accept("[修复] AI 修复失败: " + fixEx.getMessage());
                    logs.add("[修复] AI 修复失败: " + fixEx.getMessage());
                    break;
                }
            } else {
                progressCallback.accept("[运行] 语法修复已尝试 " + maxSyntaxFixes + " 次，仍有错误，跳过启动");
                logs.add("[运行] 语法修复已尝试 " + maxSyntaxFixes + " 次，仍有错误");
                return;
            }
        }

        // 阶段2：启动服务 + 运行修复循环（最多 3 次）
        for (int attempt = 1; attempt <= MAX_AUTO_FIX_RETRIES; attempt++) {
            try {
                progressCallback.accept("[运行] 正在启动本地服务（第" + attempt + "次尝试）...");
                localRunService.start(taskId);
                progressCallback.accept("[运行] 本地服务启动成功！");
                return;
            } catch (com.htmlbuilder.common.exception.BusinessException e) {
                String errorMsg = e.getMessage();
                progressCallback.accept("[运行] 启动失败: " + errorMsg);
                logs.add("[运行] 启动失败: " + errorMsg);

                if (attempt < MAX_AUTO_FIX_RETRIES) {
                    progressCallback.accept("[修复] 第 " + attempt + " 次尝试 AI 修复代码...");
                    try {
                        fixCodeNode.fix(projectDir, errorMsg, progressCallback);
                        saveProgressLog(taskId, logs);
                    } catch (Exception fixEx) {
                        progressCallback.accept("[修复] AI 修复失败: " + fixEx.getMessage());
                        logs.add("[修复] AI 修复失败: " + fixEx.getMessage());
                        break;
                    }
                } else {
                    progressCallback.accept("[运行] 已尝试 " + MAX_AUTO_FIX_RETRIES + " 次，自动修复失败，请手动检查");
                    logs.add("[运行] 已尝试 " + MAX_AUTO_FIX_RETRIES + " 次，自动修复失败");
                }
            } catch (Exception e) {
                progressCallback.accept("[运行] 启动异常: " + e.getMessage());
                logs.add("[运行] 启动异常: " + e.getMessage());
                break;
            }
        }
    }

    private void saveProgressLog(Long taskId, List<String> logs) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(logs);
            GenerationTask task = taskMapper.selectById(taskId);
            if (task != null) {
                task.setProgressLog(json);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        } catch (Exception e) {
            log.warn("序列化进度日志失败", e);
        }
    }

    /**
     * 从任务的 emitter 列表中移除指定 emitter（由 onCompletion/onTimeout/onError 回调触发）
     */
    public void removeEmitter(Long taskId, SseEmitter emitter) {
        List<SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    /**
     * 将缓冲区中的进度消息批量广播到所有连接的 SSE emitter，然后清空缓冲区。
     */
    private void flushProgressBuffer(Long taskId, StringBuilder buffer) {
        if (buffer.length() == 0) return;
        String batchMsg = buffer.toString();
        buffer.setLength(0);
        List<SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters != null) {
            for (SseEmitter em : emitters) {
                try {
                    em.send(SseEmitter.event().name("progress").data(batchMsg.trim()));
                } catch (Exception e) {
                    // emitter 已完成或断开，忽略
                }
            }
        }
    }

    private void broadcastAndCloseEmitters(Long taskId, String eventName, String data) {
        List<SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters != null) {
            for (SseEmitter em : emitters) {
                try {
                    em.send(SseEmitter.event().name(eventName).data(data));
                } catch (Exception e) {
                    // emitter 已完成或断开，发送失败
                }
                try {
                    em.complete();
                } catch (Exception e) {
                    // emitter 已经完成，忽略
                }
            }
            taskEmitters.remove(taskId);
        }
    }

    /**
     * 构造完成事件 JSON，优先使用运行中的 liveUrl 作为预览地址
     */
    private String buildCompleteData(Long taskId) {
        StringBuilder data = new StringBuilder("{\"status\":\"COMPLETED\",\"downloadUrl\":\"/api/v1/generator/download/")
                .append(taskId).append("\",\"previewUrl\":\"").append(buildPreviewUrl(taskId)).append('"');
        if (screenshotService.getScreenshotPath(taskId) != null) {
            data.append(",\"screenshotUrl\":\"/api/v1/generator/screenshot/").append(taskId).append('"');
        }
        return data.append('}').toString();
    }

    /**
     * 解析预览 URL：优先 liveUrl，回退静态预览路径
     */
    public String buildPreviewUrl(Long taskId) {
        Integer port = localRunService.getPort(taskId);
        if (port != null) {
            return "http://localhost:" + port;
        }
        return "/api/v1/generator/preview/" + taskId + "/";
    }

    /**
     * 获取已持久化的进度日志
     */
    public List<String> getProgressLogs(Long taskId) {
        // 先检查内存
        List<String> logs = progressLogs.get(taskId);
        if (logs != null) return logs;
        // 从数据库加载
        GenerationTask task = taskMapper.selectById(taskId);
        if (task != null && task.getProgressLog() != null) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        task.getProgressLog(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("反序列化进度日志失败", e);
            }
        }
        return null;
    }

    /**
     * 获取任务状态
     */
    public GenerationTask getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    /**
     * 获取输出文件路径
     */
    public Path getOutputPath(Long taskId) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || task.getOutputPath() == null) {
            return null;
        }
        return Path.of(task.getOutputPath());
    }

    /** 注入编辑器元素选取脚本，失败不影响生成主流程 */
    private void installBridge(String outputPath) {
        try {
            bridgeInjectionService.install(Path.of(outputPath));
        } catch (Exception e) {
            log.warn("注入元素选取脚本失败（不影响生成）: {}", e.getMessage());
        }
    }

    private void sendError(Long taskId, String msg) {
        broadcastAndCloseEmitters(taskId, "error", msg);
    }

    /**
     * 查询页面最新的生成任务（按 projectId + pageId 联合查询，确保项目隔离）
     * 兼容已有数据 project_id 为 NULL 的情况
     */
    public GenerationTask getLatestByPageId(Long projectId, Long pageId) {
        // 返回该项目/页面下最新的生成任务（可能是进行中、成功或失败）
        // 注意：不要按状态过滤——需求更新后会生成新任务，刷新时必须展示最新任务以续接进度
        GenerationTask task = taskMapper.selectOne(
            new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getProjectId, projectId)
                .eq(GenerationTask::getPageId, pageId)
                .orderByDesc(GenerationTask::getCreatedAt)
                .last("LIMIT 1")
        );
        if (task != null) return task;
        // 兼容旧数据 project_id 为 NULL 的情况
        return taskMapper.selectOne(
            new LambdaQueryWrapper<GenerationTask>()
                .isNull(GenerationTask::getProjectId)
                .eq(GenerationTask::getPageId, pageId)
                .orderByDesc(GenerationTask::getCreatedAt)
                .last("LIMIT 1")
        );
    }

    /** 把本次任务累计的 AI token 用量写回任务表 */
    private void persistTokenUsage(Long taskId) {
        long[] usage = TokenUsageRecorder.finish(taskId);
        TokenUsageRecorder.clear();
        if (usage == null) {
            return;
        }
        try {
            GenerationTask task = taskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            task.setModelCalls((int) usage[0]);
            task.setPromptTokens((int) usage[1]);
            task.setCompletionTokens((int) usage[2]);
            task.setTotalTokens((int) usage[3]);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            log.info("taskId={} token 用量: calls={}, prompt={}, completion={}, total={}",
                    taskId, usage[0], usage[1], usage[2], usage[3]);
        } catch (Exception e) {
            log.warn("写入 token 用量失败 taskId={}: {}", taskId, e.getMessage());
        }
    }
}