package com.htmlbuilder.generator.agent.tool;

public class WebContentTool {

    public String generateHtml(String pageName, String htmlContent, String cssContent, String jsContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"zh-CN\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>").append(pageName).append("</title>\n");
        sb.append("  <link rel=\"stylesheet\" href=\"/css/style.css\">\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append(htmlContent);
        if (jsContent != null && !jsContent.isEmpty()) {
            sb.append("  <script src=\"/js/app.js\"></script>\n");
        }
        sb.append("</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    public String generateCss(String cssContent) {
        return "/* Auto-generated styles */\n" + cssContent;
    }

    public String generateJs(String jsContent) {
        return "// Auto-generated scripts\n" + jsContent;
    }

    public String generateApiJs(String baseUrl, String[] endpoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated API client\n");
        sb.append("const API_BASE = '").append(baseUrl).append("';\n\n");
        sb.append("async function api(path, options = {}) {\n");
        sb.append("  const res = await fetch(API_BASE + path, {\n");
        sb.append("    headers: { 'Content-Type': 'application/json', ...options.headers },\n");
        sb.append("    ...options\n");
        sb.append("  });\n");
        sb.append("  return res.json();\n");
        sb.append("}\n\n");
        for (String endpoint : endpoints) {
            sb.append("// ").append(endpoint).append("\n");
        }
        return sb.toString();
    }
}