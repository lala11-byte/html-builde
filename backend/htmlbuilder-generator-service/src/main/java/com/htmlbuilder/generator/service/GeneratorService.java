package com.htmlbuilder.generator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htmlbuilder.generator.agent.AgentOrchestrator;
import com.htmlbuilder.generator.entity.GenerationTask;
import com.htmlbuilder.generator.mapper.GenerationTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public class GeneratorService {

    private static final Logger log = LoggerFactory.getLogger(GeneratorService.class);

    private final AgentOrchestrator orchestrator;
    private final GenerationTaskMapper taskMapper;
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String OUTPUT_BASE_DIR = System.getProperty("java.io.tmpdir") + "/htmlbuilder-generated/";

    public GeneratorService(AgentOrchestrator orchestrator, GenerationTaskMapper taskMapper) {
        this.orchestrator = orchestrator;
        this.taskMapper = taskMapper;
    }

    /**
     * 提交生成任务
     */
    public Long submitTask(Long userId, Long pageId, String prompt) {
        GenerationTask task = new GenerationTask();
        task.setUserId(userId);
        task.setPageId(pageId);
        task.setPrompt(prompt);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task.getId();
    }

    /**
     * 异步执行生成任务
     */
    public void executeTaskAsync(Long taskId, SseEmitter emitter) {
        emitters.put(taskId, emitter);

        executor.submit(() -> {
            try {
                GenerationTask task = taskMapper.selectById(taskId);
                if (task == null) {
                    sendError(emitter, "任务不存在");
                    return;
                }

                task.setStatus("RUNNING");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                Consumer<String> progressCallback = msg -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(msg));
                    } catch (IOException e) {
                        log.warn("SSE send failed", e);
                    }
                };

                String outputPath = orchestrator.execute(task.getPrompt(), OUTPUT_BASE_DIR, progressCallback);

                task.setStatus("COMPLETED");
                task.setOutputPath(outputPath);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("{\"status\":\"COMPLETED\",\"downloadUrl\":\"/api/v1/generator/download/" + taskId + "\",\"previewUrl\":\"/api/v1/generator/preview/" + taskId + "/\"}"));
                emitter.complete();

            } catch (Exception e) {
                log.error("Task execution failed: {}", taskId, e);
                try {
                    GenerationTask task = taskMapper.selectById(taskId);
                    if (task != null) {
                        task.setStatus("FAILED");
                        task.setErrorMessage(e.getMessage());
                        task.setUpdatedAt(LocalDateTime.now());
                        taskMapper.updateById(task);
                    }
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("生成失败: " + e.getMessage()));
                } catch (IOException ex) {
                    log.warn("Failed to send error event", ex);
                }
                emitter.completeWithError(e);
            } finally {
                emitters.remove(taskId);
            }
        });
    }

    /**
     * 获取 SSE Emitter
     */
    public SseEmitter getEmitter(Long taskId) {
        return emitters.get(taskId);
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

    private void sendError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event().name("error").data(msg));
            emitter.complete();
        } catch (IOException e) {
            log.warn("Failed to send error", e);
        }
    }

    /**
     * 查询页面最新的已完成生成任务
     */
    public GenerationTask getLatestByPageId(Long pageId) {
        return taskMapper.selectOne(
            new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getPageId, pageId)
                .eq(GenerationTask::getStatus, "COMPLETED")
                .orderByDesc(GenerationTask::getCreatedAt)
                .last("LIMIT 1")
        );
    }
}