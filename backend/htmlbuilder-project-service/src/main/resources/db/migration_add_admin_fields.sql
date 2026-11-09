-- 后台管理：作品软删除与社区上下架
-- 数据库：project_db
-- 执行方式：mysql -u root -p project_db < migration_add_admin_fields.sql

ALTER TABLE project
  ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN deleted_at DATETIME DEFAULT NULL,
  ADD COLUMN deleted_by BIGINT DEFAULT NULL,
  ADD COLUMN community_status VARCHAR(20) NOT NULL DEFAULT 'published' COMMENT 'published/unpublished',
  ADD COLUMN published_at DATETIME DEFAULT NULL;

-- 历史成品：以最近更新时间作为上架时间，供广场时间排序使用
UPDATE project
SET published_at = COALESCE(updated_at, created_at)
WHERE published_at IS NULL AND screenshot_url IS NOT NULL AND screenshot_url <> '';

CREATE INDEX idx_project_deleted ON project (deleted);
CREATE INDEX idx_project_community ON project (community_status, published_at);
