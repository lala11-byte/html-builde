CREATE TABLE IF NOT EXISTS generation_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  project_id BIGINT,
  page_id BIGINT,
  prompt TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
  output_path VARCHAR(500),
  screenshot_path VARCHAR(500),
  custom_prompt TEXT COMMENT '自定义系统提示词',
  progress_log TEXT COMMENT 'JSON 数组，存储进度日志',
  error_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user (user_id),
  INDEX idx_project (project_id),
  INDEX idx_page (page_id)
);

CREATE TABLE IF NOT EXISTS component_material (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  registry VARCHAR(60) NOT NULL DEFAULT 'shadcn' COMMENT '素材来源，如 shadcn（MCP 同源 registry）',
  item VARCHAR(120) NOT NULL COMMENT 'registry 条目名，如 login-01',
  name VARCHAR(120) COMMENT '素材显示名',
  description VARCHAR(500) COMMENT '素材说明',
  category VARCHAR(60) COMMENT '素材分类',
  slot VARCHAR(20) COMMENT '插入位置：top/bottom/flow',
  html LONGTEXT COMMENT '适配后的 HTML 片段',
  css LONGTEXT COMMENT '适配后的 CSS',
  js LONGTEXT COMMENT '适配后的交互脚本',
  status VARCHAR(20) NOT NULL DEFAULT 'READY' COMMENT 'READY/FAILED',
  error VARCHAR(500) COMMENT '适配失败原因',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_registry_item (registry, item)
);

CREATE TABLE IF NOT EXISTS page_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  page VARCHAR(500) NOT NULL COMMENT 'public 下的页面路径，如 /index.html',
  branch VARCHAR(60) NOT NULL DEFAULT 'main',
  parent_id BIGINT COMMENT '同分支上的上一个快照',
  message VARCHAR(200) COMMENT '版本说明',
  content LONGTEXT COMMENT '页面完整 HTML',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_task_page (task_id, page),
  INDEX idx_task_branch (task_id, branch)
);