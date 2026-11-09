-- 后台管理：用户角色与封禁状态
-- 数据库：user_db
-- 执行方式：mysql -u root -p user_db < migration_add_admin.sql

ALTER TABLE user
  ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT 'user/admin',
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/banned',
  ADD COLUMN ban_reason VARCHAR(200) DEFAULT NULL,
  ADD COLUMN banned_at DATETIME DEFAULT NULL;

-- 默认管理员账号 admin / admin123（已存在同名用户时仅提升为管理员）
INSERT INTO user (nickname, username, password_hash, role, status)
SELECT '管理员', 'admin', MD5('admin123'), 'admin', 'active'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user WHERE username = 'admin');

UPDATE user SET role = 'admin' WHERE username = 'admin';
