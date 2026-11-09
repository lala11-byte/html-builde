-- 后台管理：生成任务 token 用量
-- 数据库：generator_db
-- 执行方式：mysql -u root -p generator_db < migration_add_token_usage.sql

ALTER TABLE generation_task
  ADD COLUMN prompt_tokens INT NOT NULL DEFAULT 0,
  ADD COLUMN completion_tokens INT NOT NULL DEFAULT 0,
  ADD COLUMN total_tokens INT NOT NULL DEFAULT 0,
  ADD COLUMN model_calls INT NOT NULL DEFAULT 0;
