package com.htmlbuilder.generator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.generator.agent.FastChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.PromptConstants;
import com.htmlbuilder.generator.component.ComponentItem;
import com.htmlbuilder.generator.entity.ComponentMaterial;
import com.htmlbuilder.generator.mapper.ComponentMaterialMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 素材服务：素材本身就是 registry（MCP 同源）里的真实组件，而不是平台内置的模板。
 * <p>
 * 列表只返回 registry 条目的元信息；用户真正预览/使用某个素材时才去拉源码，
 * 由 AI 把 React + Tailwind 适配成可插入普通页面的原生 HTML/CSS/JS，并落库缓存。
 * 这样组件库提供的是「难描述、生成成本高」的现成设计，而不是简单拼装的区块。
 */
@Service
public class RegistryMaterialService {

    private static final Logger log = LoggerFactory.getLogger(RegistryMaterialService.class);

    private static final String REGISTRY = "shadcn";
    private static final String SOURCE = "shadcn";
    private static final String ID_PREFIX = "mcp-";
    /** shadcn registry 单项详情（与 MCP 服务读取的是同一份数据） */
    private static final String ITEM_ENDPOINT = "https://ui.shadcn.com/r/styles/new-york-v4/";
    /** 源码超长（如 dashboard-01）时截断，避免提示词与输出 token 失控 */
    private static final int MAX_SOURCE_CHARS = 20000;

    private final ComponentMaterialMapper mapper;
    private final FastChatLanguageModel fastChatLanguageModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CatalogItem> catalog = new LinkedHashMap<>();
    /** 已适配好的素材：启动时从库预加载，服务重启后无需再调用 AI */
    private final Map<String, ComponentItem> ready = new ConcurrentHashMap<>();
    /** 同一素材并发预览时只转译一次 */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public RegistryMaterialService(ComponentMaterialMapper mapper, FastChatLanguageModel fastChatLanguageModel) {
        this.mapper = mapper;
        this.fastChatLanguageModel = fastChatLanguageModel;
        for (CatalogItem item : CATALOG) {
            catalog.put(item.id(), item);
        }
    }

    /**
     * 服务启动后把库里已适配好的素材全部载入内存。
     * 适配结果持久化在 component_material，重启后直接可用，不再重复消耗 token。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void preload() {
        try {
            List<ComponentMaterial> rows = mapper.selectList(new LambdaQueryWrapper<ComponentMaterial>()
                    .eq(ComponentMaterial::getRegistry, REGISTRY)
                    .eq(ComponentMaterial::getStatus, "READY"));
            for (ComponentMaterial row : rows) {
                CatalogItem item = catalog.get(ID_PREFIX + row.getItem());
                if (item != null && isReady(row)) {
                    ready.put(item.id(), toComponent(item, row));
                }
            }
            log.info("MCP 素材已就绪 {} 个（启动预加载，无需重复适配）", ready.size());
        } catch (Exception e) {
            log.warn("MCP 素材启动预加载失败，将在首次使用时按需适配: {}", e.getMessage());
        }
    }

    /** 素材目录（来自 registry 的条目，不含适配后的代码，前端悬停时再按需拉取） */
    public List<ComponentItem> listMeta() {
        List<ComponentItem> list = new ArrayList<>();
        for (CatalogItem item : catalog.values()) {
            ComponentItem meta = new ComponentItem();
            meta.setId(item.id());
            meta.setName(item.name());
            meta.setCategory(item.category());
            meta.setDescription(item.description());
            meta.setKind("block");
            meta.setSlot(item.slot());
            meta.setSource(SOURCE);
            list.add(meta);
        }
        return list;
    }

    /**
     * 取素材详情：命中缓存直接返回；否则从 registry 拉源码并交给 AI 适配，成功后写库。
     * 失败会抛出业务异常，由前端提示「该素材适配失败」。
     */
    public ComponentItem get(String id) {
        CatalogItem item = catalog.get(normalizeId(id));
        if (item == null) {
            return null;
        }
        ComponentItem loaded = ready.get(item.id());
        if (loaded != null) {
            return loaded;
        }
        ComponentMaterial cached = find(item);
        if (isReady(cached)) {
            return toComponent(item, cached);
        }
        Object lock = locks.computeIfAbsent(item.id(), k -> new Object());
        synchronized (lock) {
            cached = find(item);
            if (isReady(cached)) {
                return toComponent(item, cached);
            }
            try {
                JsonNode result = translate(item);
                save(item, result);
            } catch (BusinessException e) {
                markFailed(item, e.getMessage());
                throw e;
            }
            ComponentMaterial saved = find(item);
            if (!isReady(saved)) {
                throw new BusinessException(2012, "素材适配失败，请稍后重试");
            }
            return toComponent(item, saved);
        }
    }

