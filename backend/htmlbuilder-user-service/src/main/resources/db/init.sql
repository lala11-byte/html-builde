-- user-service 数据库初始化脚本
-- 数据库：user_db
-- 执行方式：mysql -u root -p user_db < init.sql

CREATE TABLE IF NOT EXISTS user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nickname VARCHAR(20) DEFAULT NULL,
  username VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username)
);

-- 用户-项目关联表：项目本体在 project_db.project，此处维护用户侧的归属与标记
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