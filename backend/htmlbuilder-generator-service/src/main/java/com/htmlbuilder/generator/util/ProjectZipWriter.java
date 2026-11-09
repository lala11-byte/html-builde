package com.htmlbuilder.generator.util;

import com.htmlbuilder.generator.service.BridgeInjectionService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 项目打包：与 zt-zip 的默认打包一致，但会剔除编辑器注入的 bridge 脚本，
 * 保证下载得到的站点是干净产物。
 */
public final class ProjectZipWriter {

    private static final Pattern HB_ID_ATTR = Pattern.compile("\\sdata-hb-id=\"[^\"]*\"");

    private ProjectZipWriter() {}

    public static void write(Path rootDir, OutputStream out) throws IOException {
        Path root = rootDir.toAbsolutePath().normalize();
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8);
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            if (file.getFileName().toString().endsWith(".bak")) {
                continue;
            }
            zos.putNextEntry(new ZipEntry(name));
            if (name.toLowerCase().endsWith(".html")) {
                String html = Files.readString(file, StandardCharsets.UTF_8);
                html = BridgeInjectionService.removeBridge(html);
                html = HB_ID_ATTR.matcher(html).replaceAll("");
                zos.write(html.getBytes(StandardCharsets.UTF_8));
            } else {
                Files.copy(file, zos);
            }
            zos.closeEntry();
        }
        zos.finish();
        zos.flush();
    }
}