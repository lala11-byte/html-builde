-- 迁移：为 generation_task 表增加 custom_prompt 字段，支持用户自定义系统提示词
ALTER TABLE generation_task ADD COLUMN custom_prompt TEXT AFTER screenshot_path;