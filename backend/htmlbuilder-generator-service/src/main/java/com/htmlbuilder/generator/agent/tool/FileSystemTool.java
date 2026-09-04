package com.htmlbuilder.generator.agent.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemTool {

    public String createDirectory(String basePath, String dirPath) {
        try {
            Path fullPath = Path.of(basePath, dirPath);
            Files.createDirectories(fullPath);
            return "OK: directory created: " + fullPath;
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public String writeFile(String basePath, String filePath, String content) {
        try {
            Path fullPath = Path.of(basePath, filePath);
            Files.createDirectories(fullPath.getParent());
            Files.writeString(fullPath, content);
            return "OK: file written: " + fullPath + " (" + content.length() + " bytes)";
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public String readFile(String basePath, String filePath) {
        try {
            Path fullPath = Path.of(basePath, filePath);
            if (!Files.exists(fullPath)) {
                return "ERROR: file not found: " + fullPath;
            }
            return Files.readString(fullPath);
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }
}