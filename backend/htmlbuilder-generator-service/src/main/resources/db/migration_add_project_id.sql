-- 迁移：为 generation_task 表增加 project_id 字段，实现项目级别隔离
-- 如果列已存在，这两条语句会报错，忽略即可
ALTER TABLE generation_task ADD COLUMN project_id BIGINT AFTER user_id;
ALTER TABLE generation_task ADD INDEX idx_project (project_id);