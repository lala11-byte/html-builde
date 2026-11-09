package com.htmlbuilder.generator.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 编辑器元素选取桥接脚本注入服务。
 * 生成的站点本身是原生 HTML，父页面受 iframe 跨源限制无法读取 DOM，
 * 因此把 bridge 脚本内联进每个页面，通过 postMessage 回传选中元素信息。
 * 脚本只在 iframe 中生效（window.top !== window.self），导出时会从 ZIP 中剔除。
 */
@Service
public class BridgeInjectionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeInjectionService.class);

    public static final String MARKER = "data-hb-bridge";

    /** 匹配已注入的 bridge 脚本（含内容），用于幂等判断与导出剔除 */
    public static final Pattern BRIDGE_SCRIPT = Pattern.compile("(?is)<script[^>]*data-hb-bridge[^>]*>.*?</script>\\s*");

    private String bridgeJs = "";

    @PostConstruct
    public void init() {
        try (var in = new ClassPathResource("bridge/hbuilder-bridge.js").getInputStream()) {
            bridgeJs = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取 bridge 脚本失败，编辑器元素选取将不可用", e);
        }
    }

    /**
     * 给项目下所有 HTML 注入 bridge 脚本（已注入的跳过）。
     */
    public void install(Path projectDir) {
        if (bridgeJs.isBlank() || projectDir == null || !Files.isDirectory(projectDir)) {
            return;
        }
        Path publicDir = projectDir.resolve("public");
        if (!Files.isDirectory(publicDir)) {
            return;
        }
        List<Path> htmlFiles;
        try (Stream<Path> stream = Files.walk(publicDir)) {
            htmlFiles = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".html"))
                    .toList();
        } catch (IOException e) {
            log.warn("扫描 HTML 文件失败: {}", e.getMessage());
            return;
        }
        int injected = 0;
        for (Path file : htmlFiles) {
            try {
                String html = Files.readString(file, StandardCharsets.UTF_8);
                if (BRIDGE_SCRIPT.matcher(html).find()) {
                    // 已注入但脚本可能来自旧版本，先移除再注入
                    html = BRIDGE_SCRIPT.matcher(html).replaceAll("");
                }
                String tag = "<script " + MARKER + "=\"1\">\n" + bridgeJs + "\n</script>\n";
                String lower = html.toLowerCase();
                int idx = lower.lastIndexOf("</body>");
                if (idx < 0) {
                    idx = lower.lastIndexOf("</html>");
                }
                String updated = idx < 0 ? html + tag : html.substring(0, idx) + tag + html.substring(idx);
                Files.writeString(file, updated, StandardCharsets.UTF_8);
                injected++;
            } catch (Exception e) {
                log.warn("注入 bridge 失败 {}: {}", file, e.getMessage());
            }
        }
        if (injected > 0) {
            log.info("已为 {} 个页面注入元素选取脚本: {}", injected, projectDir);
        }
    }

    /** 移除页面中的 bridge 脚本（导出前使用） */
    public static String removeBridge(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher m = BRIDGE_SCRIPT.matcher(html);
        return m.replaceAll("");
    }
}