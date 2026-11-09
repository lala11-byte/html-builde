package com.htmlbuilder.generator.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htmlbuilder.generator.service.RegistryMaterialService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件库服务：
 * - 内置素材只有「美学配方」与「文本动效」（只改选中元素外观，不注入平台内容）
 * - 区块素材来自 MCP registry（shadcn），由 RegistryMaterialService 按需拉取真实组件并适配
 */
@Service
public class ComponentLibraryService {

    private static final Logger log = LoggerFactory.getLogger(ComponentLibraryService.class);

    private final Map<String, ComponentItem> components = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RegistryMaterialService registryMaterialService;

    public ComponentLibraryService(RegistryMaterialService registryMaterialService) {
        this.registryMaterialService = registryMaterialService;
    }

    @PostConstruct
    public void load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:components/*.json");
            for (Resource resource : resources) {
                // 平台自带的区块模板不再使用：区块素材统一从 MCP registry 拉取
                if ("blocks.json".equals(resource.getFilename())) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    List<ComponentItem> items = objectMapper.readValue(in, new TypeReference<List<ComponentItem>>() {});
                    if (items == null) {
                        continue;
                    }
                    for (ComponentItem item : items) {
                        if (item == null || item.getId() == null || item.getId().isBlank()) {
                            continue;
                        }
                        components.put(item.getId(), item);
                    }
                } catch (Exception e) {
                    log.warn("加载组件文件失败 {}: {}", resource.getFilename(), e.getMessage());
                }
            }
            log.info("组件库加载完成，共 {} 个组件", components.size());
        } catch (Exception e) {
            log.error("组件库加载失败", e);
        }
    }

    /**
     * 组件元信息：内置素材保留 html/css/js，悬停预览需要执行 js 才能看到动效；
     * MCP 素材只返回元信息，打开弹层时再按需拉取真实组件。
     */
    public List<ComponentItem> listMeta() {
        List<ComponentItem> list = new ArrayList<>();
        for (ComponentItem item : components.values()) {
            ComponentItem meta = new ComponentItem();
            meta.setId(item.getId());
            meta.setName(item.getName());
            meta.setCategory(item.getCategory());
            meta.setDescription(item.getDescription());
            meta.setKind(item.getKind());
            meta.setSlot(item.getSlot());
            meta.setScope(item.getScope());
            meta.setSource(item.getSource());
            meta.setHtml(item.getHtml());
            meta.setCss(item.getCss());
            meta.setJs(item.getJs());
            list.add(meta);
        }
        list.addAll(registryMaterialService.listMeta());
        list.sort(Comparator.comparing((ComponentItem c) -> c.getCategory() == null ? "" : c.getCategory())
                .thenComparing(c -> c.getName() == null ? "" : c.getName()));
        return list;
    }

    public ComponentItem get(String id) {
        if (id == null) {
            return null;
        }
        ComponentItem local = components.get(id);
        if (local != null) {
            return local;
        }
        return registryMaterialService.get(id);
    }
}