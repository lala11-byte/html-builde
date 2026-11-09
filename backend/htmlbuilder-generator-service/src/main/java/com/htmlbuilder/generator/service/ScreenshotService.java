package com.htmlbuilder.generator.service;

import com.microsoft.playwright.*;
import com.htmlbuilder.generator.entity.GenerationTask;
import com.htmlbuilder.generator.mapper.GenerationTaskMapper;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 网站截图服务
 * 使用 Playwright 无头浏览器对生成的网站 index.html 进行截图，
 * 截图保存到项目目录下的 screenshot.png，路径存入数据库
 */
@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);
    private static final int SCREENSHOT_STATIC_PORT = 18999;

    /** public 下不是端目录的目录，选择默认端时跳过 */
    private static final Set<String> NON_PORTAL_DIRS = Set.of(
            "css", "js", "images", "img", "assets", "database", "uploads",
            "fonts", "node_modules", "static", "media");

    private static final Pattern META_REFRESH = Pattern.compile(
            "(?is)<meta[^>]+http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*content\\s*=\\s*[\"'][^\"']*?url\\s*=\\s*([^\"';]+)");

    private final GenerationTaskMapper taskMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Playwright 实例（懒加载，全局复用）
    private Playwright playwright;
    private Browser browser;

    public ScreenshotService(GenerationTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 对生成任务产出的网站首页进行截图（使用 file:// 协议，仅适用于纯静态页面）
     */
    public void takeScreenshot(Long taskId) {
        takeScreenshot(taskId, null);
    }

    /**
     * 对生成任务产出的网站首页进行截图（优先使用 liveUrl，否则使用临时静态服务器）
     * @param taskId 任务 ID
     * @param liveUrl 运行时 URL（如 http://localhost:4105），为 null 时启动临时静态服务器
     */
    public void takeScreenshot(Long taskId, String liveUrl) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || task.getOutputPath() == null) {
            log.warn("截图跳过：taskId={} 无输出路径", taskId);
            return;
        }

        Path outputDir = Path.of(task.getOutputPath());
        Path screenshotFile = outputDir.resolve("screenshot.png");
        HttpServer staticServer = null;

        try {
            initBrowser();

            // 有 liveUrl 时直接访问运行中的服务，无需检查产物目录
            if (liveUrl == null || liveUrl.isEmpty()) {
                Path publicDir = outputDir.resolve("public");
                // 根页可能不存在（只有多端目录），只要有可访问的页面就继续
                if (!Files.exists(publicDir.resolve("index.html")) && scanPortalDirs(publicDir).isEmpty()) {
                    log.warn("截图跳过（无 liveUrl 且没有可截图的页面）：taskId={}", taskId);
                    return;
                }
            }

            Browser.NewPageOptions pageOptions = new Browser.NewPageOptions();
            pageOptions.setViewportSize(1920, 1080);

            Page page = browser.newPage(pageOptions);

            boolean useLiveUrl = (liveUrl != null && !liveUrl.isEmpty());
            String url;
            if (useLiveUrl) {
                url = liveUrl;
                log.info("截图开始（live模式）：taskId={}, url={}", taskId, url);
            } else {
                // 启动临时静态服务器，通过 http 访问使页面能正常加载相对路径资源
                staticServer = startStaticServer(outputDir);
                // 根路径可能只是跳转/端选择页，直接截默认端的真实首页
                String entry = resolveEntryPath(outputDir.resolve("public"));
                url = "http://localhost:" + SCREENSHOT_STATIC_PORT + entry;
                log.info("截图开始（静态服务器模式）：taskId={}, url={}, 服务目录={}", taskId, url, outputDir.resolve("public"));
            }

            page.navigate(url);

            // 等待页面加载完成
            page.waitForLoadState();
            // 给页面渲染和数据请求足够的响应时间
            try {
                Thread.sleep(useLiveUrl ? 4000 : 2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            // 截取 viewport 首屏作为封面展示
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(screenshotFile)
                    .setFullPage(false));

            page.close();

            // 更新数据库
            task.setScreenshotPath(screenshotFile.toString());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("截图完成：taskId={}, 文件={}, 大小={} bytes", taskId, screenshotFile,
                    Files.exists(screenshotFile) ? Files.size(screenshotFile) : 0);

        } catch (Exception e) {
            log.error("截图失败：taskId={}, error={}", taskId, e.getMessage(), e);
        } finally {
            if (staticServer != null) {
                staticServer.stop(0);
            }
        }
    }

    /**
     * 启动临时静态 HTTP 服务器，映射 public/ 目录到根路径
     */
    private HttpServer startStaticServer(Path projectDir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(SCREENSHOT_STATIC_PORT), 0);
        Path publicDir = projectDir.resolve("public").toAbsolutePath().normalize();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }
            Path file = publicDir.resolve(path.substring(1)).normalize();
            // 目录请求（如 /visitor/）补上该目录的 index.html，避免落回根跳转页
            if (file.startsWith(publicDir) && Files.isDirectory(file)) {
                file = file.resolve("index.html");
            }
            if (file.startsWith(publicDir) && Files.exists(file) && !Files.isDirectory(file)) {
                byte[] bytes = Files.readAllBytes(file);
                exchange.getResponseHeaders().set("Content-Type", getContentType(file.getFileName().toString()));
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else if (file.startsWith(publicDir) && !hasExtension(path)) {
                // 前端路由（无扩展名）回落到根 index.html
                serveNotFoundOrIndex(exchange, publicDir);
            } else {
                // 静态资源确实不存在时返回 404，不能用 HTML 冒充 CSS/JS/图片
                byte[] msg = "404 Not Found".getBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(404, msg.length);
                exchange.getResponseBody().write(msg);
            }
            exchange.close();
        });
        server.setExecutor(null); // 使用默认 executor
        server.start();
        log.info("临时静态服务器已启动，端口={}, 服务目录={}", SCREENSHOT_STATIC_PORT, publicDir);
        return server;
    }

    private void serveNotFoundOrIndex(com.sun.net.httpserver.HttpExchange exchange, Path publicDir) throws IOException {
        Path indexFile = publicDir.resolve("index.html");
        if (Files.exists(indexFile)) {
            byte[] bytes = Files.readAllBytes(indexFile);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream out = exchange.getResponseBody();
            out.write(bytes);
            return;
        }
        byte[] msg = "404 Not Found".getBytes();
        exchange.sendResponseHeaders(404, msg.length);
        exchange.getResponseBody().write(msg);
    }

    private boolean hasExtension(String path) {
        int slash = path.lastIndexOf('/');
        return path.indexOf('.', slash + 1) > slash;
    }

    /**
     * 解析要截图的入口页：
     * 1. 根页是 meta refresh 跳转页时，直接截跳转目标（默认端）；
     * 2. 根页是旧的「端选择页」时，截第一个端首页，避免封面是按钮列表；
     * 3. 否则截根页。
     */
    private String resolveEntryPath(Path publicDir) {
        String target = readMetaRefreshTarget(publicDir.resolve("index.html"));
        if (target != null) {
            String normalized = normalizeEntry(publicDir, target);
            if (!"/".equals(normalized)) {
                return normalized;
            }
        }
        List<String> portals = scanPortalDirs(publicDir);
        if (portals.isEmpty()) {
            return "/";
        }
        String first = portals.get(0);
        log.info("根页不是跳转页，使用第一个端首页作为封面：{}", first);
        return "/" + first + "/";
    }

    private String readMetaRefreshTarget(Path indexFile) {
        if (!Files.exists(indexFile)) {
            return null;
        }
        try {
            Matcher m = META_REFRESH.matcher(Files.readString(indexFile));
            return m.find() ? m.group(1).trim() : null;
        } catch (Exception e) {
            log.warn("解析根页跳转目标失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 把跳转目标转换成可访问的入口路径；目标不存在时退回根路径。
     */
    private String normalizeEntry(Path publicDir, String target) {
        String t = target.trim();
        if (t.isEmpty() || t.startsWith("http://") || t.startsWith("https://") || t.startsWith("//")) {
            return "/";
        }
        int cut = t.indexOf('#');
        if (cut >= 0) {
            t = t.substring(0, cut);
        }
        cut = t.indexOf('?');
        if (cut >= 0) {
            t = t.substring(0, cut);
        }
        if (!t.startsWith("/")) {
            t = "/" + t;
        }
        Path resolved = publicDir.resolve(t.substring(1)).normalize();
        if (!resolved.startsWith(publicDir) || !Files.exists(resolved)) {
            return "/";
        }
        if (Files.isDirectory(resolved)) {
            Path inner = resolved.resolve("index.html");
            return Files.exists(inner) ? (t.endsWith("/") ? t : t + "/") : "/";
        }
        return t;
    }

    /**
     * 扫描 public/ 下的端目录（含 index.html 的直接子目录），requirement.json 声明的顺序优先。
     */
    private List<String> scanPortalDirs(Path publicDir) {
        List<String> found = new ArrayList<>();
        try (Stream<Path> children = Files.list(publicDir)) {
            children.filter(Files::isDirectory).forEach(dir -> {
                String name = dir.getFileName().toString();
                if (NON_PORTAL_DIRS.contains(name.toLowerCase())) {
                    return;
                }
                if (Files.exists(dir.resolve("index.html"))) {
                    found.add(name);
                }
            });
        } catch (Exception e) {
            log.warn("扫描端目录失败: {}", e.getMessage());
        }
        List<String> ordered = new ArrayList<>();
        for (String key : readDeclaredPortalKeys(publicDir.getParent())) {
            if (found.remove(key)) {
                ordered.add(key);
            }
        }
        ordered.addAll(found);
        return ordered;
    }

    private List<String> readDeclaredPortalKeys(Path outputPath) {
        List<String> keys = new ArrayList<>();
        Path requirement = outputPath.resolve("requirement.json");
        if (!Files.exists(requirement)) {
            return keys;
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(requirement));
            for (JsonNode node : root.path("portals")) {
                String key = node.path("key").asText("").trim();
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        } catch (Exception e) {
            log.warn("解析 requirement.json 的 portals 失败: {}", e.getMessage());
        }
        return keys;
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
        return "application/octet-stream";
    }

    /**
     * 获取截图文件路径
     */
    public Path getScreenshotPath(Long taskId) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || task.getScreenshotPath() == null) {
            return null;
        }
        Path path = Path.of(task.getScreenshotPath());
        return Files.exists(path) ? path : null;
    }

    private synchronized void initBrowser() {
        if (playwright == null) {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true));
            log.info("Playwright 浏览器已初始化");
        }
    }

    /**
     * 应用关闭时清理浏览器资源
     */
    public void shutdown() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        log.info("Playwright 浏览器已关闭");
    }
}