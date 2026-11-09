-- 已有 user_db 升级：新增用户-项目关联表（配合昵称列请先执行 migration_add_nickname.sql）
-- 执行方式：mysql -u root -p user_db < migration_add_user_project.sql

CREATE TABLE IF NOT EXISTS user_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'OWNER',
  starred TINYINT(1) NOT NULL DEFAULT 0,
  pinned TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_project (user_id, project_id),
  INDEX idx_up_user_id (user_id)
);

-- 把 project_db 中已有的项目回填为当前用户的 OWNER 关联（跨库执行时需两库同实例）
-- INSERT IGNORE INTO user_db.user_project (user_id, project_id, role)
--   SELECT p.user_id, p.id, 'OWNER' FROM project_db.project p;
