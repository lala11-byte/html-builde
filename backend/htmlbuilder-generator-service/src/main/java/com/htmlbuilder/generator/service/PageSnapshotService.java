package com.htmlbuilder.generator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.generator.entity.PageSnapshot;
import com.htmlbuilder.generator.mapper.PageSnapshotMapper;
import com.htmlbuilder.generator.vo.SnapshotHistoryVO;
import com.htmlbuilder.generator.vo.SnapshotVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 页面版本历史：每次内容变动写入一条快照，支持按分支查看、恢复与从任意版本建分支。
 * 快照存储完整 HTML，恢复时直接写回文件并生成一条新的快照（可再回退）。
 */
@Service
public class PageSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PageSnapshotService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_BRANCH = "main";

    private final PageSnapshotMapper mapper;
    private final GeneratorService generatorService;

    public PageSnapshotService(PageSnapshotMapper mapper, GeneratorService generatorService) {
        this.mapper = mapper;
        this.generatorService = generatorService;
    }

    /**
     * 历史列表（不含正文），首次访问时把当前文件内容登记为「初始版本」。
     * 快照按页面隔离：不传 page 时返回全部页面（仅用于拿到页面列表），
     * 传 page 时只返回该页面的版本，避免多个页面的「初始版本」混在一个列表里。
     */
    public SnapshotHistoryVO listHistory(Long taskId, String page) {
        ensureBaseline(taskId);
        List<PageSnapshot> rows = mapper.selectList(new LambdaQueryWrapper<PageSnapshot>()
                .eq(PageSnapshot::getTaskId, taskId)
                .orderByDesc(PageSnapshot::getId));
        String target = (page == null || page.isBlank()) ? null : normalizePagePath(page);
        Set<String> pageSet = new LinkedHashSet<>();
        Set<String> branchSet = new LinkedHashSet<>();
        branchSet.add(DEFAULT_BRANCH);
        List<SnapshotVO> list = new ArrayList<>();
        for (PageSnapshot row : rows) {
            if (row.getPage() != null && !row.getPage().isBlank()) {
                pageSet.add(row.getPage());
            }
            if (target != null && !target.equals(row.getPage())) {
                continue;
            }
            if (row.getBranch() != null && !row.getBranch().isBlank()) {
                branchSet.add(row.getBranch());
            }
            list.add(toVO(row, false));
        }
        return new SnapshotHistoryVO(new ArrayList<>(branchSet), new ArrayList<>(pageSet), list);
    }

    /** 快照详情（含完整 HTML） */
    public SnapshotVO detail(Long snapshotId) {
        PageSnapshot row = mapper.selectById(snapshotId);
        if (row == null) {
            throw new BusinessException(2009, "版本不存在: " + snapshotId);
        }
        return toVO(row, true);
    }

    /** 恢复快照：写回文件后在同分支追加一个新版本，保证「恢复」本身也可回溯 */
    public SnapshotVO restore(Long taskId, Long snapshotId) {
        PageSnapshot src = requireSnapshot(taskId, snapshotId);
        Path file = resolvePageFile(requireProjectDir(taskId), src.getPage());
        try {
            Files.writeString(file, src.getContent() == null ? "" : src.getContent(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(2011, "恢复版本失败: " + e.getMessage());
        }
        String msg = "恢复到版本 #" + snapshotId + "（" + safe(src.getMessage(), 60) + "）";
        return record(taskId, src.getPage(), src.getBranch(), msg);
    }

    /** 从指定版本创建新分支，分支首个版本继承该版本内容 */
    public SnapshotVO branchFrom(Long taskId, Long snapshotId, String name) {
        PageSnapshot src = requireSnapshot(taskId, snapshotId);
        String branch = normalizeBranch(name);
        Long exists = mapper.selectCount(new LambdaQueryWrapper<PageSnapshot>()
                .eq(PageSnapshot::getTaskId, taskId)
                .eq(PageSnapshot::getBranch, branch));
        if (exists != null && exists > 0) {
            throw new BusinessException(2010, "分支已存在: " + branch);
        }
        PageSnapshot snap = new PageSnapshot();
        snap.setTaskId(taskId);
        snap.setPage(src.getPage());
        snap.setBranch(branch);
        snap.setParentId(src.getId());
        snap.setMessage("从版本 #" + snapshotId + " 创建分支");
        snap.setContent(src.getContent());
        mapper.insert(snap);
        return toVO(snap, false);
    }

    /** 读取页面当前内容并生成快照 */
    public SnapshotVO record(Long taskId, String page, String branch, String message) {
        Path file = resolvePageFile(requireProjectDir(taskId), page);
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取页面失败，跳过快照 {}: {}", file, e.getMessage());
            return null;
        }
        return insert(taskId, normalizePagePath(page), normalizeBranch(branch), message, content);
    }

    private SnapshotVO insert(Long taskId, String page, String branch, String message, String content) {
        Long parentId = latestId(taskId, page, branch);
        PageSnapshot snap = new PageSnapshot();
        snap.setTaskId(taskId);
        snap.setPage(page);
        snap.setBranch(branch);
        snap.setParentId(parentId);
        snap.setMessage(safe(message, 200));
        snap.setContent(content);
        mapper.insert(snap);
        return toVO(snap, false);
    }

    /**
     * 首次使用历史时，把各页面当前内容登记为 main 分支的初始版本。
     * 加锁：并发刷新历史时，selectCount 与 insert 之间的空档会插入重复的「初始版本」。
     */
    private synchronized void ensureBaseline(Long taskId) {
        Path projectDir = generatorService.getOutputPath(taskId);
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            return;
        }
        Path publicDir = projectDir.resolve("public");
        if (!Files.isDirectory(publicDir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.walk(publicDir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".html"))
                    .toList();
        } catch (Exception e) {
            return;
        }
        for (Path file : files) {
            String relative = normalizePagePath(publicDir.relativize(file).toString());
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Long exists = mapper.selectCount(new LambdaQueryWrapper<PageSnapshot>()
                        .eq(PageSnapshot::getTaskId, taskId)
                        .eq(PageSnapshot::getPage, relative));
                if (exists == null || exists == 0) {
                    insert(taskId, relative, DEFAULT_BRANCH, "初始版本", content);
                }
            } catch (Exception e) {
                log.warn("登记初始版本失败 {}: {}", file, e.getMessage());
            }
        }
    }

    private Long latestId(Long taskId, String page, String branch) {
        List<PageSnapshot> rows = mapper.selectList(new LambdaQueryWrapper<PageSnapshot>()
                .eq(PageSnapshot::getTaskId, taskId)
                .eq(PageSnapshot::getPage, page)
                .eq(PageSnapshot::getBranch, branch)
                .orderByDesc(PageSnapshot::getId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0).getId();
    }

    private PageSnapshot requireSnapshot(Long taskId, Long snapshotId) {
        if (snapshotId == null) {
            throw new BusinessException(2009, "快照ID不能为空");
        }
        PageSnapshot src = mapper.selectById(snapshotId);
        if (src == null || !taskId.equals(src.getTaskId())) {
            throw new BusinessException(2009, "版本不存在: " + snapshotId);
        }
        return src;
    }

    private Path requireProjectDir(Long taskId) {
        Path dir = generatorService.getOutputPath(taskId);
        if (dir == null || !Files.isDirectory(dir)) {
            throw new BusinessException(2008, "生成结果不存在或已被清理");
        }
        return dir;
    }

    private Path resolvePageFile(Path projectDir, String page) {
        Path publicDir = projectDir.resolve("public").toAbsolutePath().normalize();
        String p = page == null ? "" : page.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isBlank()) {
            p = "index.html";
        }
        if (!p.toLowerCase().endsWith(".html")) {
            p = p + ".html";
        }
        Path file = publicDir.resolve(p).normalize();
        if (!file.startsWith(publicDir) || !Files.isRegularFile(file)) {
            throw new BusinessException(2002, "页面不存在: " + page);
        }
        return file;
    }

    private String normalizePagePath(String page) {
        String p = page == null ? "" : page.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isBlank()) {
            p = "index.html";
        }
        return "/" + p;
    }

    private String normalizeBranch(String branch) {
        String b = branch == null ? "" : branch.trim().replaceAll("\\s+", "-");
        if (b.isBlank()) {
            return DEFAULT_BRANCH;
        }
        if (b.length() > 60) {
            b = b.substring(0, 60);
        }
        return b;
    }

    private SnapshotVO toVO(PageSnapshot row, boolean withContent) {
        SnapshotVO vo = new SnapshotVO();
        vo.setId(row.getId());
        vo.setTaskId(row.getTaskId());
        vo.setPage(row.getPage());
        vo.setBranch(row.getBranch());
        vo.setParentId(row.getParentId());
        vo.setMessage(row.getMessage());
        vo.setCreatedAt(row.getCreatedAt() == null ? "" : row.getCreatedAt().format(TIME_FMT));
        if (withContent) {
            vo.setContent(row.getContent());
        }
        return vo;
    }

    private String safe(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}