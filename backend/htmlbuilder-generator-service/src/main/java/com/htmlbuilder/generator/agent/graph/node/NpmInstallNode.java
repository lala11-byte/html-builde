package com.htmlbuilder.generator.agent.graph.node;

import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 依赖安装节点：执行 npm install。
 * - 成功：清空 NPM_ERROR，路由进入下一阶段。
 * - 失败/超时：记录 NPM_ERROR 并累计 NPM_RETRY_COUNT，由 NpmRouter 决定重试或放弃。
 * 单次执行不包含内部 AI 修复循环，重试由图路由控制。
 */
@Component
public class NpmInstallNode implements NodeAction<GenerationState> {

    private final ProgressRegistry progressRegistry;

    public NpmInstallNode(ProgressRegistry progressRegistry) {
        this.progressRegistry = progressRegistry;
    }

    @Override
    public Map<String, Object> apply(GenerationState state) {
        Long taskId = state.taskId();
        Consumer<String> rawCallback = progressRegistry.get(taskId);
        final Consumer<String> callback = rawCallback != null ? rawCallback : s -> {};

        String projectDir = state.projectDir();
        callback.accept("[依赖] 正在安装项目依赖（npm install），可能需要几分钟...");

        try {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            ProcessBuilder pb = isWindows
                    ? new ProcessBuilder("cmd", "/c", "npm", "install", "--no-audit", "--no-fund", "--loglevel=error")
                    : new ProcessBuilder("npm", "install", "--no-audit", "--no-fund", "--loglevel=error");
            pb.directory(Path.of(projectDir).toFile());
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            StringBuilder output = new StringBuilder();
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        callback.accept("[npm] " + line);
                        if (output.length() < 5000) {
                            output.append(line).append('\n');
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            outputThread.setDaemon(true);
            outputThread.start();

            if (!proc.waitFor(5, TimeUnit.MINUTES)) {
                proc.destroyForcibly();
                callback.accept("[依赖] npm install 超时（5 分钟），请检查网络");
                return Map.of(
                        GenerationState.NPM_ERROR, "npm install 超时",
                        GenerationState.NPM_RETRY_COUNT, state.npmRetryCount() + 1
                );
            }
            if (proc.exitValue() != 0) {
                String errorOutput = output.toString().trim();
                if (errorOutput.isEmpty()) {
                    errorOutput = "npm install 失败（退出码 " + proc.exitValue() + "）";
                }
                callback.accept("[依赖] npm install 失败（退出码 " + proc.exitValue() + "），错误输出:\n" + errorOutput);
                return Map.of(
                        GenerationState.NPM_ERROR, errorOutput,
                        GenerationState.NPM_RETRY_COUNT, state.npmRetryCount() + 1
                );
            }

            callback.accept("[依赖] 依赖安装完成");
            // 清空上一次遗留的 npm 错误，确保路由判定为成功
            Map<String, Object> result = new HashMap<>();
            result.put(GenerationState.NPM_ERROR, null);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.accept("[依赖] npm install 被中断");
            return Map.of(
                    GenerationState.NPM_ERROR, "npm install 被中断",
                    GenerationState.NPM_RETRY_COUNT, state.npmRetryCount() + 1
            );
        } catch (Exception e) {
            callback.accept("[依赖] npm install 执行失败: " + e.getMessage());
            return Map.of(
                    GenerationState.NPM_ERROR, "npm install 执行失败: " + e.getMessage(),
                    GenerationState.NPM_RETRY_COUNT, state.npmRetryCount() + 1
            );
        }
    }
}
