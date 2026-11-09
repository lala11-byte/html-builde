package com.htmlbuilder.generator.service;

import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.generator.entity.GenerationTask;
import com.htmlbuilder.generator.mapper.GenerationTaskMapper;
import com.htmlbuilder.generator.vo.RunStatusVO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 本地运行服务：在本机启动/停止用户生成的网站（Node.js 后端 + SQLite 数据库）
 *
 * 设计说明：
 * - 生成的项目是 Express + better-sqlite3（SQLite 为嵌入式数据库，随 server.js 启动自动初始化 data.db）
 * - 每个任务分配独立端口（4100 + taskId % 800），通过 PORT 环境变量注入
 * - 进程句柄保存在内存 Map 中，服务关闭时（@PreDestroy）全部回收
 * - 首次启动自动执行 npm install（超时 5 分钟）
 */
@Service
public class LocalRunService {

    private static final Logger log = LoggerFactory.getLogger(LocalRunService.class);

    private static final int BASE_PORT = 4100;
    private static final int PORT_RANGE = 800;
    private static final int LOG_CAPACITY = 100;
    private static final long NPM_INSTALL_TIMEOUT_MINUTES = 5;

    private final GenerationTaskMapper taskMapper;

    /** taskId → 运行中的进程 */
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();
    /** taskId → 分配的端口 */
    private final Map<Long, Integer> ports = new ConcurrentHashMap<>();
    /** taskId → 最近输出日志（用于状态查询和排错） */
    private final Map<Long, Deque<String>> recentLogs = new ConcurrentHashMap<>();

    private final boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

