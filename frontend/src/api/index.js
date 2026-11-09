import request from './request'

// 注册
export function register(data) {
  return request.post('/auth/register', data)
}

// 登录
export function login(data) {
  return request.post('/auth/login', data)
}

// 刷新 token
export function refreshToken(token) {
  return request.post('/auth/refresh', { refreshToken: token })
}

// 当前登录用户信息
export function getProfile() {
  return request.get('/users/me')
}

// 修改昵称
export function updateNickname(nickname) {
  return request.put('/users/me/nickname', { nickname })
}

// 创建项目
export function createProject(data) {
  return request.post('/projects', data)
}

// 分页查询项目列表
export function listProjects(page = 1, size = 10) {
  return request.get('/projects', { params: { page, size } })
}

// 重命名项目
export function updateProject(id, data) {
  return request.put(`/projects/${id}`, data)
}

// 删除项目
export function deleteProject(id) {
  return request.delete(`/projects/${id}`)
}

// 作品广场：其他用户的成品项目。sort=comprehensive/like/time，keyword 搜索作品名或作者
export function listCommunityProjects(page = 1, size = 12, sort = 'comprehensive', keyword = '') {
  return request.get('/projects/community', { params: { page, size, sort, keyword } })
}

// 点赞 / 取消点赞
export function toggleProjectLike(id) {
  return request.post(`/projects/${id}/like`)
}

// 创建页面
export function createPage(projectId, data) {
  return request.post(`/projects/${projectId}/pages`, data)
}

// 分页查询页面列表
export function listPages(projectId, page = 1, size = 20) {
  return request.get(`/projects/${projectId}/pages`, { params: { page, size } })
}

// 重命名页面
export function updatePage(projectId, pageId, data) {
  return request.put(`/projects/${projectId}/pages/${pageId}`, data)
}

// 删除页面
export function deletePage(projectId, pageId) {
  return request.delete(`/projects/${projectId}/pages/${pageId}`)
}

// === AI 生成器 ===

// 提交生成任务
export function submitGenerate(prompt, projectId, pageId, customPrompt) {
  return request.post('/generator/generate', { prompt, projectId, pageId, customPrompt })
}

// 获取任务状态
export function getTaskStatus(taskId) {
  return request.get(`/generator/tasks/${taskId}`)
}

// 查询页面最新的已完成生成结果
export function getLatestByPage(projectId, pageId) {
  return request.get(`/generator/pages/${pageId}/latest`, { params: { projectId } })
}

// 本机启动生成的网站（首次需 npm install，超时放宽到 6 分钟）
export function startRun(taskId) {
  return request.post(`/generator/run/${taskId}/start`, null, { timeout: 360000 })
}

// 停止本机运行的网站
export function stopRun(taskId) {
  return request.post(`/generator/run/${taskId}/stop`)
}

// 查询本机运行状态
export function getRunStatus(taskId) {
  return request.get(`/generator/run/${taskId}/status`)
}

// 查询生成网站的角色端入口（教师端/学生端/管理员端等）
export function listPortals(taskId) {
  return request.get(`/generator/portals/${taskId}`)
}

// 获取需求分析文档
export function getRequirement(taskId) {
  return request.get(`/generator/requirement/${taskId}`)
}

// 更新项目截图
export function updateProjectScreenshot(projectId, screenshotUrl) {
  return request.put(`/projects/${projectId}/screenshot`, { screenshotUrl })
}

// 提交迭代优化任务（基于已有生成结果追加需求）
export function iterateGenerate(taskId, additionalPrompt, customPrompt) {
  return request.post('/generator/iterate', { taskId, additionalPrompt, customPrompt })
}

// === 组件库 & 元素级修改 ===

// 组件库列表（仅元信息）
export function getComponents() {
  return request.get('/generator/components')
}

// 组件详情（含 html/css/js）。MCP 素材首次访问需要拉取源码并由 AI 适配，耗时较长
export function getComponent(id) {
  return request.get(`/generator/components/${id}`, { timeout: 180000 })
}

// 对选中元素做 AI 修改，或用组件片段替换/插入
export function patchElement(data) {
  return request.post('/generator/element/patch', data, { timeout: 180000 })
}

// 为旧任务补注入元素选取脚本
export function prepareElementEdit(taskId) {
  return request.post(`/generator/element/prepare/${taskId}`)
}

// === 页面版本历史 ===

// 历史：分支列表 + 页面列表 + 快照列表（不含正文）；page 可选，限定单个页面
export function getHistory(taskId, page) {
  return request.get(`/generator/history/${taskId}`, { params: page ? { page } : {} })
}

// 单个快照详情（含完整 HTML）
export function getSnapshot(id) {
  return request.get(`/generator/history/snapshot/${id}`)
}

// 恢复到指定版本
export function restoreSnapshot(taskId, snapshotId) {
  return request.post(`/generator/history/${taskId}/restore`, { snapshotId })
}

// 从指定版本创建分支
export function createSnapshotBranch(taskId, snapshotId, name) {
  return request.post(`/generator/history/${taskId}/branch`, { snapshotId, name })
}

// === 后台管理 ===

// 概览统计
export function getAdminStats() {
  return request.get('/admin/stats')
}

// 用户管理：搜索 + 封禁状态筛选
export function listAdminUsers(keyword = '', status = '', page = 1, size = 10) {
  return request.get('/admin/users', { params: { keyword, status, page, size } })
}

// 封禁用户
export function banUser(id, reason) {
  return request.put(`/admin/users/${id}/ban`, { reason })
}

// 解封用户
export function unbanUser(id) {
  return request.put(`/admin/users/${id}/unban`)
}

// 调整用户角色：role=admin/user
export function setAdminUserRole(id, role) {
  return request.put(`/admin/users/${id}/role`, { role })
}

// 重置用户密码
export function resetAdminUserPassword(id, password) {
  return request.put(`/admin/users/${id}/password`, { password })
}

// 生成任务管理：status=PENDING/RUNNING/COMPLETED/FAILED/CANCELLED
export function listAdminTasks(params = {}) {
  return request.get('/admin/tasks', { params })
}

// 终止排队中/运行中的任务
export function cancelAdminTask(id) {
  return request.put(`/admin/tasks/${id}/cancel`)
}

// 删除失败/已终止的任务
export function deleteAdminTask(id) {
  return request.delete(`/admin/tasks/${id}`)
}

// 作品管理：deleted=true/false，communityStatus=published/unpublished
export function listAdminProjects(params = {}) {
  return request.get('/admin/projects', { params })
}

// 后台删除作品（软删除）
export function adminDeleteProject(id) {
  return request.put(`/admin/projects/${id}/delete`)
}

// 恢复已删除作品
export function restoreAdminProject(id) {
  return request.put(`/admin/projects/${id}/restore`)
}

// 上架 / 下架到作品广场
export function setAdminCommunityStatus(id, status) {
  return request.put(`/admin/projects/${id}/community`, { status })
}

// Token 用量：按用户汇总
export function listTokenUsers(keyword = '', page = 1, size = 10) {
  return request.get('/admin/tokens/users', { params: { keyword, page, size } })
}

// Token 用量：某用户的按任务明细，deleted=true 只看已删除作品的任务
export function listTokenTasks(userId, deleted = '', page = 1, size = 10) {
  return request.get('/admin/tokens/tasks', { params: { userId, deleted, page, size } })
}