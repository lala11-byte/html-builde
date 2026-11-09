package com.htmlbuilder.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.generator.agent.FastChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.PromptConstants;
import com.htmlbuilder.generator.component.ComponentItem;
import com.htmlbuilder.generator.component.ComponentLibraryService;
import com.htmlbuilder.generator.dto.ElementPatchRequest;
import com.htmlbuilder.generator.vo.ElementPatchVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

/**
 * 元素级修改：只改写选中元素，不重跑整站生成流程。
 * - ai：模型只返回 {html,css,js} 片段
 * - replace / insertAfter / append：直接插入组件库片段
 * 修改前会备份为 xxx.html.bak，失败时前端可回退整站迭代。
 */
@Service
public class ElementPatchService {

    private static final Logger log = LoggerFactory.getLogger(ElementPatchService.class);
    /** 组件内承载示例文案的数据属性，仅改样式时不能复制到用户元素上 */
    private static final Set<String> TEXT_PAYLOAD_ATTRS = Set.of("data-hb-split", "data-hb-words", "data-hb-text");
    /** 页面级结构段标签：基础区块应挂在页面层级，而不是任意元素内部 */
    private static final Set<String> SECTION_TAGS = Set.of("section", "header", "footer", "nav", "main", "article", "aside");

    private final GeneratorService generatorService;
    private final ComponentLibraryService componentLibraryService;
    private final BridgeInjectionService bridgeInjectionService;
    private final FastChatLanguageModel fastChatLanguageModel;
    private final PageSnapshotService pageSnapshotService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElementPatchService(GeneratorService generatorService,
                               ComponentLibraryService componentLibraryService,
                               BridgeInjectionService bridgeInjectionService,
                               FastChatLanguageModel fastChatLanguageModel,
                               PageSnapshotService pageSnapshotService) {
        this.generatorService = generatorService;
        this.componentLibraryService = componentLibraryService;
        this.bridgeInjectionService = bridgeInjectionService;
        this.fastChatLanguageModel = fastChatLanguageModel;
        this.pageSnapshotService = pageSnapshotService;
    }

    /** 为旧任务按需注入元素选取脚本 */
    public void prepare(Long taskId) {
        Path projectDir = requireProjectDir(taskId);
        bridgeInjectionService.install(projectDir);
    }