    public LocalRunService(GenerationTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 启动生成的网站（同步执行 npm install + 启动 node server.js）
     * 支持缺包自动安装 fallback：启动失败时检测 MissingModule，自动 npm install 后重试
     */
    public synchronized RunStatusVO start(Long taskId) {
        // 已在运行则直接返回状态
        Process existing = processes.get(taskId);
        if (existing != null && existing.isAlive()) {
            return buildStatus(taskId);
        }
        // 清理已退出的残留进程记录
        stop(taskId);

        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || task.getOutputPath() == null) {
            throw new BusinessException(2001, "生成任务不存在或尚未完成");
        }
        Path dir = Path.of(task.getOutputPath());
        if (!Files.exists(dir.resolve("server.js"))) {
            throw new BusinessException(2001, "生成项目中缺少 server.js，无法启动");
        }

        recentLogs.put(taskId, new ArrayDeque<>());

        int port = BASE_PORT + (int) (taskId % PORT_RANGE);

        // 端口冲突自动顺延：默认端口可能被上次的残留 node 进程占用（EADDRINUSE），
        // 此时自动向后查找空闲端口，避免启动失败
        while (!isPortAvailable(port)) {
            log.warn("taskId={} 端口 {} 已被占用，尝试顺延到下一个端口", taskId, port);
            port++;
            if (port > BASE_PORT + PORT_RANGE + 200) {
                throw new BusinessException(2002, "无可用的空闲端口（端口段 " + port + " 全部被占用）");
            }
        }

        // 首次启动需要安装依赖
        if (!Files.exists(dir.resolve("node_modules"))) {
            appendLog(taskId, "[运行] 首次启动，正在安装依赖（npm install），可能需要几分钟...");
            npmInstall(taskId, dir);
            appendLog(taskId, "[运行] 依赖安装完成");
        }

        appendLog(taskId, "[运行] 正在校验 server.js 语法...");
        validateSyntax(taskId, dir);

        // 启动重试循环：检测缺包自动安装后重试（最多3次）
        for (int attempt = 0; attempt < 3; attempt++) {
            appendLog(taskId, "[运行] 正在本机启动网站服务（端口 " + port + "）"
                    + (attempt > 0 ? " [重试第" + attempt + "次]" : ""));
            try {
                ProcessBuilder pb = new ProcessBuilder("node", "server.js");
                pb.directory(dir.toFile());
                pb.environment().put("PORT", String.valueOf(port));
                pb.redirectErrorStream(true);

                Process proc = pb.start();
                processes.put(taskId, proc);
                ports.put(taskId, port);

                // 同步读取启动阶段输出，确保捕获到错误信息
                StringBuilder startupOutput = new StringBuilder();
                BufferedReader startupReader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));

                long deadline = System.currentTimeMillis() + 3000;
                while (System.currentTimeMillis() < deadline && proc.isAlive()) {
                    if (startupReader.ready()) {
                        String line = startupReader.readLine();
                        if (line != null) {
                            startupOutput.append(line).append('\n');
                            appendLog(taskId, "[server] " + line);
                        }
                    } else {
                        Thread.sleep(100);
                    }
                }

                // 如果进程已退出，清空剩余输出
                if (!proc.isAlive()) {
                    String line;
                    while ((line = startupReader.readLine()) != null) {
                        startupOutput.append(line).append('\n');
                        appendLog(taskId, "[server] " + line);
                    }
                    startupReader.close();
                    processes.remove(taskId);
                    ports.remove(taskId);
                    String errMsg = startupOutput.length() > 0
                            ? startupOutput.toString().trim()
                            : "(无错误输出)";

                    // 检测缺包错误，自动安装缺失的包后重试
                    String missingModule = extractMissingModule(errMsg);
                    if (missingModule != null && attempt < 2) {
                        appendLog(taskId, "[运行] 检测到缺失依赖: " + missingModule + "，正在自动安装...");
                        installSinglePackage(taskId, dir, missingModule);
                        appendLog(taskId, "[运行] " + missingModule + " 安装完成，重新启动...");
                        continue;
                    }

                    throw new BusinessException(2002, "网站启动失败:\n" + errMsg);
                }

                // 进程存活，切换为异步消费剩余输出
                final BufferedReader finalReader = startupReader;
                Thread outputThread = new Thread(() -> consumeOutput(taskId, proc, finalReader));
                outputThread.setDaemon(true);
                outputThread.start();

                appendLog(taskId, "[运行] 网站已启动: http://localhost:" + port);
                log.info("taskId={} 本地运行已启动，端口 {}", taskId, port);
                return buildStatus(taskId);

            } catch (BusinessException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(2002, "启动被中断");
            } catch (Exception e) {
                if (attempt < 2) {
                    String missingModule = extractMissingModule(e.getMessage());
                    if (missingModule != null) {
                        appendLog(taskId, "[运行] 检测到缺失依赖: " + missingModule + "，正在自动安装...");
                        installSinglePackage(taskId, dir, missingModule);
                        appendLog(taskId, "[运行] " + missingModule + " 安装完成，重新启动...");
                        continue;
                    }
                }
                throw new BusinessException(2002, "网站启动失败: " + e.getMessage());
            }
        }

        throw new BusinessException(2002, "网站启动失败：多次重试后仍无法启动");
    }

    /**
     * 从错误信息中提取缺失的模块名
     * 匹配 "Cannot find module 'xxx'" 或 "MODULE_NOT_FOUND" require stack 中的模块
     */
    private String extractMissingModule(String errMsg) {
        if (errMsg == null) return null;
        // 匹配 Cannot find module 'xxx'
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "Cannot find module '([^']+)'");
        java.util.regex.Matcher matcher = pattern.matcher(errMsg);
        if (matcher.find()) {
            String moduleName = matcher.group(1);
            // 排除相对路径引用（如 './config' './db' 等），只处理 npm 包名
            if (moduleName.startsWith(".") || moduleName.startsWith("/")) {
                return null;
            }
            return moduleName;
        }
        return null;
    }

    /**
     * 安装单个 npm 包
     */
    private void installSinglePackage(Long taskId, Path dir, String packageName) {
        try {
            ProcessBuilder pb = isWindows
                    ? new ProcessBuilder("cmd", "/c", "npm", "install", packageName,
                            "--no-audit", "--no-fund", "--loglevel=error")
                    : new ProcessBuilder("npm", "install", packageName,
                            "--no-audit", "--no-fund", "--loglevel=error");
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLog(taskId, "[npm] " + line);
                }
            }
            if (!proc.waitFor(3, TimeUnit.MINUTES)) {
                proc.destroyForcibly();
                appendLog(taskId, "[npm] " + packageName + " 安装超时");
            }
        } catch (Exception e) {
            appendLog(taskId, "[npm] " + packageName + " 安装失败: " + e.getMessage());
        }
    }

    /**
     * 停止网站运行（后端进程 + SQLite 随进程关闭）
     */
    public synchronized RunStatusVO stop(Long taskId) {
        Process proc = processes.remove(taskId);
        Integer port = ports.remove(taskId);
        if (proc != null) {
            proc.destroy();
            try {
                if (!proc.waitFor(3, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                proc.destroyForcibly();
            }
            appendLog(taskId, "[运行] 网站服务已停止");
            log.info("taskId={} 本地运行已停止", taskId);
        }
        RunStatusVO vo = new RunStatusVO();
        vo.setRunning(false);
        vo.setPort(port);
        vo.setLogs(lastLogs(taskId, 30));
        return vo;
    }

    /**
     * 查询运行状态
     */
    public RunStatusVO status(Long taskId) {
        Process proc = processes.get(taskId);
        boolean running = proc != null && proc.isAlive();
        if (!running && proc != null) {
            // 进程已退出，清理记录
            processes.remove(taskId);
            ports.remove(taskId);
        }
        RunStatusVO vo = new RunStatusVO();
        vo.setRunning(running);
        if (running) {
            Integer port = ports.get(taskId);
            vo.setPort(port);
            vo.setUrl("http://localhost:" + port);
        }
        vo.setLogs(lastLogs(taskId, 30));
        return vo;
    }

    /** 服务关闭时回收所有子进程 */
    @PreDestroy
    public void shutdown() {
        processes.keySet().forEach(this::stop);
    }

    /**
     * 获取任务分配的端口号（用于截图等场景构造 live URL）
     * @return 端口号，未启动返回 null
     */
    public Integer getPort(Long taskId) {
        return ports.get(taskId);
    }

    /**
     * 检测端口是否可用（未被占用）
     * Windows 上 ServerSocket 默认带 SO_REUSEADDR，端口被占用时也能 bind 成功，
     * 所以改为主动连接：能连上就说明有服务在监听。
     */
    private boolean isPortAvailable(int port) {
        return !canConnect("127.0.0.1", port) && !canConnect("::1", port);
    }

    private boolean canConnect(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 公开的语法校验：对 server.js 执行 node --check，返回错误信息（null 表示语法正确）
     * 供 GeneratorService 在 autoStartWithFix 之前做预校验
     */
    public String checkSyntaxOnly(Long taskId, Path dir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--check", "server.js");
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder errors = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (errors.length() < 3000) {
                        errors.append(line).append('\n');
                    }
                }
            }
            if (!proc.waitFor(15, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "server.js 语法校验超时";
            }
            if (proc.exitValue() != 0) {
                appendLog(taskId, "[校验] 语法错误:\n" + errors);
                return errors.toString().trim();
            }
            appendLog(taskId, "[校验] server.js 语法校验通过");
            return null; // 语法正确
        } catch (Exception e) {
            return "语法校验执行失败: " + e.getMessage();
        }
    }

    /**
     * 启动前校验 server.js 语法（node --check），
     * 提前拦截 AI 生成代码的语法错误，给出明确的错误定位
     */
    private void validateSyntax(Long taskId, Path dir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--check", "server.js");
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder errors = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (errors.length() < 1500) {
                        errors.append(line).append('\n');
                    }
                }
            }
            if (!proc.waitFor(15, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new BusinessException(2002, "server.js 语法校验超时");
            }
            if (proc.exitValue() != 0) {
                appendLog(taskId, "[校验] 语法错误:\n" + errors);
                throw new BusinessException(2002, "生成的 server.js 存在语法错误，请重新生成网站。错误信息:\n" + errors);
            }
            appendLog(taskId, "[校验] server.js 语法校验通过");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(2002, "语法校验执行失败: " + e.getMessage());
        }
    }

    private void npmInstall(Long taskId, Path dir) {
        try {
            ProcessBuilder pb = isWindows
                    ? new ProcessBuilder("cmd", "/c", "npm", "install", "--no-audit", "--no-fund", "--loglevel=error")
                    : new ProcessBuilder("npm", "install", "--no-audit", "--no-fund", "--loglevel=error");
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(taskId, "[npm] " + line);
                    }
                } catch (Exception ignored) {
                }
            });
            outputThread.setDaemon(true);
            outputThread.start();

            if (!proc.waitFor(NPM_INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                proc.destroyForcibly();
                throw new BusinessException(2002, "npm install 超时（" + NPM_INSTALL_TIMEOUT_MINUTES + " 分钟），请检查网络");
            }
            if (proc.exitValue() != 0) {
                throw new BusinessException(2002, "npm install 失败，请查看运行日志: " + lastLogs(taskId, 10));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(2002, "npm install 被中断");
        } catch (Exception e) {
            throw new BusinessException(2002, "npm install 执行失败: " + e.getMessage());
        }
    }

    private void consumeOutput(Long taskId, Process proc, BufferedReader reader) {
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLog(taskId, "[server] " + line);
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void appendLog(Long taskId, String line) {
        Deque<String> deque = recentLogs.computeIfAbsent(taskId, k -> new ArrayDeque<>());
        if (deque.size() >= LOG_CAPACITY) {
            deque.pollFirst();
        }
        deque.addLast(line);
        log.info("[run-{}] {}", taskId, line);
    }

    private List<String> lastLogs(Long taskId, int count) {
        Deque<String> deque = recentLogs.get(taskId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            List<String> all = new ArrayList<>(deque);
            return all.subList(Math.max(0, all.size() - count), all.size());
        }
    }

    private RunStatusVO buildStatus(Long taskId) {
        return status(taskId);
    }
}
