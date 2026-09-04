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
export function submitGenerate(prompt) {
  return request.post('/generator/generate', { prompt })
}

// 获取任务状态
export function getTaskStatus(taskId) {
  return request.get(`/generator/tasks/${taskId}`)
}