    public ElementPatchVO patch(ElementPatchRequest req) {
        Path projectDir = requireProjectDir(req.getTaskId());
        Path pageFile = resolvePageFile(projectDir, req.getPage());
        String selector = req.getSelector() == null ? "" : req.getSelector().trim();
        String mode = req.getMode() == null ? "" : req.getMode().trim();

        Document doc;
        try {
            doc = Jsoup.parse(Files.readString(pageFile, StandardCharsets.UTF_8), "UTF-8");
        } catch (Exception e) {
            throw new BusinessException(2002, "读取页面失败: " + e.getMessage());
        }
        doc.outputSettings().prettyPrint(false);

        Element target = null;
        if (!selector.isBlank()) {
            try {
                target = doc.selectFirst(selector);
            } catch (Exception e) {
                throw new BusinessException(2002, "选择器无法解析: " + selector);
            }
        }

        String html = "";
        String css = "";
        String js = "";
        String componentId = req.getComponentId();
        String componentName = "";
        String message;
        // 美学配方会把选择器写进 CSS，重复应用时需要替换旧规则而不是跳过注入
        boolean replaceInjectedStyle = false;

        if (mode.isBlank()) {
            mode = (componentId != null && !componentId.isBlank()) ? "replace" : "ai";
        }

        // 按需求生成区块属于页面级操作（位置由素材用途决定），不需要先选中元素；
        // 其余模式（改样式、AI 改写、替换/添加区块等）仍必须定位到目标元素
        boolean pageLevel = "blockGenerate".equals(mode);
        if (target == null && !pageLevel) {
            throw new BusinessException(2002, "未能在页面中定位选中元素，请刷新预览后重试");
        }
        if (target != null && isProtected(target)) {
            throw new BusinessException(2003, "该元素属于页面骨架（html/head/body/script），不支持直接替换");
        }

        if ("ai".equals(mode)) {
            if (req.getInstruction() == null || req.getInstruction().isBlank()) {
                throw new BusinessException(2004, "请填写修改要求");
            }
            JsonNode result = askModel(req, target);
            html = text(result, "html");
            css = text(result, "css");
            js = text(result, "js");
            if (html == null || html.isBlank()) {
                throw new BusinessException(2005, "AI 未返回有效 HTML，请换个说法重试");
            }
            componentId = null;
            message = "AI 修改：" + brief(req.getInstruction());
        } else if ("style".equals(mode)) {
            ComponentItem item = requireComponent(componentId);
            componentName = item.getName();
            if ("target".equals(item.getScope())) {
                // 美学配方：只把 CSS 作用到选中元素的选择器上，不注入平台内容、不动原有结构
                String recipe = item.getCss();
                if (recipe == null || recipe.isBlank()) {
                    throw new BusinessException(2006, "该配方不包含样式: " + componentId);
                }
                css = scopeCss(recipe, selector);
                js = "";
                replaceInjectedStyle = true;
                message = "应用美学配方：" + componentName;
            } else {
                if (!applyStyleOnly(target, item.getHtml())) {
                    throw new BusinessException(2006, "该组件不包含可应用的样式: " + componentId);
                }
                css = item.getCss();
                js = item.getJs();
                message = "应用样式：" + componentName;
            }
        } else if ("blockGenerate".equals(mode)) {
            if (req.getInstruction() == null || req.getInstruction().isBlank()) {
                throw new BusinessException(2004, "请描述你想要的区块内容");
            }
            ComponentItem material = requireComponent(componentId);
            componentName = material.getName();
            JsonNode result = askModelForBlock(req, material);
            html = text(result, "html");
            css = text(result, "css");
            js = text(result, "js");
            if (html == null || html.isBlank()) {
                throw new BusinessException(2005, "AI 未返回有效区块，请换个说法重试");
            }
            // 生成内容每次不同，不做资源去重
            componentId = null;
            insertBlock(doc, target, material, html);
            message = "按需求生成区块（参考：" + componentName + "）：" + brief(req.getInstruction());
        } else if ("position".equals(mode)) {
            String styleText = req.getStyle() == null ? "" : req.getStyle().trim();
            if (styleText.length() > 600) {
                styleText = styleText.substring(0, 600);
            }
            String lower = styleText.toLowerCase();
            if (lower.contains("<") || lower.contains(">") || lower.contains("expression(")) {
                throw new BusinessException(2004, "非法的位置样式");
            }
            target.attr("style", styleText);
            componentId = null;
            message = "移动元素";
        } else if ("blockAdd".equals(mode) || "blockReplace".equals(mode)) {
            ComponentItem item = requireComponent(componentId);
            componentName = item.getName();
            if (!"block".equals(item.getKind())) {
                throw new BusinessException(2006, "该组件不是页面区块: " + componentId);
            }
            if ("blockReplace".equals(mode)) {
                Element section = nearestSection(target);
                if (section == null || "body".equals(section.tagName())) {
                    throw new BusinessException(2004, "当前选中位置不在页面结构段内，请改用“添加区块”");
                }
                // 替换不是简单覆盖：把素材版式与原区块内容交给 AI 融合重写，避免页面语义断裂
                JsonNode result = askModelForReplace(req, item, section);
                html = text(result, "html");
                css = text(result, "css");
                js = text(result, "js");
                if (html == null || html.isBlank()) {
                    throw new BusinessException(2005, "AI 未返回有效区块，请重试");
                }
                // 融合结果每次不同，不再按素材去重注入
                componentId = null;
                applyDom(doc, section, "replace", html);
                message = "替换区块（融合原内容，参考：" + componentName + "）：" + brief(req.getInstruction());
            } else {
                html = item.getHtml();
                css = item.getCss();
                js = item.getJs();
                if (html == null || html.isBlank()) {
                    throw new BusinessException(2006, "组件内容为空: " + componentId);
                }
                insertBlock(doc, target, item, html);
                message = "添加区块：" + componentName;
            }
        } else if ("replace".equals(mode) || "insertAfter".equals(mode) || "append".equals(mode)) {
            ComponentItem item = requireComponent(componentId);
            componentName = item.getName();
            String instruction = req.getInstruction() == null ? "" : req.getInstruction().trim();
            if (!instruction.isBlank() && !"append".equals(mode)) {
                // 带修改要求时同样交给 AI 融合，保留选中元素原有信息
                JsonNode result = askModelForReplace(req, item, target);
                html = text(result, "html");
                css = text(result, "css");
                js = text(result, "js");
                if (html == null || html.isBlank()) {
                    throw new BusinessException(2005, "AI 未返回有效内容，请重试");
                }
                componentId = null;
                message = modeLabel(mode) + "（融合原内容，参考：" + componentName + "）：" + brief(instruction);
            } else {
                html = item.getHtml();
                css = item.getCss();
                js = item.getJs();
                if (html == null || html.isBlank()) {
                    throw new BusinessException(2006, "组件内容为空: " + componentId);
                }
                message = modeLabel(mode) + "组件：" + componentName;
            }
        } else {
            throw new BusinessException(2004, "不支持的修改方式: " + mode);
        }

        String marker = (componentId != null ? "component:" + componentId : "patch")
                + ":" + System.currentTimeMillis();

        try {
            // AI 模式返回的是该元素的新片段，按替换处理；
            // style/position 已直接改在目标元素上，blockAdd/blockReplace 已在上面完成页面级插入/替换
            boolean domApplied = "style".equals(mode) || "position".equals(mode)
                    || "blockAdd".equals(mode) || "blockReplace".equals(mode)
                    || "blockGenerate".equals(mode);
            if (!domApplied) {
                applyDom(doc, target, "ai".equals(mode) ? "replace" : mode, html);
            }
            if (replaceInjectedStyle) {
                removeInjected(doc, "style", componentId);
            }
            injectStyle(doc, css, marker, componentId);
            injectScript(doc, js, marker, componentId);
            Path backup = Path.of(pageFile + ".bak");
            Files.copy(pageFile, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(pageFile, doc.outerHtml(), StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("元素修改写盘失败: {}", pageFile, e);
            throw new BusinessException(2007, "写入页面失败: " + e.getMessage());
        }

        // 每次页面变动自动生成快照（快照失败不影响修改本身）
        try {
            pageSnapshotService.record(req.getTaskId(), toPagePath(projectDir, pageFile), req.getBranch(), message);
        } catch (Exception e) {
            log.warn("生成页面快照失败: {}", e.getMessage());
        }

        ElementPatchVO vo = new ElementPatchVO();
        vo.setPage(pageFile.getFileName().toString());
        vo.setSelector(selector);
        vo.setMode(mode);
        vo.setComponentId(componentId);
        vo.setSummary(message);
        vo.setPreviewUrl(generatorService.buildPreviewUrl(req.getTaskId()));
        return vo;
    }

    private ComponentItem requireComponent(String componentId) {
        ComponentItem item = componentLibraryService.get(componentId);
        if (item == null) {
            throw new BusinessException(2006, "组件不存在: " + componentId);
        }
        return item;
    }

    /**
     * 仅把组件根元素的 class 与 data-hb-* 属性附加到选中元素上，
     * 保留原有文案与子结构，由组件 CSS/JS 提供视觉效果。
     */
    private boolean applyStyleOnly(Element target, String componentHtml) {
        if (componentHtml == null || componentHtml.isBlank()) {
            return false;
        }
        Element root;
        try {
            root = Jsoup.parseBodyFragment(componentHtml).body().children().first();
        } catch (Exception e) {
            return false;
        }
        if (root == null) {
            return false;
        }
        boolean changed = false;
        for (String cls : root.className().split("\\s+")) {
            if (!cls.isBlank() && !target.hasClass(cls)) {
                target.addClass(cls);
                changed = true;
            }
        }
        for (Attribute attr : root.attributes()) {
            String key = attr.getKey();
            // 组件里的示例文案（data-hb-split / data-hb-words / data-hb-text）不复制，
            // 否则动效脚本会把用户原有文案替换成组件示例文案
            if (key.startsWith("data-hb-") && !"data-hb-id".equals(key) && !TEXT_PAYLOAD_ATTRS.contains(key)) {
                target.attr(key, attr.getValue());
                changed = true;
            }
        }
        return changed;
    }

    /** 项目内页面路径，如 /index.html，用于历史快照分页 */
    private String toPagePath(Path projectDir, Path pageFile) {
        Path publicDir = projectDir.resolve("public").toAbsolutePath().normalize();
        Path file = pageFile.toAbsolutePath().normalize();
        if (file.startsWith(publicDir)) {
            return "/" + publicDir.relativize(file).toString().replace('\\', '/');
        }
        return "/" + pageFile.getFileName();
    }

    private String brief(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }

    private String modeLabel(String mode) {
        return switch (mode) {
            case "replace" -> "替换";
            case "insertAfter" -> "插入到元素之后";
            case "append" -> "追加到元素内部";
            default -> mode;
        };
    }

    private JsonNode askModel(ElementPatchRequest req, Element target) {
        String outerHtml = req.getOuterHTML() != null && !req.getOuterHTML().isBlank()
                ? req.getOuterHTML() : target.outerHtml();
        if (outerHtml.length() > 12000) {
            outerHtml = outerHtml.substring(0, 12000);
        }
        String prompt = PromptConstants.ELEMENT_PATCH
                + "\n\n【页面】" + (req.getPage() == null ? "/" : req.getPage())
                + "\n【元素标签】" + target.tagName()
                + "\n【元素选择器】" + req.getSelector()
                + "\n【元素当前 HTML】\n" + outerHtml
                + "\n【用户修改要求】\n" + req.getInstruction();
        return askJson(prompt);
    }

    /** 以素材为参考、按用户需求生成一个页面级区块（不照抄素材文案） */
    private JsonNode askModelForBlock(ElementPatchRequest req, ComponentItem material) {
        StringBuilder prompt = new StringBuilder(PromptConstants.MATERIAL_BLOCK)
                .append("\n\n【参考素材】").append(material.getName())
                .append("（").append(material.getDescription() == null ? "" : material.getDescription()).append("）")
                .append("\n【所在页面】").append(req.getPage() == null ? "/" : req.getPage())
                .append("\n【用户需求】\n").append(req.getInstruction());
        // MCP 素材已适配为真实结构，作为版式与视觉参考附上，让生成结果延续原组件的设计质感
        String refHtml = material.getHtml();
        if (refHtml != null && !refHtml.isBlank()) {
            prompt.append("\n【素材实现（仅参考版式、间距、配色与交互，文案必须按用户需求重写）】\n");
            prompt.append(truncate(refHtml, 6000));
            String refCss = material.getCss();
            if (refCss != null && !refCss.isBlank()) {
                prompt.append("\n【素材样式】\n").append(truncate(refCss, 3000));
            }
        }
        return askJson(prompt.toString());
    }

    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    /**
     * 用素材的版式重写原有区块/元素：把素材实现与原内容一起交给 AI，
     * 让 AI 决定保留哪些信息、如何映射到新版式，避免替换后页面上下文断裂。
     */
    private JsonNode askModelForReplace(ElementPatchRequest req, ComponentItem material, Element origin) {
        String instruction = req.getInstruction() == null ? "" : req.getInstruction().trim();
        if (instruction.isBlank()) {
            instruction = "无额外要求：按素材版式重写，完整保留原区块的信息、链接与功能。";
        }
        String originHtml = truncate(origin.outerHtml(), 10000);
        StringBuilder prompt = new StringBuilder(PromptConstants.MATERIAL_REPLACE)
                .append("\n\n【参考素材】").append(material.getName())
                .append("（").append(material.getDescription() == null ? "" : material.getDescription()).append("）")
                .append("\n【所在页面】").append(req.getPage() == null ? "/" : req.getPage())
                .append("\n【原元素标签】").append(origin.tagName())
                .append("\n【原元素选择器】").append(req.getSelector() == null ? "" : req.getSelector())
                .append("\n【原元素当前 HTML（内容来源，必须保留其信息与功能）】\n").append(originHtml)
                .append("\n【用户补充要求】\n").append(instruction);
        String refHtml = material.getHtml();
        if (refHtml != null && !refHtml.isBlank()) {
            prompt.append("\n【素材实现（仅参考版式、间距、配色与交互，示例文案必须替换为原元素内容）】\n");
            prompt.append(truncate(refHtml, 6000));
            String refCss = material.getCss();
            if (refCss != null && !refCss.isBlank()) {
                prompt.append("\n【素材样式】\n").append(truncate(refCss, 3000));
            }
        }
        return askJson(prompt.toString());
    }

    private JsonNode askJson(String prompt) {
        String raw;
        try {
            raw = fastChatLanguageModel.chat(prompt);
        } catch (Exception e) {
            throw new BusinessException(2005, "AI 调用失败: " + e.getMessage());
        }
        String json = CodeIOUtil.extractJson(CodeIOUtil.stripCodeFences(raw));
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull()) {
                throw new BusinessException(2005, "AI 未返回有效结果");
            }
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 返回内容无法解析为 JSON: {}", raw);
            throw new BusinessException(2005, "AI 返回格式不正确，请重试");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    /**
     * 基础区块的插入位置由组件自身的用途（slot）决定，避免把整段页面结构塞进任意元素内部：
     * - top：页面顶部（导航）
     * - bottom：页面末尾（页脚）
     * - flow（默认）：插到当前所在结构段之后，保持页面从上到下的阅读顺序
     */
    private void insertBlock(Document doc, Element target, ComponentItem item, String html) {
        Element body = doc.body();
        if (body == null) {
            throw new BusinessException(2003, "页面缺少 body 节点，无法添加区块");
        }
        String slot = item.getSlot() == null ? "" : item.getSlot().trim();
        if ("top".equals(slot)) {
            body.prepend(html);
            return;
        }
        if ("bottom".equals(slot)) {
            insertAtBodyEnd(body, html);
            return;
        }
        Element section = nearestSection(target);
        if (section == null || "body".equals(section.tagName())) {
            Element footer = null;
            for (Element child : body.children()) {
                if ("footer".equals(child.tagName())) {
                    footer = child;
                    break;
                }
            }
            if (footer != null) {
                footer.before(html);
            } else {
                insertAtBodyEnd(body, html);
            }
            return;
        }
        section.after(html);
    }

    /** 放在 body 末尾，但排在页面原有脚本之前，避免影响脚本执行顺序 */
    private void insertAtBodyEnd(Element body, String html) {
        Element lastScript = null;
        for (Element child : body.children()) {
            if ("script".equals(child.tagName())) {
                lastScript = child;
            }
        }
        if (lastScript != null) {
            lastScript.before(html);
        } else {
            body.append(html);
        }
    }

    /** 向上查找最近的结构段（section/header/footer 等），找不到时返回 body */
    private Element nearestSection(Element el) {
        Element cur = el;
        while (cur != null && !"body".equals(cur.tagName()) && !"html".equals(cur.tagName())) {
            if (SECTION_TAGS.contains(cur.tagName())) {
                return cur;
            }
            cur = cur.parent();
        }
        return cur;
    }

    private void applyDom(Document doc, Element target, String mode, String html) {
        switch (mode) {
            case "replace" -> {
                Element parent = target.parent();
                if (parent == null) {
                    throw new BusinessException(2003, "该元素没有父节点，无法替换");
                }
                List<Node> nodes = Parser.parseFragment(html, parent, target.baseUri());
                for (Node node : nodes) {
                    target.before(node);
                }
                target.remove();
            }
            case "insertAfter" -> {
                Element parent = target.parent();
                if (parent == null) {
                    throw new BusinessException(2003, "该元素没有父节点，无法插入");
                }
                List<Node> nodes = Parser.parseFragment(html, parent, target.baseUri());
                Node anchor = target;
                for (Node node : nodes) {
                    anchor.after(node);
                    anchor = node;
                }
            }
            case "append" -> target.append(html);
            default -> throw new BusinessException(2004, "不支持的修改方式: " + mode);
        }
    }

    private void injectStyle(Document doc, String css, String marker, String componentId) {
        if (css == null || css.isBlank() || hasInjected(doc, "style", componentId)) {
            return;
        }
        Element style = new Element("style");
        style.attr("data-hb-patch", marker);
        if (componentId != null) {
            style.attr("data-hb-component", componentId);
        }
        style.appendChild(new DataNode(css));
        if (doc.head() != null) {
            doc.head().appendChild(style);
        } else {
            doc.appendChild(style);
        }
    }

    private void injectScript(Document doc, String js, String marker, String componentId) {
        if (js == null || js.isBlank() || hasInjected(doc, "script", componentId)) {
            return;
        }
        Element script = new Element("script");
        script.attr("data-hb-patch", marker);
        if (componentId != null) {
            script.attr("data-hb-component", componentId);
        }
        script.appendChild(new DataNode(js));
        if (doc.body() != null) {
            doc.body().appendChild(script);
        } else {
            doc.appendChild(script);
        }
    }

    /** 同一组件的样式/脚本只注入一次，重复添加区块时不会堆积重复资源 */
    private boolean hasInjected(Document doc, String tag, String componentId) {
        if (componentId == null) {
            return false;
        }
        for (Element el : doc.select(tag)) {
            if (componentId.equals(el.attr("data-hb-component"))) {
                return true;
            }
        }
        return false;
    }

    /** 移除某个组件已注入的资源，用于美学配方改绑到新的选中元素 */
    private void removeInjected(Document doc, String tag, String componentId) {
        if (componentId == null) {
            return;
        }
        for (Element el : doc.select(tag)) {
            if (componentId.equals(el.attr("data-hb-component"))) {
                el.remove();
            }
        }
    }

    /** 把配方里的 & 替换成选中元素的选择器，让样式只作用于该元素 */
    private String scopeCss(String template, String selector) {
        String sel = (selector == null || selector.isBlank()) ? "body" : selector.trim();
        return template.replace("&", sel);
    }

    /** 页面骨架节点不允许作为替换目标 */
    private boolean isProtected(Element el) {
        String tag = el.tagName();
        return "html".equals(tag) || "head".equals(tag) || "body".equals(tag)
                || "script".equals(tag) || "meta".equals(tag) || "link".equals(tag);
    }

    private Path requireProjectDir(Long taskId) {
        Path projectDir = generatorService.getOutputPath(taskId);
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            throw new BusinessException(2008, "生成结果不存在或已被清理");
        }
        return projectDir;
    }

    /**
     * 把 bridge 回传的路径映射到 public 下的实际文件，禁止越出 public 目录。
     */
    private Path resolvePageFile(Path projectDir, String page) {
        Path publicDir = projectDir.resolve("public").toAbsolutePath().normalize();
        String p = page == null ? "" : page.trim();
        int hashIdx = p.indexOf('#');
        if (hashIdx >= 0) {
            p = p.substring(0, hashIdx);
        }
        int queryIdx = p.indexOf('?');
        if (queryIdx >= 0) {
            p = p.substring(0, queryIdx);
        }
        // 兼容前端回传的完整 URL 或网关预览前缀（/api/v1/generator/preview/{taskId}/...）
        p = p.replaceFirst("^https?://[^/]+", "");
        p = p.replaceFirst("^/?(?:api/v1/)?generator/preview/\\d+", "");
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isBlank()) {
            p = "index.html";
        }
        if (p.endsWith("/")) {
            p = p + "index.html";
        }
        if (!p.toLowerCase().endsWith(".html")) {
            Path direct = publicDir.resolve(p + ".html").normalize();
            Path dirIndex = publicDir.resolve(p + "/index.html").normalize();
            if (Files.isRegularFile(direct)) {
                return direct;
            }
            if (Files.isRegularFile(dirIndex)) {
                return dirIndex;
            }
            throw new BusinessException(2002, "页面不存在: " + p);
        }
        Path file = publicDir.resolve(p).normalize();
        if (!file.startsWith(publicDir)) {
            throw new BusinessException(2002, "非法的页面路径: " + page);
        }
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(2002, "页面不存在: " + p);
        }
        return file;
    }
}