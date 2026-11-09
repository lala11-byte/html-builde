-- 已有 user_db 升级：为注册昵称新增 nickname 列
-- 执行方式：mysql -u root -p user_db < migration_add_nickname.sql
ALTER TABLE user ADD COLUMN nickname VARCHAR(20) DEFAULT NULL AFTER id;