package com.htmlbuilder.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htmlbuilder.generator.vo.PortalVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 角色端入口服务：扫描生成产物中的多端目录，输出各端入口地址。
 *
 * 约定（单服务多 URL）：
 * - public/index.html 仅为根路径跳转页，指向默认端
 * - public/&lt;key&gt;/index.html 为各端入口页
 * - 所有端共享同一套 /api 接口与数据库
 */
@Service
public class PortalService {

    private static final Logger log = LoggerFactory.getLogger(PortalService.class);

    /** public 下的非端目录，扫描时跳过 */
    private static final Set<String> RESERVED_DIRS = Set.of(
            "css", "js", "images", "img", "assets", "database", "uploads",
            "fonts", "node_modules", "static", "media");

    private final GeneratorService generatorService;
    private final LocalRunService localRunService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PortalService(GeneratorService generatorService, LocalRunService localRunService) {
        this.generatorService = generatorService;
        this.localRunService = localRunService;
    }

    /**
     * 列出某个生成任务的角色端入口；无端目录时返回空列表（兼容旧的单页产物）。
     */
    public List<PortalVO> list(Long taskId) {
        Path outputPath = generatorService.getOutputPath(taskId);
        if (outputPath == null || !Files.isDirectory(outputPath)) {
            return List.of();
        }
        Path publicDir = outputPath.resolve("public");
        if (!Files.isDirectory(publicDir)) {
            return List.of();
        }

        List<PortalVO> result = new ArrayList<>();
        // 1. requirement.json 中声明的端（保持声明顺序与中文名）
        Map<String, String> declared = readDeclaredPortals(outputPath);
        // 2. public 下的实际端目录
        Set<String> actualDirs = scanPortalDirs(publicDir);

        Integer port = localRunService.getPort(taskId);

        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String key = entry.getKey();
            if (!actualDirs.contains(key)) {
                continue;
            }
            result.add(build(taskId, key, entry.getValue(), port));
        }
        // 未被 requirement 声明但实际存在的端目录，追加到末尾（名称用 key）
        for (String key : actualDirs) {
            if (!declared.containsKey(key)) {
                result.add(build(taskId, key, key, port));
            }
        }
        return result;
    }

    /**
     * 填充运行状态中的端入口列表。
     */
    public void fillPortals(Long taskId, com.htmlbuilder.generator.vo.RunStatusVO vo) {
        if (vo == null) {
            return;
        }
        try {
            vo.setPortals(list(taskId));
        } catch (Exception e) {
            log.warn("填充端入口失败 taskId={}", taskId, e);
            vo.setPortals(List.of());
        }
    }

    private PortalVO build(Long taskId, String key, String name, Integer port) {
        PortalVO vo = new PortalVO();
        vo.setKey(key);
        vo.setName(name == null || name.isBlank() ? key : name);
        vo.setPath(key + "/");
        vo.setUrl(port != null
                ? "http://localhost:" + port + "/" + key + "/"
                : "/api/v1/generator/preview/" + taskId + "/" + key + "/");
        return vo;
    }

    /**
     * 读取 requirement.json 中的 portals 声明，返回有序的 key -> 中文名。
     */
    private Map<String, String> readDeclaredPortals(Path outputPath) {
        Map<String, String> portals = new LinkedHashMap<>();
        Path requirement = outputPath.resolve("requirement.json");
        if (!Files.exists(requirement)) {
            return portals;
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(requirement));
            JsonNode nodes = root.path("portals");
            if (!nodes.isArray()) {
                return portals;
            }
            for (JsonNode node : nodes) {
                String key = node.path("key").asText("").trim().toLowerCase();
                if (key.isEmpty()) {
                    continue;
                }
                String name = node.path("name").asText("").trim();
                portals.putIfAbsent(key, name.isEmpty() ? key : name);
            }
        } catch (Exception e) {
            log.warn("解析 requirement.json 的 portals 失败: {}", e.getMessage());
        }
        return portals;
    }

    /**
     * 扫描 public/ 下包含 index.html 的直接子目录，作为端目录。
     */
    private Set<String> scanPortalDirs(Path publicDir) {
        Set<String> dirs = new LinkedHashSet<>();
        try (Stream<Path> children = Files.list(publicDir)) {
            children.filter(Files::isDirectory)
                    .forEach(dir -> {
                        String name = dir.getFileName().toString();
                        if (RESERVED_DIRS.contains(name.toLowerCase())) {
                            return;
                        }
                        if (Files.exists(dir.resolve("index.html"))) {
                            dirs.add(name);
                        }
                    });
        } catch (Exception e) {
            log.warn("扫描端目录失败: {}", e.getMessage());
        }
        return dirs;
    }
}