    private String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        String value = id.trim();
        return value.startsWith(ID_PREFIX) ? value : ID_PREFIX + value;
    }

    private ComponentMaterial find(CatalogItem item) {
        List<ComponentMaterial> rows = mapper.selectList(new LambdaQueryWrapper<ComponentMaterial>()
                .eq(ComponentMaterial::getRegistry, REGISTRY)
                .eq(ComponentMaterial::getItem, item.item())
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean isReady(ComponentMaterial row) {
        return row != null && "READY".equals(row.getStatus())
                && row.getHtml() != null && !row.getHtml().isBlank();
    }

    /** 拉 registry 源码 → AI 适配为原生 HTML/CSS/JS */
    private JsonNode translate(CatalogItem item) {
        String source = fetchSource(item.item());
        String prompt = PromptConstants.MATERIAL_TRANSLATE
                + "\n\n【组件】" + item.item() + "（" + item.description() + "）"
                + "\n【registry 源码】\n" + source;
        String raw;
        try {
            raw = fastChatLanguageModel.chat(prompt);
        } catch (Exception e) {
            throw new BusinessException(2013, "组件适配 AI 调用失败: " + e.getMessage());
        }
        String json = CodeIOUtil.extractJson(CodeIOUtil.stripCodeFences(raw));
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("素材 {} 适配结果无法解析为 JSON: {}", item.item(), raw);
            throw new BusinessException(2012, "组件适配结果格式不正确，请重试");
        }
        String html = text(node, "html");
        if (html == null || html.isBlank()) {
            throw new BusinessException(2012, "组件适配后没有可用结构，请重试");
        }
        return node;
    }

    private String fetchSource(String name) {
        String url = ITEM_ENDPOINT + name + ".json";
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header("User-Agent", "htmlbuilder-generator")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + response.statusCode());
                }
                return concatFiles(objectMapper.readTree(response.body()));
            } catch (Exception e) {
                last = e;
                log.warn("拉取 MCP 组件失败 {}/3 {}: {}", attempt + 1, name, e.getMessage());
                sleep((attempt + 1) * 800L);
            }
        }
        throw new BusinessException(2012, "从 MCP registry 拉取组件失败: "
                + (last == null ? name : last.getMessage()));
    }

    private String concatFiles(JsonNode root) {
        JsonNode files = root == null ? null : root.get("files");
        if (files == null || !files.isArray() || files.isEmpty()) {
            throw new IllegalStateException("registry 未返回组件源码");
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode file : files) {
            String content = file.path("content").asText("");
            if (content.isBlank()) {
                continue;
            }
            sb.append("\n===== ").append(file.path("path").asText("file")).append(" =====\n").append(content);
            if (sb.length() >= MAX_SOURCE_CHARS) {
                break;
            }
        }
        String text = sb.toString();
        return text.length() > MAX_SOURCE_CHARS ? text.substring(0, MAX_SOURCE_CHARS) : text;
    }

    private void save(CatalogItem item, JsonNode result) {
        ComponentMaterial row = find(item);
        boolean insert = row == null;
        if (insert) {
            row = new ComponentMaterial();
            row.setRegistry(REGISTRY);
            row.setItem(item.item());
        }
        row.setName(item.name());
        row.setDescription(item.description());
        row.setCategory(item.category());
        row.setSlot(item.slot());
        row.setHtml(text(result, "html"));
        row.setCss(text(result, "css"));
        row.setJs(text(result, "js"));
        row.setStatus("READY");
        row.setError(null);
        if (insert) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
        ready.put(item.id(), toComponent(item, row));
        log.info("MCP 素材适配完成并缓存: {}/{}", REGISTRY, item.item());
    }

    private void markFailed(CatalogItem item, String message) {
        ready.remove(item.id());
        try {
            ComponentMaterial row = find(item);
            boolean insert = row == null;
            if (insert) {
                row = new ComponentMaterial();
                row.setRegistry(REGISTRY);
                row.setItem(item.item());
            }
            row.setName(item.name());
            row.setCategory(item.category());
            row.setSlot(item.slot());
            row.setStatus("FAILED");
            row.setError(message == null ? "适配失败" : message.substring(0, Math.min(message.length(), 480)));
            if (insert) {
                mapper.insert(row);
            } else {
                mapper.updateById(row);
            }
        } catch (Exception e) {
            log.warn("记录素材适配失败状态出错: {}", e.getMessage());
        }
    }

    private ComponentItem toComponent(CatalogItem item, ComponentMaterial row) {
        ComponentItem component = new ComponentItem();
        component.setId(item.id());
        component.setName(item.name());
        component.setCategory(item.category());
        component.setDescription(item.description());
        component.setKind("block");
        component.setSlot(item.slot());
        component.setSource(SOURCE);
        component.setHtml(row.getHtml());
        component.setCss(row.getCss());
        component.setJs(row.getJs());
        return component;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 素材目录：条目名与 registry 完全一致，展示信息补中文 */
    private record CatalogItem(String item, String name, String category, String slot, String description) {
        String id() {
            return ID_PREFIX + item;
        }
    }

    private static final List<CatalogItem> CATALOG = List.of(
            // 登录 / 注册：整块认证页面，手写成本高，最适合直接套用
            new CatalogItem("login-01", "极简登录卡片", "登录注册", "flow", "居中卡片式登录，邮箱与密码输入、记住我、第三方登录入口"),
            new CatalogItem("login-02", "分栏登录 · 品牌介绍", "登录注册", "flow", "左右分栏登录页，左侧品牌说明、右侧登录表单"),
            new CatalogItem("login-03", "登录 · 社交账号", "登录注册", "flow", "带第三方登录按钮的居中登录卡片"),
            new CatalogItem("login-04", "双栏登录 · 产品展示", "登录注册", "flow", "右侧展示产品卖点的双栏登录页"),
            new CatalogItem("login-05", "深色品牌登录", "登录注册", "flow", "深色渐变背景的品牌风登录页"),
            new CatalogItem("signup-01", "注册卡片", "登录注册", "flow", "简洁的账号注册卡片，含姓名、邮箱、密码"),
            new CatalogItem("signup-02", "分栏注册 · 品牌介绍", "登录注册", "flow", "左右分栏注册页，左侧品牌说明、右侧注册表单"),
            new CatalogItem("signup-03", "注册 · 特性说明", "登录注册", "flow", "带产品特性说明的注册页"),
            new CatalogItem("signup-04", "双栏注册 · 产品展示", "登录注册", "flow", "右侧展示产品价值的双栏注册页"),
            new CatalogItem("signup-05", "深色品牌注册", "登录注册", "flow", "深色渐变背景的品牌风注册页"),
            // 后台布局
            new CatalogItem("sidebar-01", "后台侧边栏布局", "后台布局", "flow", "左侧固定导航 + 主内容区，含折叠菜单与账号信息"),
            new CatalogItem("sidebar-07", "可收起侧边栏", "后台布局", "flow", "侧边栏可收起为图标模式，主区带面包屑导航"),
            new CatalogItem("sidebar-10", "团队侧边栏", "后台布局", "flow", "带团队切换器的后台侧边栏布局"),
            // 数据展示
            new CatalogItem("data-table-demo", "数据表格 · 排序筛选", "数据展示", "flow", "带搜索、筛选、排序与分页的完整数据表格"),
            new CatalogItem("table-demo", "基础表格", "数据展示", "flow", "浅色描边卡片中的基础数据表格"),
            new CatalogItem("card-demo", "卡片 · 操作区", "数据展示", "flow", "标准内容卡片，含标题、说明、输入与操作按钮"),
            new CatalogItem("pagination-demo", "分页条", "数据展示", "flow", "带上一页/下一页与页码的分页组件"),
            new CatalogItem("tabs-demo", "标签页", "数据展示", "flow", "带账号与密码两个面板的标签页切换"),
            // 导航
            new CatalogItem("navigation-menu-demo", "顶部导航菜单", "导航", "top", "带下拉面板的顶部导航菜单，适合官网首屏导航"),
            // 表单与交互
            new CatalogItem("form-rhf-demo", "表单 · 字段校验", "表单与交互", "flow", "含输入校验与提交反馈的表单"),
            new CatalogItem("calendar-demo", "日历", "表单与交互", "flow", "单月日历，可切换月份与选择日期"),
            new CatalogItem("dialog-demo", "弹窗", "表单与交互", "flow", "带标题、说明与确认操作的模态弹窗"),
            new CatalogItem("command-demo", "命令面板", "表单与交互", "flow", "支持快捷键搜索的命令面板，含分组与快捷项"),
            new CatalogItem("accordion-demo", "折叠面板", "表单与交互", "flow", "FAQ 风格的折叠面板，支持展开收起"),
            new CatalogItem("carousel-demo", "轮播", "表单与交互", "flow", "带左右切换按钮的横向轮播")
    );
}