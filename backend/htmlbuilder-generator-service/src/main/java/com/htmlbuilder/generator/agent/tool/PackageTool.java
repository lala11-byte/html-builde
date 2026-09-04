package com.htmlbuilder.generator.agent.tool;

public class PackageTool {

    public String generatePackageJson(String projectName, String description) {
        return """
{
  "name": "%s",
  "version": "1.0.0",
  "description": "%s",
  "main": "server.js",
  "scripts": {
    "start": "node server.js",
    "dev": "node server.js"
  },
  "dependencies": {
    "express": "^4.18.2",
    "better-sqlite3": "^11.0.0",
    "cors": "^2.8.5"
  }
}
""".formatted(projectName, description);
    }

    public String generateServerJs(String projectName, String[] routes, String dbSchema) {
        StringBuilder sb = new StringBuilder();
        sb.append("const express = require('express');\n");
        sb.append("const cors = require('cors');\n");
        sb.append("const Database = require('better-sqlite3');\n");
        sb.append("const path = require('path');\n\n");
        sb.append("const app = express();\n");
        sb.append("app.use(cors());\n");
        sb.append("app.use(express.json());\n");
        sb.append("app.use(express.static(path.join(__dirname, 'public')));\n\n");
        sb.append("// Initialize database\n");
        sb.append("const db = new Database(path.join(__dirname, 'data.db'));\n");
        sb.append("db.exec(`").append(dbSchema).append("`);\n\n");
        for (String route : routes) {
            sb.append(route).append("\n");
        }
        sb.append("\nconst PORT = process.env.PORT || 3000;\n");
        sb.append("app.listen(PORT, () => {\n");
        sb.append("  console.log(`Server running at http://localhost:${PORT}`);\n");
        sb.append("});\n");
        return sb.toString();
    }

    public String generateStartScript() {
        return "#!/bin/bash\nnpm install && npm start\n";
    }

    public String generateGitignore() {
        return """
node_modules/
data.db
.env
""";
    }
}