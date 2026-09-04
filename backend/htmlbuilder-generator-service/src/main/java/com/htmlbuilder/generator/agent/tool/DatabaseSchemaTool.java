package com.htmlbuilder.generator.agent.tool;

public class DatabaseSchemaTool {

    public String generateSchema(String tableDefinitions) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Auto-generated SQLite Schema\n\n");
        sb.append("PRAGMA journal_mode=WAL;\n\n");
        sb.append(tableDefinitions);
        sb.append("\n\n-- Seed data\n");
        return sb.toString();
    }

    public String generateSeedData(String tableName, String[] columns, String[][] rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(tableName).append(" (");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(columns[i]);
        }
        sb.append(") VALUES\n");
        for (int r = 0; r < rows.length; r++) {
            sb.append("(");
            for (int c = 0; c < rows[r].length; c++) {
                if (c > 0) sb.append(", ");
                sb.append(rows[r][c]);
            }
            sb.append(")");
            if (r < rows.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(";\n");
        return sb.toString();
    }

    public String generateInitDbScript(String schema, String seedData) {
        return schema + "\n" + seedData;
    }
}